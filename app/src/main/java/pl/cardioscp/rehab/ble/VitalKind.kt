package pl.cardioscp.rehab.ble

/** Typ pomiaru (podzbiór DSD MeasurementType) — sesja rehab + menu Pomiary pacjenta. */
enum class VitalMeasureType(val label: String, val shortLabel: String) {
    BLOOD_PRESSURE("Ciśnienie tętnicze", "Ciśnienie"),
    WEIGHT("Masa ciała", "Masa"),
    SPO2("Saturacja SpO₂", "Saturacja"),
    GLUCOSE("Glikemia", "Glikemia"),
}

object VitalKindMapping {
    fun kindFor(type: VitalMeasureType): BleVitalKind = when (type) {
        VitalMeasureType.BLOOD_PRESSURE -> BleVitalKind.BP_AUTO
        VitalMeasureType.WEIGHT -> BleVitalKind.WEIGHT_AUTO
        VitalMeasureType.SPO2 -> BleVitalKind.SPO2_TD8255
        VitalMeasureType.GLUCOSE -> BleVitalKind.GLU_TD4277
    }

    fun hasValues(reading: VitalReading): Boolean = when (reading.kind) {
        BleVitalKind.BP_AUTO, BleVitalKind.BP_TAIDOC_AUTO,
        BleVitalKind.BP_TD3140, BleVitalKind.BP_TD3128, BleVitalKind.BP_MICROLIFE ->
            reading.systolicMmHg != null && reading.diastolicMmHg != null
        BleVitalKind.WEIGHT_AUTO, BleVitalKind.WEIGHT_CHARDER,
        BleVitalKind.WEIGHT_TD2555, BleVitalKind.WEIGHT_IXELLENCE ->
            reading.weightKg != null
        BleVitalKind.SPO2_TD8255 ->
            reading.spo2Percent != null
        BleVitalKind.GLU_TD4277 ->
            reading.glucoseMgDl != null
        else -> false
    }
}

data class WhoAssessment(
    val label: String,
    val argb: Long,
    val comments: List<String>,
)

object WhoPresentation {
    private const val NAVY = 0xFF002660L

    fun assess(type: VitalMeasureType, reading: VitalReading): WhoAssessment {
        return when (type) {
            VitalMeasureType.BLOOD_PRESSURE -> {
                val band = BpWho.band(reading.systolicMmHg, reading.diastolicMmHg)
                WhoAssessment(
                    label = BpWho.label(band),
                    argb = BpWho.argb(band),
                    comments = listOfNotNull(
                        BpWho.label(band).takeIf { it.isNotBlank() },
                        "W spoczynku",
                        "Po wysiłku",
                        "Rano",
                        "Wieczór",
                        "Po leku",
                    ).distinct(),
                )
            }
            VitalMeasureType.WEIGHT -> WhoAssessment(
                label = "",
                argb = NAVY,
                comments = listOf("Rano", "Po posiłku"),
            )
            VitalMeasureType.SPO2 -> {
                val band = Spo2Who.band(reading.spo2Percent)
                WhoAssessment(
                    label = Spo2Who.label(band),
                    argb = Spo2Who.argb(band),
                    comments = listOf("W spoczynku", "Po wysiłku", "Rano", "Wieczór"),
                )
            }
            VitalMeasureType.GLUCOSE -> {
                val band = GlucoseWho.band(reading.glucoseMgDl)
                WhoAssessment(
                    label = GlucoseWho.label(band),
                    argb = GlucoseWho.argb(band),
                    comments = listOf("Na czczo", "Po posiłku", "Przed posiłkiem", "Wieczór"),
                )
            }
        }
    }
}
