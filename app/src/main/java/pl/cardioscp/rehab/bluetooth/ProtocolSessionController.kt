package pl.cardioscp.rehab.bluetooth

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import pl.cardioscp.rehab.bluetooth.protocol.FrameType
import pl.cardioscp.rehab.bluetooth.protocol.OfflineSessionOrchestrator
import pl.cardioscp.rehab.bluetooth.protocol.PayloadCodec

/**
 * Drives [OfflineSessionOrchestrator] over a live [EhoMiniDeviceClient] link.
 */
class ProtocolSessionController(
    private val client: EhoMiniDeviceClient,
    private val scope: CoroutineScope,
) {
    sealed interface Status {
        data object Idle : Status
        data object Running : Status
        data class Pulse(val bpm: Int) : Status
        data class ScpSaved(val bytes: Int) : Status
        data class Failed(val reason: String) : Status
        data object Finished : Status
    }

    private val _status = MutableStateFlow<Status>(Status.Idle)
    val status: StateFlow<Status> = _status.asStateFlow()

    private var orchestrator: OfflineSessionOrchestrator? = null
    private var collectJob: Job? = null

    fun startDefaultSession(userId: String) {
        stop()
        val orch = OfflineSessionOrchestrator()
        orchestrator = orch
        _status.value = Status.Running
        collectJob = scope.launch {
            client.incomingFrames.collect { frame ->
                // ACK pulse values are produced by the orchestrator; also surface BPM.
                if (frame.type == FrameType.PULSE_VALUE) {
                    runCatching {
                        _status.value = Status.Pulse(PayloadCodec.parsePulseValue(frame.payload))
                    }
                }
                dispatch(orch.onFrame(frame))
            }
        }
        scope.launch {
            val events = orch.start(
                OfflineSessionOrchestrator.Config(
                    unixTimestampSeconds = System.currentTimeMillis() / 1000L,
                    samplingHz = 500,
                    pulseAverageSeconds = 10,
                    clearBuffer = false,
                    pulseIntervalTenths = 10,
                    ecgJobs = listOf(
                        OfflineSessionOrchestrator.EcgJob(
                            lookbackSeconds = 10,
                            totalSeconds = 10,
                            userId = userId,
                        ),
                    ),
                ),
            )
            dispatch(events)
        }
    }

    fun stop() {
        collectJob?.cancel()
        collectJob = null
        orchestrator = null
        _status.value = Status.Idle
    }

    private suspend fun dispatch(events: List<OfflineSessionOrchestrator.Event>) {
        for (event in events) {
            when (event) {
                is OfflineSessionOrchestrator.Event.Outbound -> client.sendFrame(event.frame)
                is OfflineSessionOrchestrator.Event.Pulse ->
                    _status.value = Status.Pulse(event.bpm)
                is OfflineSessionOrchestrator.Event.ScpCompleted ->
                    _status.value = Status.ScpSaved(event.bytes.size)
                is OfflineSessionOrchestrator.Event.Failed ->
                    _status.value = Status.Failed(event.reason)
                OfflineSessionOrchestrator.Event.Finished ->
                    _status.value = Status.Finished
            }
        }
    }
}
