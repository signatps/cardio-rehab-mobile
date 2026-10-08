package pl.cardioscp.rehab.clinic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class WelcomePhraseTest {
    @Test
    fun firstName_takesGivenName() {
        assertEquals("Jan", WelcomePhrase.firstName("Jan Kowalski"))
    }

    @Test
    fun build_medsAndSession() {
        val phrase = WelcomePhrase.build("Jan Kowalski", pendingMeds = 2, pendingRehabSessions = 1)
        assertTrue(phrase.startsWith("Witaj Jan."))
        assertTrue(phrase.contains("przyjęcie 2 leków"))
        assertTrue(phrase.contains("1 sesję rehabilitacji"))
    }

    @Test
    fun build_onlyMedsWhenNoSession() {
        val phrase = WelcomePhrase.build("Jan Kowalski", pendingMeds = 2, pendingRehabSessions = 0)
        assertTrue(phrase.contains("przyjęcie 2 leków"))
        assertFalse(phrase.contains("sesj"))
    }

    @Test
    fun build_onlySession() {
        val phrase = WelcomePhrase.build("Anna Nowak", pendingMeds = 0, pendingRehabSessions = 1)
        assertEquals(
            "Witaj Anna. Dziś w planie masz 1 sesję rehabilitacji.",
            phrase,
        )
    }

    @Test
    fun fromClinic_skipsDoneRehabSession() {
        val today = LocalDate.of(2026, 10, 8)
        val clinic = ClinicSnapshot(
            patientName = "Jan Kowalski",
            measurements = emptyList(),
            medications = emptyList(),
            todayDoses = listOf(
                MedDose("d1", "Bisoprolol", "5 mg", LocalTime.of(8, 0), DoseStatus.PENDING),
                MedDose("d2", "ASA", "75 mg", LocalTime.of(20, 0), DoseStatus.TAKEN),
            ),
            diseases = emptyList(),
            sessions = listOf(
                PlannedSession(
                    "s1", today, LocalTime.of(10, 0),
                    PlannedSessionKind.REHAB_INTERVAL, "Trening",
                    PlannedSessionStatus.DONE,
                ),
            ),
            dayPlan = emptyList(),
        )
        val phrase = WelcomePhrase.buildFromClinic(clinic, today)
        assertEquals("Witaj Jan. Dziś w planie masz przyjęcie 1 leku.", phrase)
    }

    @Test
    fun plural_sessions() {
        assertEquals("1 sesję rehabilitacji", WelcomePhrase.sessionsPhrase(1))
        assertEquals("2 sesje rehabilitacji", WelcomePhrase.sessionsPhrase(2))
        assertEquals("5 sesji rehabilitacji", WelcomePhrase.sessionsPhrase(5))
    }
}
