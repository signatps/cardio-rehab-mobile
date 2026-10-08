package pl.cardioscp.rehab.ble

/**
 * Ocena temperatury ciała (°C) — typowe progi kliniczne / WHO (gorączka ≥ 38°C):
 * - &lt; 36.0 — hipotermia
 * - 36.0–37.5 — norma
 * - 37.6–37.9 — stan podgorączkowy
 * - 38.0–38.9 — gorączka
 * - ≥ 39.0 — wysoka gorączka
 */
object TempWho {
    enum class Band {
        HYPOTHERMIA,
        NORMAL,
        SUBFEBRILE,
        FEVER,
        HIGH_FEVER,
        UNKNOWN,
    }

    fun band(celsius: Double?): Band = when {
        celsius == null || celsius <= 0 -> Band.UNKNOWN
        celsius < 36.0 -> Band.HYPOTHERMIA
        celsius <= 37.5 -> Band.NORMAL
        celsius < 38.0 -> Band.SUBFEBRILE
        celsius < 39.0 -> Band.FEVER
        else -> Band.HIGH_FEVER
    }

    fun label(band: Band): String = when (band) {
        Band.HYPOTHERMIA -> "hipotermia (<36.0°C)"
        Band.NORMAL -> "norma (36.0–37.5°C)"
        Band.SUBFEBRILE -> "podgorączkowa (37.6–37.9°C)"
        Band.FEVER -> "gorączka WHO (≥38°C)"
        Band.HIGH_FEVER -> "wysoka gorączka (≥39°C)"
        Band.UNKNOWN -> ""
    }

    fun argb(band: Band): Long = when (band) {
        Band.NORMAL -> 0xFF1B7A3DL
        Band.SUBFEBRILE, Band.HYPOTHERMIA -> 0xFFD4780AL
        Band.FEVER -> 0xFFC45C0AL
        Band.HIGH_FEVER -> 0xFFB42318L
        Band.UNKNOWN -> 0xFF002660L
    }
}
