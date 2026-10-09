package pl.cardioscp.rehab.session

import pl.cardioscp.rehab.ecg.EcgAnalysisEngine
import pl.cardioscp.rehab.ecg.HeartRateStatsEngine
import pl.cardioscp.rehab.ecg.Lead
import pl.cardioscp.rehab.ecg.RecordingMode
import pl.cardioscp.rehab.scp.ScpEcgParser
import pl.cardioscp.rehab.scp.ScpRecording
import pl.cardioscp.rehab.scp.ScpToEcgLeads
import kotlin.math.roundToInt

data class EcgHrTrend(
    val startBpm: Int?,
    val avgBpm: Int?,
    val endBpm: Int?,
)

/**
 * Tętno początkowe / średnie / końcowe z zapisu SCP (z odstępów RR).
 */
object EcgHrTrendEngine {
    fun fromRecording(recording: ScpRecording): EcgHrTrend {
        return runCatching {
            val bytes = recording.file.readBytes()
            val scp = ScpEcgParser.parse(bytes)
            val leads = ScpToEcgLeads.toMillivolts(scp)
            fromLeads(leads, scp.samplingHz)
        }.getOrDefault(EcgHrTrend(null, null, null))
    }

    fun fromLeads(leadsMv: Map<Lead, DoubleArray>, samplingHz: Int): EcgHrTrend {
        if (samplingHz <= 0 || leadsMv.isEmpty()) {
            return EcgHrTrend(null, null, null)
        }
        val analysis = EcgAnalysisEngine.analyze(
            leadsMv = leadsMv,
            samplingHz = samplingHz,
            analysisLead = when {
                Lead.II in leadsMv -> Lead.II
                Lead.I in leadsMv -> Lead.I
                else -> leadsMv.keys.first()
            },
            calibration = pl.cardioscp.rehab.ecg.CalibrationInfo.unknown(),
            recordingMode = RecordingMode.Rest,
        )
        val peaks = analysis.rPeaks
        if (peaks.size < 3) {
            val avg = analysis.hrAvgBpm ?: analysis.heartRateBpm
            return EcgHrTrend(avg, avg, avg)
        }
        val rrMs = IntArray(peaks.size - 1) { i ->
            ((peaks[i + 1] - peaks[i]) * 1000.0 / samplingHz).roundToInt()
        }
        val stats = HeartRateStatsEngine.fromRrMs(rrMs) ?: run {
            val avg = analysis.hrAvgBpm ?: analysis.heartRateBpm
            return EcgHrTrend(avg, avg, avg)
        }
        val bpms = rrMs
            .filter { it in 200..3_000 }
            .map { (60_000.0 / it).roundToInt().coerceIn(15, 250) }
        if (bpms.isEmpty()) {
            return EcgHrTrend(stats.minBpm, stats.avgBpm, stats.maxBpm)
        }
        val start = bpms.take(3).average().roundToInt()
        val end = bpms.takeLast(3).average().roundToInt()
        return EcgHrTrend(start, stats.avgBpm, end)
    }

    fun shortLabel(fullLabel: String): String {
        val l = fullLabel.lowercase()
        return when {
            "kwalifik" in l -> "EKG kwalifikacyjne"
            "spoczynk" in l && "start" in l -> "EKG spoczynkowe"
            "spoczynk" in l -> "EKG spoczynkowe"
            "szczyt" in l -> {
                val m = Regex("""(\d+)\s*/\s*(\d+)""").find(fullLabel)
                if (m != null) "EKG szczyt ${m.groupValues[1]}" else "EKG szczyt wysiłku"
            }
            else -> "EKG"
        }
    }
}
