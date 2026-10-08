package pl.cardioscp.rehab.ecg

/**
 * Uśredniony zespół PQRST z jednego odprowadzenia.
 *
 * Wspólna pula globalnych zdarzeń QRS; kwalifikacja i wyrównanie **per lead**.
 * Brak dobrych zespołów → niedostępny reprezentant (bez fallbacku do całego rytmu).
 */
data class AveragePqr(
    val lead: Lead,
    val mean: DoubleArray,
    val overlays: List<DoubleArray>,
    val samplingHz: Int,
    val preSamples: Int,
    val postSamples: Int,
    val beatCount: Int,
    val rejectedBeats: Int = 0,
    val unavailableReason: String? = null,
    /** Globalne indeksy QRS użyte do nakładek na tym odprowadzeniu. */
    val sourcePeaks: IntArray = IntArray(0),
    val candidateCount: Int = 0,
    val rejectReasons: Map<String, Int> = emptyMap(),
    /** RMS rozrzutu wyrównania względem mediany (mV). */
    val alignmentSpreadMv: Double = 0.0,
    /** Ułamek okna pokryty danymi (0–1). */
    val windowCoverage: Double = 0.0,
) {
    val length: Int get() = preSamples + postSamples + 1
    val durationSec: Float get() = length / samplingHz.toFloat()
}

object AveragePqrEngine {
    /**
     * @param representativePeaks wymagane — puste / null / <2 → brak reprezentanta z powodem.
     *   Nie wolno podstawiać całej listy rytmu.
     */
    fun compute(
        leadsMv: Map<Lead, DoubleArray>,
        rPeaks: IntArray,
        samplingHz: Int,
        preMs: Int = 200,
        postMs: Int = 450,
        analysisLead: Lead = Lead.II,
        rrMedianMs: Int? = null,
        representativePeaks: IntArray? = null,
    ): AveragePqr? {
        val src = leadsMv[analysisLead] ?: return null
        if (src.isEmpty()) return null

        if (representativePeaks == null || representativePeaks.size < 2) {
            return AveragePqr(
                lead = analysisLead,
                mean = DoubleArray(0),
                overlays = emptyList(),
                samplingHz = samplingHz,
                preSamples = 0,
                postSamples = 0,
                beatCount = 0,
                rejectedBeats = rPeaks.size,
                unavailableReason = "Brak zaakceptowanych, podobnych i wyrównanych zespołów do reprezentanta.",
                sourcePeaks = IntArray(0),
                candidateCount = representativePeaks?.size ?: 0,
                rejectReasons = mapOf("no_representative_peaks" to 1),
            )
        }

        val peaks = representativePeaks
        val rr = rrMedianMs ?: if (peaks.size >= 2) {
            val gaps = IntArray(peaks.size - 1) {
                ((peaks[it + 1] - peaks[it]) * 1000.0 / samplingHz).toInt()
            }
            gaps.sorted()[gaps.size / 2]
        } else {
            800
        }

        // Okno: nie obejmuje następnego QRS. post ≤ min(żądane, 0.40·RR − 40 ms).
        val maxPostByRr = (rr * 0.40).toInt() - 40
        val safePost = minOf(postMs, maxPostByRr.coerceAtLeast(100))
        val safePre = minOf(preMs, (rr * 0.30).toInt().coerceAtLeast(60))
        val pre = ((safePre / 1000.0) * samplingHz).toInt().coerceAtLeast(1)
        val post = ((safePost / 1000.0) * samplingHz).toInt().coerceAtLeast(1)
        val len = pre + post + 1

        val rejectReasons = linkedMapOf<String, Int>()
        fun bump(reason: String) {
            rejectReasons[reason] = (rejectReasons[reason] ?: 0) + 1
        }

        // Pełne okno + brak następnego QRS w post.
        val usable = ArrayList<Int>()
        for (i in peaks.indices) {
            val peak = peaks[i]
            if (peak - pre < 0 || peak + post >= src.size) {
                bump("edge_truncated")
                continue
            }
            val next = peaks.getOrNull(i + 1)
            if (next != null && next <= peak + post) {
                bump("next_qrs_in_window")
                continue
            }
            usable += peak
        }

        if (usable.size < 2) {
            return AveragePqr(
                lead = analysisLead,
                mean = DoubleArray(len),
                overlays = emptyList(),
                samplingHz = samplingHz,
                preSamples = pre,
                postSamples = post,
                beatCount = 0,
                rejectedBeats = peaks.size - usable.size,
                unavailableReason = "Za mało pełnych zespołów bez następnego QRS w oknie.",
                sourcePeaks = IntArray(0),
                candidateCount = peaks.size,
                rejectReasons = rejectReasons,
            )
        }

        val beats = usable.map { peak -> DoubleArray(len) { i -> src[peak - pre + i] } }

        // Grupowanie morfologii: korelacja / MSE względem mediany → zostaw największy klaster.
        val mean0 = DoubleArray(len) { i ->
            val col = beats.map { it[i] }.sorted()
            col[col.size / 2]
        }
        val errors = beats.map { beat ->
            var e = 0.0
            for (i in 0 until len) {
                val d = beat[i] - mean0[i]
                e += d * d
            }
            e / len
        }
        val errMed = errors.sorted()[errors.size / 2].coerceAtLeast(1e-6)
        val clusterIdx = beats.indices.filter { errors[it] <= errMed * 3.0 }
        if (clusterIdx.size < 2) {
            for (i in beats.indices) {
                if (i !in clusterIdx) bump("morphology_outlier")
            }
            return AveragePqr(
                lead = analysisLead,
                mean = DoubleArray(0),
                overlays = emptyList(),
                samplingHz = samplingHz,
                preSamples = pre,
                postSamples = post,
                beatCount = 0,
                rejectedBeats = peaks.size,
                unavailableReason = "Brak spójnego klastra morfologii — nie mieszam różnych kształtów.",
                sourcePeaks = IntArray(0),
                candidateCount = peaks.size,
                rejectReasons = rejectReasons,
            )
        }
        for (i in beats.indices) {
            if (i !in clusterIdx) bump("morphology_outlier")
        }

        val kept = clusterIdx.map { beats[it] }
        val keptPeaks = clusterIdx.map { usable[it] }.toIntArray()
        val robust = DoubleArray(len) { i ->
            val col = kept.map { it[i] }.sorted()
            col[col.size / 2]
        }

        // Rozrzut wyrównania: RMS różnicy względem mediany w okolicy QRS (środek okna).
        val tip = pre
        val half = (0.04 * samplingHz).toInt().coerceAtLeast(2)
        var spreadAcc = 0.0
        var spreadN = 0
        for (beat in kept) {
            for (j in (tip - half).coerceAtLeast(0)..(tip + half).coerceAtMost(len - 1)) {
                val d = beat[j] - robust[j]
                spreadAcc += d * d
                spreadN++
            }
        }
        val spread = if (spreadN > 0) kotlin.math.sqrt(spreadAcc / spreadN) else 0.0
        val coverage = kept.size.toDouble() / peaks.size.coerceAtLeast(1)

        return AveragePqr(
            lead = analysisLead,
            mean = robust,
            overlays = kept,
            samplingHz = samplingHz,
            preSamples = pre,
            postSamples = post,
            beatCount = kept.size,
            rejectedBeats = peaks.size - kept.size,
            sourcePeaks = keptPeaks,
            candidateCount = peaks.size,
            rejectReasons = rejectReasons,
            alignmentSpreadMv = spread,
            windowCoverage = coverage,
        )
    }
}
