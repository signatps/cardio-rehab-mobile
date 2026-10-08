package pl.cardioscp.rehab.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrainingPlannerTest {
    @Test
    fun interval_threeCycles_structure() {
        val plan = TrainingPlan(cycles = 3, exerciseSec = 60, restSec = 60, acquireSec = 10)
        val tl = TrainingPlanner.interval(plan)
        // W1 O1 W2 O2 W3 + post = 6 phases
        assertEquals(6, tl.phases.size)
        assertEquals(TrainingPhaseKind.EXERCISE, tl.phases[0].kind)
        assertEquals(TrainingPhaseKind.REST, tl.phases[1].kind)
        assertEquals(TrainingPhaseKind.EXERCISE, tl.phases[2].kind)
        assertEquals(TrainingPhaseKind.REST, tl.phases[3].kind)
        assertEquals(TrainingPhaseKind.EXERCISE, tl.phases[4].kind)
        assertEquals(TrainingPhaseKind.POST_TRAINING, tl.phases[5].kind)
        assertEquals(60 + 60 + 60 + 60 + 60 + 120, tl.totalSec)
        assertTrue(tl.phases.all { it.ecgAtStart })
        assertTrue(TrainingPlanner.needsEndOfLastExerciseEcg(tl.phases[4], plan))
    }
}
