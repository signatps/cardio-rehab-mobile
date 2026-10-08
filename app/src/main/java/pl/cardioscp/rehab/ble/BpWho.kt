package pl.cardioscp.rehab.ble

/**
 * Klasyfikacja ciśnienia tętniczego — kategorie WHO / ESH (pomiar gabinetowy)
 * oraz etykiety jak w DPS `BloodCirculatoryPressure.qml`.
 *
 * Kategorię wyznacza **wyższy** z progów skurczowego / rozkurczowego
 * (DPS oceniało tylko SYS; tu pełne WHO).
 *
 * | Kategoria | SYS (mmHg) | DIA (mmHg) |
 * |---|---|---|
 * | Niskie | &lt; 90 | — |
 * | Optymalne | &lt; 120 | &lt; 80 |
 * | Normalne | 120–129 | 80–84 |
 * | Prawidłowe wysokie | 130–139 | 85–89 |
 * | Nadciśnienie 1° | 140–159 | 90–99 |
 * | Nadciśnienie 2° | 160–179 | 100–109 |
 * | Nadciśnienie 3° | ≥ 180 | ≥ 110 |
 */
object BpWho {
    enum class Band {
        LOW,
        OPTIMAL,
        NORMAL,
        HIGH_NORMAL,
        GRADE1,
        GRADE2,
        GRADE3,
        UNKNOWN,
    }

    fun band(systolicMmHg: Int?, diastolicMmHg: Int?): Band {
        val sys = systolicMmHg ?: return Band.UNKNOWN
        val dia = diastolicMmHg ?: return Band.UNKNOWN
        if (sys <= 0 || dia <= 0) return Band.UNKNOWN
        if (sys < 90) return Band.LOW

        val bySys = when {
            sys < 120 -> Band.OPTIMAL
            sys <= 129 -> Band.NORMAL
            sys <= 139 -> Band.HIGH_NORMAL
            sys <= 159 -> Band.GRADE1
            sys <= 179 -> Band.GRADE2
            else -> Band.GRADE3
        }
        val byDia = when {
            dia < 80 -> Band.OPTIMAL
            dia <= 84 -> Band.NORMAL
            dia <= 89 -> Band.HIGH_NORMAL
            dia <= 99 -> Band.GRADE1
            dia <= 109 -> Band.GRADE2
            else -> Band.GRADE3
        }
        return worse(bySys, byDia)
    }

    /** Jak w DPS: tylko SYS (dla zgodności z ankietą BloodCirculatoryPressure). */
    fun bandSystolicOnly(systolicMmHg: Int?): Band {
        val sys = systolicMmHg ?: return Band.UNKNOWN
        if (sys <= 0) return Band.UNKNOWN
        return when {
            sys < 90 -> Band.LOW
            sys <= 119 -> Band.OPTIMAL
            sys <= 129 -> Band.NORMAL
            sys <= 139 -> Band.HIGH_NORMAL
            sys <= 159 -> Band.GRADE1
            sys <= 169 -> Band.GRADE2 // DPS: 160–169 → 2°, ≥170 → 3°
            else -> Band.GRADE3
        }
    }

    private fun worse(a: Band, b: Band): Band {
        fun rank(band: Band): Int = when (band) {
            Band.UNKNOWN -> -1
            Band.OPTIMAL -> 0
            Band.NORMAL -> 1
            Band.HIGH_NORMAL -> 2
            Band.LOW -> 3 // ostrzeżenie — nie „lepsze” niż optymalne
            Band.GRADE1 -> 4
            Band.GRADE2 -> 5
            Band.GRADE3 -> 6
        }
        return if (rank(a) >= rank(b)) a else b
    }

    fun label(band: Band): String = when (band) {
        Band.LOW -> "niskie (SYS <90)"
        Band.OPTIMAL -> "optymalne WHO (<120 / <80)"
        Band.NORMAL -> "normalne (120–129 / 80–84)"
        Band.HIGH_NORMAL -> "prawidłowe wysokie (130–139 / 85–89)"
        Band.GRADE1 -> "nadciśnienie 1° (140–159 / 90–99)"
        Band.GRADE2 -> "nadciśnienie 2° (160–179 / 100–109)"
        Band.GRADE3 -> "nadciśnienie 3° (≥180 / ≥110)"
        Band.UNKNOWN -> ""
    }

    fun argb(band: Band): Long = when (band) {
        Band.OPTIMAL, Band.NORMAL -> 0xFF1B7A3DL
        Band.HIGH_NORMAL, Band.LOW -> 0xFFD4780AL
        Band.GRADE1 -> 0xFFC45C0AL
        Band.GRADE2, Band.GRADE3 -> 0xFFB42318L
        Band.UNKNOWN -> 0xFF002660L
    }

    fun isBpKind(kind: BleVitalKind): Boolean = BleDeviceIdentity.isBpKind(kind)
}
