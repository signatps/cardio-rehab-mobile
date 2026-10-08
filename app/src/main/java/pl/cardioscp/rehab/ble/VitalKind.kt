package pl.cardioscp.rehab.ble

/** Typ pomiaru w sesji rehabilitacji (podzbiór DSD MeasurementType). */
enum class VitalMeasureType(val label: String, val shortLabel: String) {
    BLOOD_PRESSURE("Ciśnienie tętnicze", "Ciśnienie"),
    WEIGHT("Masa ciała", "Masa"),
}

object VitalKindMapping {
    fun kindFor(type: VitalMeasureType): BleVitalKind = when (type) {
        VitalMeasureType.BLOOD_PRESSURE -> BleVitalKind.BP_AUTO
        VitalMeasureType.WEIGHT -> BleVitalKind.WEIGHT_AUTO
    }

    fun hasValues(reading: VitalReading): Boolean = when (reading.kind) {
        BleVitalKind.BP_AUTO, BleVitalKind.BP_TAIDOC_AUTO,
        BleVitalKind.BP_TD3140, BleVitalKind.BP_TD3128, BleVitalKind.BP_MICROLIFE ->
            reading.systolicMmHg != null && reading.diastolicMmHg != null
        BleVitalKind.WEIGHT_AUTO, BleVitalKind.WEIGHT_CHARDER,
        BleVitalKind.WEIGHT_TD2555, BleVitalKind.WEIGHT_IXELLENCE ->
            reading.weightKg != null
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
        }
    }
}
