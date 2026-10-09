package pl.cardioscp.rehab.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.cardioscp.rehab.clinic.PlannedSessionStatus

class SessionSchemeProgressTest {
    @Test
    fun doneStatus_marksAllStepsComplete() {
        val progress = SessionSchemeProgress.fromArchive(
            archive = null,
            status = PlannedSessionStatus.DONE,
        )
        assertTrue(progress.steps.filter { it.inPlan }.all { it.done })
        assertEquals(8, progress.plannedCount)
    }

    @Test
    fun scheduledWithoutArchive_nothingDone() {
        val progress = SessionSchemeProgress.fromArchive(
            archive = null,
            status = PlannedSessionStatus.SCHEDULED,
        )
        assertEquals(0, progress.doneCount)
    }

    @Test
    fun disqualifiedAfterSurvey_stopsBeforeTraining() {
        val archive = ArchivedRehabSession(
            id = "a1",
            sessionNumber = 1,
            title = "Sesja",
            startedAtMs = 1L,
            ecgs = listOf(
                ArchivedEcgSlot("e1", "EKG kwalifikacyjne (przed sesją)", "q.scp", 1L),
            ),
            bloodPressureSummary = "120/80",
            weightSummary = "70 kg",
            surveyPassed = false,
            surveyAnswers = mapOf("chest_pain" to true),
        )
        val progress = SessionSchemeProgress.fromArchive(
            archive = archive,
            status = PlannedSessionStatus.DISQUALIFIED,
        )
        assertTrue(progress.steps.first { it.id == SessionSchemeStepId.QUAL_ECG }.done)
        assertTrue(progress.steps.first { it.id == SessionSchemeStepId.BP }.done)
        assertTrue(progress.steps.first { it.id == SessionSchemeStepId.SURVEY }.done)
        assertFalse(progress.steps.first { it.id == SessionSchemeStepId.TRAINING }.done)
        assertTrue(progress.steps.first { it.id == SessionSchemeStepId.SUMMARY }.done)
    }

    @Test
    fun templateMatchesPatientStartStrip() {
        val badges = SessionSchemeProgress.schemeTemplate().steps.map { it.badge }
        assertEquals(listOf("K", "BP", "kg", "A", "▶", "T", "B", "Σ"), badges)
    }
}
