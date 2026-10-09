package pl.cardioscp.rehab.session

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class LiveEcgHrTest {
    @Test
    fun fromSnapshot_detectsHrNearSyntheticRate() {
        val hz = 250
        val bpmTarget = 72
        val sec = 10
        val n = hz * sec
        val period = (60.0 * hz / bpmTarget)
        val ii = DoubleArray(n) { i ->
            val phase = (i % period) / period
            // Prosty QRS-like spike + baseline
            when {
                phase < 0.04 -> 1.2
                phase < 0.08 -> -0.3
                else -> 0.02 * sin(2 * PI * i / hz)
            }
        }
        val snap = LiveEcgSnapshot(
            samplingHz = hz,
            leads = listOf("I" to DoubleArray(n), "II" to ii, "V1" to DoubleArray(n)),
            streaming = true,
            onlineUnsupported = false,
            generation = 1,
        )
        val bpm = LiveEcgHr.fromSnapshot(snap)
        assertNotNull(bpm)
        assertTrue("bpm=$bpm", bpm!! in 55..95)
    }
}
