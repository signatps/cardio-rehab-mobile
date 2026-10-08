package pl.cardioscp.rehab.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrainingPlannerTest {
    @Test
    fun interval_threeCycles_structure() {
        val plan = TrainingPlan(cycles = 3, exerciseSec = 60, restSec = 60, acquireSec = 10)
        val tl = TrainingPlanner.interval(plan)
        // EKG start + 3×(W + EKG szczyt + O) = 1 + 9 = 10
        assertEquals(10, tl.phases.size)
        assertEquals(TrainingPhaseKind.ECG_REST_START, tl.phases[0].kind)
        assertEquals(TrainingPhaseKind.EXERCISE, tl.phases[1].kind)
        assertEquals(TrainingPhaseKind.ECG_PEAK, tl.phases[2].kind)
        assertEquals(TrainingPhaseKind.REST, tl.phases[3].kind)
        assertEquals(TrainingPhaseKind.EXERCISE, tl.phases[4].kind)
        assertEquals(TrainingPhaseKind.ECG_PEAK, tl.phases[5].kind)
        assertEquals(TrainingPhaseKind.REST, tl.phases[6].kind)
        assertEquals(TrainingPhaseKind.EXERCISE, tl.phases[7].kind)
        assertEquals(TrainingPhaseKind.ECG_PEAK, tl.phases[8].kind)
        assertEquals(TrainingPhaseKind.REST, tl.phases[9].kind)
        assertTrue(tl.phases.count { it.kind == TrainingPhaseKind.ECG_PEAK } == 3)
        assertTrue(tl.phases.count { it.kind == TrainingPhaseKind.EXERCISE } == 3)
    }
}
