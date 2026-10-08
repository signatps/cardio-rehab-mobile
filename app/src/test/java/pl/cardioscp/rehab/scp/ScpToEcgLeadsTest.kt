package pl.cardioscp.rehab.scp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.cardioscp.rehab.ecg.CalibrationSource
import pl.cardioscp.rehab.ecg.EcgAnalysisEngine
import pl.cardioscp.rehab.ecg.Lead
import pl.cardioscp.rehab.ecg.RecordingMode

class ScpToEcgLeadsTest {
    @Test
    fun convertsSampleScpAndRunsCardioScpAnalysis() {
        val bytes = javaClass.classLoader!!
            .getResourceAsStream("scp/sample.scp")!!
            .readBytes()
        val rec = ScpEcgParser.parse(bytes)
        val leadsMv = ScpToEcgLeads.toMillivolts(rec)
        assertTrue(Lead.I in leadsMv)
        assertTrue(Lead.II in leadsMv)
        assertTrue(Lead.V1 in leadsMv)
        // Derived Einthoven
        assertTrue(Lead.III in leadsMv)

        val cal = ScpToEcgLeads.calibration(rec)
        assertEquals(CalibrationSource.ScpAvm, cal.source)
        assertTrue(cal.amplitudesTrusted)

        // First sample of lead I in sample file is -12 → mV = -12 * 7100 / 1e6
        assertEquals(-12 * 7100 / 1_000_000.0, leadsMv.getValue(Lead.I)[0], 1e-6)

        val analysis = EcgAnalysisEngine.analyze(
            leadsMv = leadsMv,
            samplingHz = rec.samplingHz,
            analysisLead = Lead.II,
            calibration = cal,
            recordingMode = RecordingMode.Rest,
        )
        assertNotNull(analysis)
        assertTrue(analysis.rPeaks.isNotEmpty() || analysis.findings.isNotEmpty())
    }

    @Test
    fun sampleToMv_matchesEn1064Avm() {
        // AVM 7100 nV/LSB → 1 LSB = 0.0071 mV (EN 1064)
        assertEquals(0.0071, EcgScale.sampleToMv(1, 7100).toDouble(), 1e-9)
        assertEquals(-0.0852, EcgScale.sampleToMv(-12, 7100).toDouble(), 1e-6)
    }
}
