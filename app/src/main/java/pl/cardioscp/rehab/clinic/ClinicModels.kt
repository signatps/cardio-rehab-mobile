package pl.cardioscp.rehab.clinic

import java.time.LocalDate
import java.time.LocalTime

enum class VitalKind {
    BLOOD_PRESSURE,
    WEIGHT,
    SPO2,
    PULSE,
    ECG,
}

data class ClinicMeasurement(
    val id: String,
    val kind: VitalKind,
    val label: String,
    val valueText: String,
    val measuredAtMs: Long,
    val note: String = "",
    /** Identyfikator sesji rehab — pomiary z jednej sesji są grupowane. */
    val sessionGroupId: String? = null,
    val sessionGroupTitle: String? = null,
)

enum class DoseStatus { PENDING, TAKEN, SKIPPED }

data class MedDose(
    val id: String,
    val drugName: String,
    val doseLabel: String,
    val time: LocalTime,
    val status: DoseStatus,
)

enum class DiseaseStatus { AKTUALNA, PRZEWLEKLA, HISTORYCZNA }

data class Disease(
    val id: String,
    val name: String,
    val icd: String = "",
    val status: DiseaseStatus,
    val diagnosedLabel: String,
    val note: String = "",
)

data class Medication(
    val id: String,
    val name: String,
    val doseLabel: String,
    val scheduleNote: String,
    /** Godziny HH:mm, np. 08:00, 20:00. */
    val times: List<String> = emptyList(),
)

enum class PlannedSessionKind { REHAB_INTERVAL, ECG_CHECK, CONSULT }

enum class PlannedSessionStatus {
    SCHEDULED,
    DONE,
    /** Brak dopuszczenia (ankieta / kwalifikacja). */
    DISQUALIFIED,
    MISSED,
    CANCELLED,
}

data class PlannedSession(
    val id: String,
    val date: LocalDate,
    val time: LocalTime,
    val kind: PlannedSessionKind,
    val title: String,
    val status: PlannedSessionStatus,
)

data class DayPlanItem(
    val id: String,
    val time: LocalTime,
    val title: String,
    val detail: String,
    val done: Boolean = false,
)

data class ClinicSnapshot(
    val patientName: String,
    val measurements: List<ClinicMeasurement>,
    val medications: List<Medication>,
    val todayDoses: List<MedDose>,
    val diseases: List<Disease>,
    val sessions: List<PlannedSession>,
    val dayPlan: List<DayPlanItem>,
)
