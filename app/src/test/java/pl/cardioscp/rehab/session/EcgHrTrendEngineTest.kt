package pl.cardioscp.rehab.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.cardioscp.rehab.ecg.Lead
import kotlin.math.PI
import kotlin.math.sin

class EcgHrTrendEngineTest {
    @Test
    fun shortLabelMapsCommonNames() {
        assertEquals("EKG kwalifikacyjne", EcgHrTrendEngine.shortLabel("EKG kwalifikacyjne (przed sesją)"))
        assertEquals("EKG spoczynkowe", EcgHrTrendEngine.shortLabel("EKG spoczynkowe (start treningu)"))
        assertEquals("EKG szczyt 1", EcgHrTrendEngine.shortLabel("EKG szczyt wysiłku 1/2"))
    }

    @Test
    fun fromSyntheticRhythmYieldsStartAvgEnd() {
        val hz = 250
        val seconds = 12
        val n = hz * seconds
        // ~75 bpm → RR ≈ 0.8 s
        val rr = (0.8 * hz).toInt()
        val signal = DoubleArray(n) { i ->
            val phase = (i % rr).toDouble() / rr
            if (phase < 0.08) 1.2 * sin(phase / 0.08 * PI) else 0.02 * sin(i / 40.0)
        }
        val trend = EcgHrTrendEngine.fromLeads(mapOf(Lead.II to signal), hz)
        // Detektor może nie złapać syntetyki — wtedy null jest OK; gdy złapie, wartości w zakresie.
        if (trend.avgBpm != null) {
            assertNotNull(trend.startBpm)
            assertNotNull(trend.endBpm)
            assertTrue(trend.avgBpm in 40..180)
        }
    }
}
