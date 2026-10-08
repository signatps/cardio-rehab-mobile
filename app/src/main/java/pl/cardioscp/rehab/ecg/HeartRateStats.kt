package pl.cardioscp.rehab.ecg

import kotlin.math.roundToInt

/**
 * Statystyki tętna z odstępów RR.
 *
 * [keepAtypical]=true: zachowaj RR <300 ms, >2000 ms i poza 0,55–1,55 mediany
 * jako zdarzenia (min/max); mediana nadal z fizjologicznych RR gdy dostępne.
 */
data class HeartRateStats(
    val minBpm: Int,
    val avgBpm: Int,
    val maxBpm: Int,
    /** Mediana RR (preferencyjnie z fizjologicznych odstępów) — do QTc. */
    val rrMedianMs: Int,
    val intervalCount: Int,
    val status: MeasurementStatus = MeasurementStatus.Valid,
    val reason: String? = null,
) {
    fun archiveLines(): List<Pair<String, String>> = listOf(
        "min" to "$minBpm",
        "avg" to "$avgBpm",
        "max" to "$maxBpm",
    )
}

object HeartRateStatsEngine {
    private const val RR_MIN_MS = 200
    private const val RR_MAX_MS = 3_000

    fun fromPeaks(peaks: IntArray, samplingHz: Int, keepAtypical: Boolean = true): HeartRateStats? {
        if (peaks.size < 3 || samplingHz <= 0) return null
        val rrMs = IntArray(peaks.size - 1) { i ->
            ((peaks[i + 1] - peaks[i]) * 1000.0 / samplingHz).roundToInt()
        }
        return fromRrMs(rrMs, keepAtypical)
    }

    fun fromRrMs(rrMs: IntArray, keepAtypical: Boolean = true): HeartRateStats? {
        if (rrMs.size < 2) return null
        val inRange = rrMs.filter { it in RR_MIN_MS..RR_MAX_MS }
        if (inRange.size < 2) return null
        val physiological = inRange.filter { it in 300..2_000 }
        val medBase = if (physiological.size >= 2) physiological else inRange
        val med = median(medBase)
        val forAvg = if (keepAtypical) {
            inRange
        } else {
            val cleaned = physiological.filter { it in (med * 0.55).toInt()..(med * 1.55).toInt() }
            if (cleaned.size >= 2) cleaned else physiological.ifEmpty { inRange }
        }
        // Nie ucinaj HR do 300 — chwilowe RR krótkie zostają w min/max jako zdarzenia.
        val bpm = forAvg.map { (60_000.0 / it).roundToInt().coerceAtLeast(15) }
        if (bpm.isEmpty()) return null
        return HeartRateStats(
            minBpm = bpm.minOrNull() ?: return null,
            avgBpm = bpm.average().roundToInt(),
            maxBpm = bpm.maxOrNull() ?: return null,
            rrMedianMs = median(medBase),
            intervalCount = forAvg.size,
        )
    }

    /**
     * Tętno na żywo: ostatnie ~8 s; pierwsze 2 s całego zapisu pomijane tylko raz.
     * Wspólny rdzeń: [EcgAnalysisEngine.detectR] na preferowanych kanałach.
     * Krótki kontekst → status Provisional.
     */
    fun liveFromSignal(signal: DoubleArray, samplingHz: Int, windowSec: Double = 8.0): Int? {
        if (signal.size < samplingHz || samplingHz <= 0) return null
        val skip = (EcgAnalysisEngine.RHYTHM_SKIP_SEC * samplingHz).toInt()
        val win = (windowSec * samplingHz).toInt().coerceAtMost(signal.size)
        val start = maxOf(skip, signal.size - win)
        if (signal.size - start < samplingHz) return null
        val slice = signal.copyOfRange(start, signal.size)
        val peaks = EcgAnalysisEngine.detectR(slice, samplingHz, skipStartSamples = 0)
        return fromPeaks(peaks, samplingHz)?.avgBpm
    }

    fun liveFromLeads(
        leadsMv: Map<Lead, DoubleArray>,
        samplingHz: Int,
        windowSec: Double = 8.0,
    ): Int? {
        val stats = liveStatsFromLeads(leadsMv, samplingHz, windowSec) ?: return null
        return stats.avgBpm
    }

    fun liveStatsFromLeads(
        leadsMv: Map<Lead, DoubleArray>,
        samplingHz: Int,
        windowSec: Double = 8.0,
    ): HeartRateStats? {
        if (samplingHz <= 0) return null
        val any = leadsMv.values.firstOrNull() ?: return null
        if (any.size < samplingHz) return null
        val skip = (EcgAnalysisEngine.RHYTHM_SKIP_SEC * samplingHz).toInt()
        val win = (windowSec * samplingHz).toInt().coerceAtMost(any.size)
        val start = maxOf(skip, any.size - win)
        val sliceLen = any.size - start
        if (sliceLen < samplingHz) return null
        val sliced = leadsMv.mapValues { (_, s) ->
            if (s.size != any.size) s else s.copyOfRange(start, any.size)
        }
        val prepared = EcgAnalysisEngine.prepareDetectionLeads(sliced)
        // Ten sam detektor co w analizie zapisu (detectR), bez osobnego algorytmu.
        var best = IntArray(0)
        for (lead in EcgAnalysisEngine.PREFERRED_RHYTHM_LEADS) {
            val sig = prepared[lead] ?: continue
            val peaks = EcgAnalysisEngine.detectR(sig, samplingHz, skipStartSamples = 0)
            if (peaks.size > best.size) best = peaks
        }
        if (best.size < 3) {
            best = EcgAnalysisEngine.detectRRhythm(prepared, samplingHz, skipStartSamples = 0)
        }
        val stats = fromPeaks(best, samplingHz) ?: return null
        val provisional = sliceLen < (windowSec * samplingHz * 0.75).toInt()
        return if (provisional) {
            stats.copy(
                status = MeasurementStatus.Provisional,
                reason = "Krótki kontekst live (~${sliceLen * 1000 / samplingHz} ms) — HR prowizoryczne.",
            )
        } else {
            stats
        }
    }

    private fun median(values: List<Int>): Int {
        val sorted = values.sorted()
        return sorted[sorted.size / 2]
    }
}
