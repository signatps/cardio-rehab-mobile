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
import pl.cardioscp.rehab.bluetooth.protocol.PulseScenarioOrchestrator
import pl.cardioscp.rehab.bluetooth.protocol.ScenarioEvent
import pl.cardioscp.rehab.bluetooth.protocol.SequenceGenerator

/**
 * Runs documented SPP scenarios over a live [EhoMiniDeviceClient] link.
 * Shares one [SequenceGenerator] for the lifetime of the BT session.
 */
class ProtocolSessionController(
    private val client: EhoMiniDeviceClient,
    private val scope: CoroutineScope,
) {
    enum class Scenario { PULSE, ECG_OFFLINE_CREATE }

    sealed interface Status {
        data object Idle : Status
        data class Running(val scenario: Scenario) : Status
        data class Pulse(val bpm: Int) : Status
        data class Info(val message: String) : Status
        data class Failed(val reason: String) : Status
        data object Finished : Status
    }

    private val _status = MutableStateFlow<Status>(Status.Idle)
    val status: StateFlow<Status> = _status.asStateFlow()

    private val sequences = SequenceGenerator()
    private val commands = CommandFactory(sequences)

    private var pulseOrch: PulseScenarioOrchestrator? = null
    private var ecgOrch: EcgOfflineCreateOrchestrator? = null
    private var collectJob: Job? = null

    fun startPulseScenario() {
        scope.launch {
            prepareDevice()
            val orch = PulseScenarioOrchestrator(
                commands = commands,
                autoStopAfterPulses = 12,
            )
            pulseOrch = orch
            ecgOrch = null
            _status.value = Status.Running(Scenario.PULSE)
            collectJob?.cancel()
            collectJob = scope.launch {
                client.incomingFrames.collect { frame ->
                    if (frame.type == FrameType.PULSE_VALUE && frame.payload.isNotEmpty()) {
                        runCatching {
                            _status.value = Status.Pulse(PayloadCodec.parsePulseValue(frame.payload))
                        }
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
            _status.value = Status.Running(Scenario.ECG_OFFLINE_CREATE)
            collectJob?.cancel()
            collectJob = scope.launch {
                client.incomingFrames.collect { frame ->
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

    fun stop() {
        collectJob?.cancel()
        collectJob = null
        pulseOrch = null
        ecgOrch = null
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

    private suspend fun dispatch(events: List<ScenarioEvent>) {
        for (event in events) {
            when (event) {
                is ScenarioEvent.Outbound -> client.sendFrame(event.frame)
                is ScenarioEvent.Pulse -> _status.value = Status.Pulse(event.bpm)
                is ScenarioEvent.Info -> _status.value = Status.Info(event.message)
                is ScenarioEvent.Failed -> _status.value = Status.Failed(event.reason)
                ScenarioEvent.Finished -> _status.value = Status.Finished
            }
        }
    }
}
