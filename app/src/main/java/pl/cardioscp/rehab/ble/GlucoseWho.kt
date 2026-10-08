package pl.cardioscp.rehab.ble

/**
 * Ocena glikemii — progi z DPS (`GlucoseDataInfo.qml`), zgodne z typowymi
 * zakresami klinicznymi / WHO dla glikemii na czczo (mg/dL):
 * - 70–99 — prawidłowa
 * - < 70 — zaniżona (hipoglikemia)
 * - > 100 — podwyższona
 * - 100 — w DPS bez osobnego if → „Prawidłowa”
 *
 * Wartość z TD-4277 jest zawsze w mg/dL.
 */
object GlucoseWho {
    enum class Band {
        NORMAL,
        LOW,
        HIGH,
        UNKNOWN,
    }

    fun band(mgDl: Int?): Band = when {
        mgDl == null || mgDl <= 0 -> Band.UNKNOWN
        mgDl in 70..99 -> Band.NORMAL
        mgDl < 70 -> Band.LOW
        mgDl > 100 -> Band.HIGH
        else -> Band.NORMAL // dokładnie 100 — jak DPS
    }

    fun label(band: Band): String = when (band) {
        Band.NORMAL -> "prawidłowa WHO (70–99 mg/dL)"
        Band.LOW -> "zaniżona (<70 mg/dL)"
        Band.HIGH -> "podwyższona (>100 mg/dL)"
        Band.UNKNOWN -> ""
    }

    fun argb(band: Band): Long = when (band) {
        Band.NORMAL -> 0xFF1B7A3DL
        Band.LOW -> 0xFFD4780AL
        Band.HIGH -> 0xFFB42318L
        Band.UNKNOWN -> 0xFF002660L
    }

    /** mg/dL → mmol/L (współczynnik 18.0 jak w typowych glukometrach). */
    fun toMmol(mgDl: Int): Double = mgDl / 18.0

    fun format(mgDl: Int): String =
        "%d mg/dL (%.1f mmol/L)".format(mgDl, toMmol(mgDl))
}
