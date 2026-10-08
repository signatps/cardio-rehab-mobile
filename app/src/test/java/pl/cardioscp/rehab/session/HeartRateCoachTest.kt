package pl.cardioscp.rehab.session

import org.junit.Assert.assertEquals
import org.junit.Test

class HeartRateCoachTest {
    private val limit = CycleHeartRateLimit(cycle = 1, minBpm = 90, maxBpm = 120)

    @Test
    fun valueTone_inZoneNearAndOut() {
        assertEquals(HeartRateValueTone.WAITING, HeartRateCoach.valueTone(null, limit))
        assertEquals(HeartRateValueTone.OUT_OF_ZONE, HeartRateCoach.valueTone(80, limit))
        assertEquals(HeartRateValueTone.NEAR_EDGE, HeartRateCoach.valueTone(92, limit))
        assertEquals(HeartRateValueTone.IN_ZONE, HeartRateCoach.valueTone(105, limit))
        assertEquals(HeartRateValueTone.NEAR_EDGE, HeartRateCoach.valueTone(118, limit))
        assertEquals(HeartRateValueTone.OUT_OF_ZONE, HeartRateCoach.valueTone(130, limit))
    }

    @Test
    fun evaluate_cues() {
        assertEquals(HeartRateCoachCue.SPEED_UP, HeartRateCoach.evaluate(70, limit))
        assertEquals(HeartRateCoachCue.IN_ZONE, HeartRateCoach.evaluate(100, limit))
        assertEquals(HeartRateCoachCue.SLOW_DOWN, HeartRateCoach.evaluate(140, limit))
    }
}
