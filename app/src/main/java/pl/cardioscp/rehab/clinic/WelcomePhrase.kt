package pl.cardioscp.rehab.clinic

import java.time.LocalDate

/**
 * Fraza powitania głosowego z planu dnia.
 * Gdy sesja rehab na dziś jest już wykonana — przypominamy tylko o lekach.
 */
object WelcomePhrase {
    fun firstName(patientName: String): String =
        patientName.trim().substringBefore(' ').take(40).ifBlank { "Pacjencie" }

    fun pendingMedCount(clinic: ClinicSnapshot): Int =
        clinic.todayDoses.count { it.status == DoseStatus.PENDING }

    fun pendingRehabSessionCount(clinic: ClinicSnapshot, today: LocalDate = LocalDate.now()): Int =
        clinic.sessions.count {
            it.date == today &&
                it.kind == PlannedSessionKind.REHAB_INTERVAL &&
                it.status == PlannedSessionStatus.SCHEDULED
        }

    fun build(
        patientName: String,
        pendingMeds: Int,
        pendingRehabSessions: Int,
    ): String {
        val name = firstName(patientName)
        val hi = "Witaj $name."
        return when {
            pendingMeds > 0 && pendingRehabSessions > 0 ->
                "$hi Dziś w planie masz przyjęcie ${medsPhrase(pendingMeds)} " +
                    "i ${sessionsPhrase(pendingRehabSessions)}."
            pendingMeds > 0 ->
                "$hi Dziś w planie masz przyjęcie ${medsPhrase(pendingMeds)}."
            pendingRehabSessions > 0 ->
                "$hi Dziś w planie masz ${sessionsPhrase(pendingRehabSessions)}."
            else ->
                "$hi Na dziś nie masz już zaplanowanych leków ani sesji rehabilitacji."
        }
    }

    fun buildFromClinic(clinic: ClinicSnapshot, today: LocalDate = LocalDate.now()): String =
        build(
            patientName = clinic.patientName,
            pendingMeds = pendingMedCount(clinic),
            pendingRehabSessions = pendingRehabSessionCount(clinic, today),
        )

    /** Dopełniacz po „przyjęcie”: 1 leku / 2 leków / 5 leków. */
    fun medsPhrase(count: Int): String = when {
        count <= 0 -> "0 leków"
        count == 1 -> "1 leku"
        else -> "$count leków"
    }

    /** Biernik: 1 sesję / 2 sesje / 5 sesji rehabilitacji. */
    fun sessionsPhrase(count: Int): String = when {
        count <= 0 -> "0 sesji rehabilitacji"
        count == 1 -> "1 sesję rehabilitacji"
        count % 10 in 2..4 && count % 100 !in 12..14 -> "$count sesje rehabilitacji"
        else -> "$count sesji rehabilitacji"
    }
}
