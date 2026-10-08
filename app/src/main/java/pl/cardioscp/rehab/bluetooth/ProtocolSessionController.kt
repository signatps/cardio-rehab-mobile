package pl.cardioscp.rehab.bluetooth

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import pl.cardioscp.rehab.bluetooth.protocol.CommandFactory
import pl.cardioscp.rehab.bluetooth.protocol.EcgOfflineCreateOrchestrator
import pl.cardioscp.rehab.bluetooth.protocol.FrameType
import pl.cardioscp.rehab.bluetooth.protocol.PayloadCodec
import pl.cardioscp.rehab.bluetooth.protocol.ProtocolFrame
import pl.cardioscp.rehab.bluetooth.protocol.PulseScenarioOrchestrator
import pl.cardioscp.rehab.bluetooth.protocol.ScenarioEvent
import pl.cardioscp.rehab.bluetooth.protocol.ScpDownloadOrchestrator
import pl.cardioscp.rehab.bluetooth.protocol.SequenceGenerator

/**
 * Runs documented SPP scenarios over a live [EhoMiniDeviceClient] link.
 * Shares one [SequenceGenerator] for the lifetime of the BT session.
 *
 * Pulse BPM is published on [lastPulseBpm] / [pulseSampleCount] separately from
 * [status], so rapid Info updates cannot conflate away Pulse values in a StateFlow.
 */
