package pl.cardioscp.rehab.ble

/**
 * BMI wg WHO (+ etykiety DPS `WeightDataSurvey.qml`):
 * - &lt; 18.5 — niedowaga
 * - 18.5–24.99 — prawidłowa
 * - 25.0–29.99 — nadwaga
 * - 30.0–34.99 — otyłość I°
 * - 35.0–39.99 — otyłość II°
 * - ≥ 40 — otyłość III°
 *
 * BMI = masa[kg] / (wzrost[m])²
 */
object BmiWho {
    enum class Band {
        UNDERWEIGHT,
        NORMAL,
        OVERWEIGHT,
        OBESE_I,
        OBESE_II,
        OBESE_III,
        UNKNOWN,
    }

    fun compute(weightKg: Double?, heightCm: Double?): Double? {
        val w = weightKg ?: return null
        val hCm = heightCm ?: return null
        if (w <= 0 || hCm < 50 || hCm > 250) return null
        val hM = hCm / 100.0
        return w / (hM * hM)
    }

    fun band(bmi: Double?): Band = when {
        bmi == null || bmi <= 0 -> Band.UNKNOWN
        bmi < 18.5 -> Band.UNDERWEIGHT
        bmi < 25.0 -> Band.NORMAL
        bmi < 30.0 -> Band.OVERWEIGHT
        bmi < 35.0 -> Band.OBESE_I
        bmi < 40.0 -> Band.OBESE_II
        else -> Band.OBESE_III
    }

    fun label(band: Band): String = when (band) {
        Band.UNDERWEIGHT -> "niedowaga WHO (<18.5)"
        Band.NORMAL -> "prawidłowa WHO (18.5–24.9)"
        Band.OVERWEIGHT -> "nadwaga (25–29.9)"
        Band.OBESE_I -> "otyłość I° (30–34.9)"
        Band.OBESE_II -> "otyłość II° (35–39.9)"
        Band.OBESE_III -> "otyłość III° (≥40)"
        Band.UNKNOWN -> ""
    }

    fun argb(band: Band): Long = when (band) {
        Band.NORMAL -> 0xFF1B7A3DL
        Band.UNDERWEIGHT, Band.OVERWEIGHT -> 0xFFD4780AL
        Band.OBESE_I -> 0xFFC45C0AL
        Band.OBESE_II, Band.OBESE_III -> 0xFFB42318L
        Band.UNKNOWN -> 0xFF002660L
    }

    fun format(bmi: Double): String = "%.1f".format(bmi)

    fun parseHeightCm(raw: String): Double? {
        val cleaned = raw.trim().replace(',', '.')
        if (cleaned.isEmpty()) return null
        val v = cleaned.toDoubleOrNull() ?: return null
        return v.takeIf { it in 50.0..250.0 }
    }
}
