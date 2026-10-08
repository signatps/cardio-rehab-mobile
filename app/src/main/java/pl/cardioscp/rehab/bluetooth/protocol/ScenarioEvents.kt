package pl.cardioscp.rehab.bluetooth.protocol

/** Shared outbound/events surface for protocol scenario runners. */
sealed interface ScenarioEvent {
    data class Outbound(val frame: ProtocolFrame) : ScenarioEvent
    data class Pulse(val bpm: Int) : ScenarioEvent
    data class Info(val message: String) : ScenarioEvent
    data class Failed(val reason: String) : ScenarioEvent
    data object Finished : ScenarioEvent
}
