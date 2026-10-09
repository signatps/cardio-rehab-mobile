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
    /** Konfiguracja strumienia ECG Online (0x0F). */
    data class EcgOnlineInfoEvent(val info: PayloadCodec.EcgOnlineInfo) : ScenarioEvent
    /** Paczka próbek ECG Online (0x10). */
    data class EcgOnlineSamples(
        val leadLabels: List<String>,
        /** Kolejne próbki czasu; każdy wiersz = wartości mV dla leadLabels. */
        val samplesMv: Array<DoubleArray>,
        /** Surowce signed (do taśmy sesji / SCP); null gdy niedostępne. */
        val samplesRaw: Array<ShortArray>? = null,
        val avmNanoVolts: Int = 7100,
        val firstSampleIndex: Int,
    ) : ScenarioEvent
    /** Urządzenie nie obsługuje ECG Online. */
    data object EcgOnlineUnsupported : ScenarioEvent
    data class Failed(val reason: String) : ScenarioEvent
    data object Finished : ScenarioEvent
}
