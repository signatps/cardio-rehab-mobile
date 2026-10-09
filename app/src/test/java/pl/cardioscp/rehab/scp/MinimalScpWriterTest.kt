package pl.cardioscp.rehab.scp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.cardioscp.rehab.session.OnlineEcgFragment

class MinimalScpWriterTest {
    @Test
    fun writeAndParseRoundTrip() {
        val n = 250
        val i = ShortArray(n) { (it % 50).toShort() }
        val ii = ShortArray(n) { ((it + 3) % 50).toShort() }
        val v1 = ShortArray(n) { ((it + 7) % 50).toShort() }
        val frag = OnlineEcgFragment(
            samplingHz = 250,
            avmNanoVolts = 7100,
            leads = listOf("I" to i, "II" to ii, "V1" to v1),
        )
        val bytes = MinimalScpWriter.fromFragment(frag)
        val rec = ScpEcgParser.parse(bytes)
        assertEquals(250, rec.samplingHz)
        assertEquals(7100, rec.avm)
        assertEquals(3, rec.leads.size)
        assertEquals("I", rec.leads[0].label)
        assertEquals(n, rec.leads[0].samples.size)
        assertEquals(i[0], rec.leads[0].samples[0])
        assertTrue(rec.durationSeconds in 0.9..1.1)
    }
}
