package pl.cardioscp.rehab.bluetooth.protocol

/** Shared outbound/events surface for protocol scenario runners. */
sealed interface ScenarioEvent {
    data class Outbound(val frame: ProtocolFrame) : ScenarioEvent
    data class Pulse(val bpm: Int) : ScenarioEvent
    data class Info(val message: String) : ScenarioEvent
    /** Complete SCP file bytes (all fragments already reassembled). */
    data class ScpFileReady(val bytes: ByteArray) : ScenarioEvent {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is ScpFileReady) return false
            return bytes.contentEquals(other.bytes)
        }

        override fun hashCode(): Int = bytes.contentHashCode()
    }
    data class Failed(val reason: String) : ScenarioEvent
    data object Finished : ScenarioEvent
}
