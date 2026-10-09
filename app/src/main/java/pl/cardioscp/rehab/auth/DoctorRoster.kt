package pl.cardioscp.rehab.auth

import pl.cardioscp.rehab.clinic.PlannedSession
import pl.cardioscp.rehab.clinic.PlannedSessionKind
import pl.cardioscp.rehab.clinic.PlannedSessionStatus
import java.time.LocalDate
import java.time.LocalTime

/**
 * Pacjenci widoczni dla lekarza (demo).
 * [usesLocalClinic] = true → dane z [pl.cardioscp.rehab.clinic.ClinicDemoStore].
 */
data class RosterPatient(
    val id: String,
    val displayName: String,
    val usesLocalClinic: Boolean,
)

object DoctorRoster {
    val patients: List<RosterPatient> = listOf(
        RosterPatient(
            id = "adam-testowski",
            displayName = "Adam Testowski",
            usesLocalClinic = true,
        ),
        RosterPatient(
            id = "ewa-nowak",
            displayName = "Ewa Nowak",
            usesLocalClinic = false,
        ),
    )

    fun byId(id: String): RosterPatient? = patients.firstOrNull { it.id == id }

    /** Sesje demo dla pacjentów spoza lokalnej kliniki. */
    fun seedSessions(patientId: String, today: LocalDate): List<PlannedSession> {
        if (patientId != "ewa-nowak") return emptyList()
        return listOf(
            PlannedSession(
                id = "ewa-rehab-today",
                date = today,
                time = LocalTime.of(11, 0),
                kind = PlannedSessionKind.REHAB_INTERVAL,
                title = "Trening interwałowy",
                status = PlannedSessionStatus.SCHEDULED,
            ),
            PlannedSession(
                id = "ewa-rehab-yday",
                date = today.minusDays(1),
                time = LocalTime.of(10, 0),
                kind = PlannedSessionKind.REHAB_INTERVAL,
                title = "Trening interwałowy",
                status = PlannedSessionStatus.DONE,
                completedAtMs = System.currentTimeMillis() - 86_400_000L,
            ),
            PlannedSession(
                id = "ewa-consult",
                date = today.plusDays(3),
                time = LocalTime.of(14, 30),
                kind = PlannedSessionKind.CONSULT,
                title = "Konsultacja",
                status = PlannedSessionStatus.SCHEDULED,
            ),
        )
    }
}
