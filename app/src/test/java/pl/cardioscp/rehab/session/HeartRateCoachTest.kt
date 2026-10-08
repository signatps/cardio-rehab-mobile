package pl.cardioscp.rehab.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HeartRateCoachTest {
    private val limit = CycleHeartRateLimit(cycle = 1, minBpm = 90, maxBpm = 120)

    @Test
    fun belowMin_speedUp() {
        assertEquals(HeartRateCoachCue.SPEED_UP, HeartRateCoach.evaluate(80, limit))
        assertEquals("PRZYSPIESZ", HeartRateCoach.screenText(HeartRateCoachCue.SPEED_UP))
        assertEquals("Przyspiesz", HeartRateCoach.speakText(HeartRateCoachCue.SPEED_UP))
    }

    @Test
    fun aboveMax_slowDown() {
        assertEquals(HeartRateCoachCue.SLOW_DOWN, HeartRateCoach.evaluate(130, limit))
        assertEquals("ZWOLNIJ", HeartRateCoach.screenText(HeartRateCoachCue.SLOW_DOWN))
        assertEquals("Zwolnij", HeartRateCoach.speakText(HeartRateCoachCue.SLOW_DOWN))
    }

    @Test
    fun inZone_noCue() {
        assertEquals(HeartRateCoachCue.IN_ZONE, HeartRateCoach.evaluate(100, limit))
        assertNull(HeartRateCoach.screenText(HeartRateCoachCue.IN_ZONE))
        assertNull(HeartRateCoach.speakText(HeartRateCoachCue.IN_ZONE))
    }

    @Test
    fun waiting_whenNoPulse() {
        assertEquals(HeartRateCoachCue.WAITING, HeartRateCoach.evaluate(null, limit))
        assertEquals(HeartRateCoachCue.WAITING, HeartRateCoach.evaluate(0, limit))
    }
}
