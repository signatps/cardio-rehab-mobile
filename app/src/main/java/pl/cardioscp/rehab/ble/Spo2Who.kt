package pl.cardioscp.rehab.ble

/**
 * Normy saturacji wg wytycznych klinicznych / WHO (pulsoksymetria):
 * - ≥ 95% — norma
 * - 90–94% — obniżona (ostrożność)
 * - < 90% — hipoksemia (krytyczna)
 */
object Spo2Who {
    enum class Band {
        NORMAL,
        CAUTION,
        CRITICAL,
        UNKNOWN,
    }

    fun band(spo2Percent: Int?): Band = when {
        spo2Percent == null -> Band.UNKNOWN
        spo2Percent >= 95 -> Band.NORMAL
        spo2Percent >= 90 -> Band.CAUTION
        else -> Band.CRITICAL
    }

    fun label(band: Band): String = when (band) {
        Band.NORMAL -> "norma WHO (≥95%)"
        Band.CAUTION -> "obniżone (90–94%)"
        Band.CRITICAL -> "hipoksemia (<90%)"
        Band.UNKNOWN -> ""
    }

    /** ARGB do UI (Compose: Color(argb)). */
    fun argb(band: Band): Long = when (band) {
        Band.NORMAL -> 0xFF1B7A3DL
        Band.CAUTION -> 0xFFD4780AL
        Band.CRITICAL -> 0xFFB42318L
        Band.UNKNOWN -> 0xFF002660L
    }

    /** Jak w DPS `td8255.py`: 30 < sat ≤ 100, 30 < pulse < 250. */
    fun isPlausible(sat: Int, pulse: Int): Boolean =
        sat in 31..100 && pulse in 31..249

    /** Alias — okno oczekiwania SpO₂ = wspólny limit sesji BLE. */
    const val WAIT_TIMEOUT_MS: Long = BleVitalsWait.TIMEOUT_MS
}
