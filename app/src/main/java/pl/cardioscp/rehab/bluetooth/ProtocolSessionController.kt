package pl.cardioscp.rehab.bluetooth

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import pl.cardioscp.rehab.bluetooth.protocol.EcgOfflineCreateOrchestrator
import pl.cardioscp.rehab.bluetooth.protocol.FrameType
import pl.cardioscp.rehab.bluetooth.protocol.PayloadCodec
import pl.cardioscp.rehab.bluetooth.protocol.PulseScenarioOrchestrator
import pl.cardioscp.rehab.bluetooth.protocol.ScenarioEvent

/**
 * Runs documented SPP scenarios over a live [EhoMiniDeviceClient] link.
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

    private var pulseOrch: PulseScenarioOrchestrator? = null
    private var ecgOrch: EcgOfflineCreateOrchestrator? = null
    private var collectJob: Job? = null
    private var active: Scenario? = null

    fun startPulseScenario() {
        stop()
        val orch = PulseScenarioOrchestrator(autoStopAfterPulses = 3)
        pulseOrch = orch
        active = Scenario.PULSE
        _status.value = Status.Running(Scenario.PULSE)
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
        scope.launch {
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
        stop()
        val orch = EcgOfflineCreateOrchestrator()
        ecgOrch = orch
        active = Scenario.ECG_OFFLINE_CREATE
        _status.value = Status.Running(Scenario.ECG_OFFLINE_CREATE)
        collectJob = scope.launch {
            client.incomingFrames.collect { frame ->
                dispatch(orch.onFrame(frame))
            }
        }
        scope.launch {
            dispatch(
                orch.start(
                    EcgOfflineCreateOrchestrator.Config(
                        unixTimestampSeconds = System.currentTimeMillis() / 1000L,
                        samplingHz = 500,
                        pulseAverageSeconds = 10,
                        lookbackSeconds = 5,
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
        active = null
        _status.value = Status.Idle
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
