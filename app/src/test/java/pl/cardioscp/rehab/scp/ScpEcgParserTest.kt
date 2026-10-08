package pl.cardioscp.rehab.scp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScpEcgParserTest {
    @Test
    fun parsesEhoMiniSampleLeads() {
        val bytes = javaClass.classLoader!!
            .getResourceAsStream("scp/sample.scp")!!
            .readBytes()
        val rec = ScpEcgParser.parse(bytes)
        assertEquals(500, rec.samplingHz)
        assertEquals(7100, rec.avm)
        assertEquals(3, rec.leads.size)
        assertEquals("I", rec.leads[0].label)
        assertEquals("II", rec.leads[1].label)
        assertEquals(10_000, rec.leads[0].samples.size)
        assertEquals((-12).toShort(), rec.leads[0].samples[0])
        assertTrue(rec.durationSeconds in 19.0..21.0)
    }
}