class ProtocolSessionController(
    private val client: EhoMiniDeviceClient,
    private val scope: CoroutineScope,
) {
    enum class Scenario { PULSE, ECG_OFFLINE_CREATE, SCP_DOWNLOAD }

    sealed interface Status {
        data object Idle : Status
        data class Running(val scenario: Scenario) : Status
        data class Info(val message: String) : Status
        data class Failed(val reason: String) : Status
        data object Finished : Status
    }

    private val _status = MutableStateFlow<Status>(Status.Idle)
    val status: StateFlow<Status> = _status.asStateFlow()

    private val _lastPulseBpm = MutableStateFlow<Int?>(null)
    val lastPulseBpm: StateFlow<Int?> = _lastPulseBpm.asStateFlow()

    private val _pulseSampleCount = MutableStateFlow(0)
    val pulseSampleCount: StateFlow<Int> = _pulseSampleCount.asStateFlow()

    private val _electrodeWarning = MutableStateFlow<String?>(null)
    val electrodeWarning: StateFlow<String?> = _electrodeWarning.asStateFlow()

    private val _lastScpBytes = MutableStateFlow<ByteArray?>(null)
    val lastScpBytes: StateFlow<ByteArray?> = _lastScpBytes.asStateFlow()

    private val sequences = SequenceGenerator()
    private val commands = CommandFactory(sequences)

    private var pulseOrch: PulseScenarioOrchestrator? = null
    private var ecgOrch: EcgOfflineCreateOrchestrator? = null
    private var scpOrch: ScpDownloadOrchestrator? = null
    private var collectJob: Job? = null

    fun startPulseScenario() {
        scope.launch {
            prepareDevice()
            _lastPulseBpm.value = null
            _pulseSampleCount.value = 0
            _electrodeWarning.value = null
            val orch = PulseScenarioOrchestrator(
                commands = commands,
                // Keep streaming long enough for avg window (Init pulseAverageSeconds=10).
                autoStopAfterPulses = 20,
            )
            pulseOrch = orch
            ecgOrch = null
            scpOrch = null
            _status.value = Status.Running(Scenario.PULSE)
            collectJob?.cancel()
            collectJob = scope.launch {
                client.incomingFrames.collect { frame ->
                    noteDeviceAlerts(frame)
                    if (frame.type == FrameType.PULSE_VALUE && frame.payload.isNotEmpty()) {
                        publishPulse(PayloadCodec.parsePulseValue(frame.payload))
                    }
                    dispatch(orch.onFrame(frame))
                }
            }
            dispatch(
                orch.start(
                    PulseScenarioOrchestrator.Config(
                        unixTimestampSeconds = System.currentTimeMillis() / 1000L,
                        samplingHz = 500,
                        pulseAverageSeconds = 10,
                        pulseIntervalTenths = 10,
                    ),
                ),
            )
        }
    }

    fun startEcgOfflineCreateScenario(userId: String) {
        scope.launch {
            prepareDevice()
            val orch = EcgOfflineCreateOrchestrator(commands = commands)
            ecgOrch = orch
            pulseOrch = null
            scpOrch = null
            _status.value = Status.Running(Scenario.ECG_OFFLINE_CREATE)
            collectJob?.cancel()
            collectJob = scope.launch {
                client.incomingFrames.collect { frame ->
                    noteDeviceAlerts(frame)
                    dispatch(orch.onFrame(frame))
                }
            }
            // lookback=0: buffer may be empty right after Init (firmware rng_msgs >= back).
            // total > lookback required. Offline Done after ~total seconds.
            dispatch(
                orch.start(
                    EcgOfflineCreateOrchestrator.Config(
                        unixTimestampSeconds = System.currentTimeMillis() / 1000L,
                        samplingHz = 500,
                        pulseAverageSeconds = 10,
                        lookbackSeconds = 0,
                        totalSeconds = 10,
                        userId = userId,
                        reinitAfterEnd = true,
                    ),
                ),
            )
        }
    }

    /** Download the complete SCP currently stored on the device (all fragments assembled). */
    fun startScpDownload() {
        scope.launch {
            prepareDevice()
            _lastScpBytes.value = null
            val orch = ScpDownloadOrchestrator(commands = commands)
            scpOrch = orch
            pulseOrch = null
            ecgOrch = null
            _status.value = Status.Running(Scenario.SCP_DOWNLOAD)
            collectJob?.cancel()
            collectJob = scope.launch {
                client.incomingFrames.collect { frame ->
                    noteDeviceAlerts(frame)
                    dispatch(orch.onFrame(frame))
                }
            }
            dispatch(
                orch.start(
                    ScpDownloadOrchestrator.Config(
                        unixTimestampSeconds = System.currentTimeMillis() / 1000L,
                        samplingHz = 500,
                        pulseAverageSeconds = 10,
                    ),
                ),
            )
        }
    }

    fun stop() {
        collectJob?.cancel()
        collectJob = null
        pulseOrch = null
        ecgOrch = null
        scpOrch = null
        _status.value = Status.Idle
    }

    /**
     * Best-effort End so firmware clears `app_init_flag` before a new scenario Init.
     */
    private suspend fun prepareDevice() {
        stop()
        _status.value = Status.Info("Sending End to clear previous Init (if any)")
        val end = commands.end()
        runCatching { client.sendFrame(end) }
        withTimeoutOrNull(1_500) {
            client.incomingFrames.first {
                it.type == FrameType.ACK && it.sequence == end.sequence ||
                    it.type == FrameType.COMMAND_ERROR
            }
        }
        delay(200)
    }

    private fun publishPulse(bpm: Int) {
        _lastPulseBpm.value = bpm
        _pulseSampleCount.value = _pulseSampleCount.value + 1
    }

    private fun noteDeviceAlerts(frame: ProtocolFrame) {
        if (frame.type != FrameType.DEVICE_ERROR || frame.payload.isEmpty()) return
        val code = frame.payload[0].toInt() and 0xFF
        if (code == 0x01) {
            _electrodeWarning.value =
                "Elektrody odpięte — urządzenie raportuje puls 0, aż będzie sygnał EKG"
        }
    }

    private suspend fun dispatch(events: List<ScenarioEvent>) {
        for (event in events) {
            when (event) {
                is ScenarioEvent.Outbound -> client.sendFrame(event.frame)
                is ScenarioEvent.Pulse -> publishPulse(event.bpm)
                is ScenarioEvent.ScpFileReady -> {
                    _lastScpBytes.value = event.bytes
                    _status.value = Status.Info("Pobrano cały plik SCP (${event.bytes.size} B)")
                }
                is ScenarioEvent.Info -> _status.value = Status.Info(event.message)
                is ScenarioEvent.Failed -> _status.value = Status.Failed(event.reason)
                ScenarioEvent.Finished -> _status.value = Status.Finished
            }
        }
    }
}
