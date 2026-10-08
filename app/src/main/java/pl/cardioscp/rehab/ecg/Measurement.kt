package pl.cardioscp.rehab.ecg

/**
 * Status pojedynczego pomiaru — brak wyniku to `null` + powód, nigdy „0” jako brak.
 */
enum class MeasurementStatus {
    Valid,
    Provisional,
    NotMeasurable,
}

data class Measurement<T>(
    val value: T?,
    val status: MeasurementStatus,
    val reason: String? = null,
    val leadsUsed: List<Lead> = emptyList(),
    val beatsUsed: Int = 0,
    val algorithmVersion: String = EcgAnalysisEngine.ALGORITHM_VERSION,
) {
    val isUsable: Boolean get() = status == MeasurementStatus.Valid && value != null

    companion object {
        fun <T> valid(
            value: T,
            leads: List<Lead> = emptyList(),
            beats: Int = 0,
        ) = Measurement(value, MeasurementStatus.Valid, leadsUsed = leads, beatsUsed = beats)

        fun <T> provisional(
            value: T?,
            reason: String,
            leads: List<Lead> = emptyList(),
            beats: Int = 0,
        ) = Measurement(value, MeasurementStatus.Provisional, reason, leads, beats)

        fun <T> notMeasurable(
            reason: String,
            leads: List<Lead> = emptyList(),
            beats: Int = 0,
        ) = Measurement<T>(null, MeasurementStatus.NotMeasurable, reason, leads, beats)
    }
}

/**
 * Skąd wzięto skalę mV. Gain USB **musi** pochodzić z odpowiedzi aparatu na `0x01`.
 */
enum class CalibrationSource {
    /** Gain odczytany z parametrów EHO12 (`0x01`). */
    DeviceParams,
    /** Symulator / sygnał testowy aplikacji. */
    Simulator,
    /** SCP-ECG section 6 AVM (nV/LSB), np. EHO-MINI AVM=7100. */
    ScpAvm,
    /** Stary zapis bez metadanych albo brak gain z aparatu. */
    Unknown,
}

data class CalibrationInfo(
    val source: CalibrationSource,
    val gain: Int?,
    /** Dokumentowany wzór toru: mV = counts * gain / 1_000_000 (DevEHO12). */
    val formula: String = "mV = counts * gain / 1e6",
) {
    val amplitudesTrusted: Boolean
        get() = source == CalibrationSource.DeviceParams ||
            source == CalibrationSource.Simulator ||
            source == CalibrationSource.ScpAvm

    companion object {
        fun fromDeviceGain(gain: Int) = CalibrationInfo(CalibrationSource.DeviceParams, gain)
        fun simulator(gain: Int) = CalibrationInfo(CalibrationSource.Simulator, gain)
        fun fromScpAvm(avmNvPerLsb: Int) = CalibrationInfo(
            source = CalibrationSource.ScpAvm,
            gain = avmNvPerLsb,
            formula = "mV = sample * AVM_nV / 1e6",
        )
        fun unknown(gain: Int? = null) = CalibrationInfo(CalibrationSource.Unknown, gain)
    }
}

/** Tryb zapisu — wpływa na dostępność morfologii / ST. */
enum class RecordingMode {
    Rest,
    Motion,
}
