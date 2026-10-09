package pl.cardioscp.rehab.session

/**
 * Ciągła taśma próbek EKG Online na czas sesji.
 * Fragmenty wycinane wg profilu treningu (spoczynek / szczyt / …).
 */
class SessionEcgTape(
    val samplingHz: Int = 250,
    maxSeconds: Int = 900,
) {
    private val capacity = (samplingHz * maxSeconds).coerceAtLeast(samplingHz)
    private val buffers = linkedMapOf<String, ArrayDeque<Short>>()
    var avmNanoVolts: Int = 7100
        private set
    var leadLabels: List<String> = emptyList()
        private set
    private var totalAppended = 0L

    @Synchronized
    fun reset() {
        buffers.clear()
        leadLabels = emptyList()
        avmNanoVolts = 7100
        totalAppended = 0L
    }

    @Synchronized
    fun onInfo(labels: List<String>, avm: Int) {
        if (avm > 0) avmNanoVolts = avm
        leadLabels = labels
        labels.forEach { buffers.getOrPut(it) { ArrayDeque() } }
    }

    /** Wiersze = próbki czasu; kolumny = kanały w kolejności [leadLabels]. */
    @Synchronized
    fun append(labels: List<String>, rows: Array<ShortArray>, avm: Int = 0) {
        if (avm > 0) avmNanoVolts = avm
        if (labels.isNotEmpty()) leadLabels = labels
        for (row in rows) {
            labels.forEachIndexed { i, label ->
                if (i >= row.size) return@forEachIndexed
                val q = buffers.getOrPut(label) { ArrayDeque() }
                q.addLast(row[i])
                while (q.size > capacity) q.removeFirst()
            }
            totalAppended++
        }
    }

    @Synchronized
    fun availableSamples(): Int = buffers.values.minOfOrNull { it.size } ?: 0

    @Synchronized
    fun availableSeconds(): Float =
        if (samplingHz <= 0) 0f else availableSamples().toFloat() / samplingHz

    /**
     * Ostatnie [seconds] s taśmy (lub mniej, jeśli bufor krótszy).
     * Zwraca null gdy brak próbek.
     */
    @Synchronized
    fun cutLastSeconds(seconds: Int): OnlineEcgFragment? {
        val need = (seconds.coerceAtLeast(1) * samplingHz).coerceAtLeast(1)
        val n = availableSamples()
        if (n <= 0) return null
        val take = need.coerceAtMost(n)
        val labels = leadLabels.ifEmpty { buffers.keys.toList() }
        if (labels.isEmpty()) return null
        val leads = labels.map { label ->
            val q = buffers[label] ?: ArrayDeque()
            val start = (q.size - take).coerceAtLeast(0)
            val samples = ShortArray(take)
            var i = 0
            for ((idx, v) in q.withIndex()) {
                if (idx >= start) {
                    samples[i++] = v
                    if (i >= take) break
                }
            }
            label to samples
        }
        return OnlineEcgFragment(
            samplingHz = samplingHz,
            avmNanoVolts = avmNanoVolts,
            leads = leads,
        )
    }
}

data class OnlineEcgFragment(
    val samplingHz: Int,
    val avmNanoVolts: Int,
    val leads: List<Pair<String, ShortArray>>,
) {
    val sampleCount: Int get() = leads.maxOfOrNull { it.second.size } ?: 0
    val durationSec: Float
        get() = if (samplingHz <= 0) 0f else sampleCount.toFloat() / samplingHz
}
