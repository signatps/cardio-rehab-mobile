package pl.cardioscp.rehab.clinic

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

/** Lokalny magazyn demo (jak mobile-DSD: leki, choroby, pomiary, plan) — bez chmury. */
class ClinicDemoStore {
    private val zone = ZoneId.systemDefault()
    private val today = LocalDate.now()

    private var measurements = defaultMeasurements()
    private var medications = defaultMedications()
    private var doses = defaultDoses()
    private var diseases = defaultDiseases()
    private var sessions = defaultSessions()
    private var dayPlan = defaultDayPlan()

    fun snapshot(): ClinicSnapshot = ClinicSnapshot(
        patientName = "Jan Kowalski",
        measurements = measurements.sortedByDescending { it.measuredAtMs },
        medications = medications,
        todayDoses = doses.sortedBy { it.time },
        diseases = diseases,
        sessions = sessions.sortedWith(compareBy({ it.date }, { it.time })),
        dayPlan = dayPlan.sortedBy { it.time },
    )

    fun addMeasurement(kind: VitalKind, label: String, valueText: String, note: String = "") {
        measurements = listOf(
            ClinicMeasurement(
                id = UUID.randomUUID().toString(),
                kind = kind,
                label = label,
                valueText = valueText,
                measuredAtMs = System.currentTimeMillis(),
                note = note,
            ),
        ) + measurements
    }

    fun markDoseTaken(id: String) {
        doses = doses.map {
            if (it.id == id) it.copy(status = DoseStatus.TAKEN) else it
        }
        dayPlan = dayPlan.map {
            if (it.id == "dose-$id") it.copy(done = true) else it
        }
    }

    fun sessionsInMonth(yearMonth: java.time.YearMonth): List<PlannedSession> =
        sessions.filter { java.time.YearMonth.from(it.date) == yearMonth }

    fun sessionsOn(date: LocalDate): List<PlannedSession> =
        sessions.filter { it.date == date }

    private fun at(daysAgo: Long, hour: Int, minute: Int = 0): Long =
        today.minusDays(daysAgo).atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

    private fun defaultMeasurements() = listOf(
        ClinicMeasurement("m1", VitalKind.BLOOD_PRESSURE, "Ciśnienie", "128/82 · 72/min", at(0, 8, 10)),
        ClinicMeasurement("m2", VitalKind.WEIGHT, "Masa", "78.2 kg", at(0, 8, 5)),
        ClinicMeasurement("m3", VitalKind.PULSE, "Tętno", "68 bpm", at(1, 9, 0)),
        ClinicMeasurement("m4", VitalKind.BLOOD_PRESSURE, "Ciśnienie", "132/84 · 74/min", at(1, 8, 15)),
        ClinicMeasurement("m5", VitalKind.WEIGHT, "Masa", "78.5 kg", at(2, 8, 0)),
        ClinicMeasurement("m6", VitalKind.SPO2, "SpO₂", "97%", at(3, 10, 0)),
        ClinicMeasurement("m7", VitalKind.BLOOD_PRESSURE, "Ciśnienie", "126/80 · 70/min", at(4, 8, 20)),
    )

    private fun defaultMedications() = listOf(
        Medication("med1", "Bisoprolol", "5 mg", "rano"),
        Medication("med2", "Ramipril", "5 mg", "rano"),
        Medication("med3", "Furosemid", "40 mg", "rano"),
        Medication("med4", "ASA", "75 mg", "wieczór"),
    )

    private fun defaultDoses() = listOf(
        MedDose("d1", "Bisoprolol", "5 mg", LocalTime.of(8, 0), DoseStatus.TAKEN),
        MedDose("d2", "Ramipril", "5 mg", LocalTime.of(8, 0), DoseStatus.TAKEN),
        MedDose("d3", "Furosemid", "40 mg", LocalTime.of(8, 30), DoseStatus.PENDING),
        MedDose("d4", "ASA", "75 mg", LocalTime.of(20, 0), DoseStatus.PENDING),
    )

    private fun defaultDiseases() = listOf(
        Disease("dis1", "Niewydolność serca (HFrEF)", "I50.1", DiseaseStatus.AKTUALNA, "2023-04"),
        Disease("dis2", "Nadciśnienie tętnicze", "I10", DiseaseStatus.AKTUALNA, "2019-11"),
        Disease("dis3", "Zawał mięśnia sercowego", "I21", DiseaseStatus.HISTORYCZNA, "2023-03"),
    )

    private fun defaultSessions(): List<PlannedSession> {
        val base = mutableListOf(
            PlannedSession(
                "s0", today, LocalTime.of(10, 0),
                PlannedSessionKind.REHAB_INTERVAL, "Trening sekwencyjny",
                PlannedSessionStatus.SCHEDULED,
            ),
            PlannedSession(
                "s1", today.plusDays(2), LocalTime.of(10, 0),
                PlannedSessionKind.REHAB_INTERVAL, "Trening sekwencyjny",
                PlannedSessionStatus.SCHEDULED,
            ),
            PlannedSession(
                "s2", today.plusDays(4), LocalTime.of(11, 30),
                PlannedSessionKind.ECG_CHECK, "Kontrola EKG",
                PlannedSessionStatus.SCHEDULED,
            ),
            PlannedSession(
                "s3", today.minusDays(2), LocalTime.of(10, 0),
                PlannedSessionKind.REHAB_INTERVAL, "Trening sekwencyjny",
                PlannedSessionStatus.DONE,
            ),
            PlannedSession(
                "s4", today.plusDays(7), LocalTime.of(9, 0),
                PlannedSessionKind.CONSULT, "Konsultacja kardiologiczna",
                PlannedSessionStatus.SCHEDULED,
            ),
        )
        for (d in 1..28 step 3) {
            val date = today.withDayOfMonth(d.coerceAtMost(today.lengthOfMonth()))
            if (base.none { it.date == date }) {
                base += PlannedSession(
                    "sx$d", date, LocalTime.of(10, 0),
                    PlannedSessionKind.REHAB_INTERVAL, "Trening sekwencyjny",
                    if (date.isBefore(today)) PlannedSessionStatus.DONE else PlannedSessionStatus.SCHEDULED,
                )
            }
        }
        return base
    }

    private fun defaultDayPlan() = listOf(
        DayPlanItem("dose-d1", LocalTime.of(8, 0), "Leki rano", "Bisoprolol 5 mg, Ramipril 5 mg", done = true),
        DayPlanItem("dose-d3", LocalTime.of(8, 30), "Furosemid", "40 mg", done = false),
        DayPlanItem("vitals", LocalTime.of(9, 0), "Pomiary", "Ciśnienie, waga", done = false),
        DayPlanItem("session", LocalTime.of(10, 0), "Sesja rehabilitacji", "Trening sekwencyjny 2×15 s", done = false),
        DayPlanItem("dose-d4", LocalTime.of(20, 0), "ASA", "75 mg wieczorem", done = false),
    )
}
