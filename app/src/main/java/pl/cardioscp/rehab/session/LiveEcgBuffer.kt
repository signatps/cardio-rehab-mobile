package pl.cardioscp.rehab.session

/**
 * Okno próbek EKG Online do rysowania w trakcie zapisu (ostatnie [windowSec] s).
 */
class LiveEcgBuffer(
    private val samplingHz: Int = 250,
    private val windowSec: Float = 8f,
) {
    private val capacity = (samplingHz * windowSec).toInt().coerceAtLeast(samplingHz)
    private val buffers = linkedMapOf<String, ArrayDeque<Double>>()
    private var _streaming = false
    private var _unsupported = false
    private var generation = 0

    @Synchronized
    fun reset() {
        buffers.clear()
        _streaming = false
        _unsupported = false
        generation += 1
    }

    @Synchronized
    fun markUnsupported() {
        _unsupported = true
        _streaming = false
        generation += 1
    }

    @Synchronized
    fun onInfo(leadLabels: List<String>) {
        _unsupported = false
        _streaming = true
        leadLabels.forEach { label ->
            buffers.getOrPut(label) { ArrayDeque() }
        }
        generation += 1
    }

    @Synchronized
    fun append(leadLabels: List<String>, rowsMv: Array<DoubleArray>) {
        if (_unsupported) return
        _streaming = true
        for (row in rowsMv) {
            leadLabels.forEachIndexed { i, label ->
                if (i >= row.size) return@forEachIndexed
                val q = buffers.getOrPut(label) { ArrayDeque() }
                q.addLast(row[i])
                while (q.size > capacity) q.removeFirst()
            }
        }
        generation += 1
    }

    @Synchronized
    fun clearStreaming() {
        _streaming = false
        generation += 1
    }

    @Synchronized
    fun snapshot(): LiveEcgSnapshot {
        val leads = buffers.map { (label, q) ->
            label to q.toDoubleArray()
        }
        return LiveEcgSnapshot(
            samplingHz = samplingHz,
            leads = leads,
            streaming = _streaming,
            onlineUnsupported = _unsupported,
            generation = generation,
        )
    }
}

data class LiveEcgSnapshot(
    val samplingHz: Int,
    val leads: List<Pair<String, DoubleArray>>,
    val streaming: Boolean,
    val onlineUnsupported: Boolean,
    val generation: Int,
) {
    val hasTrace: Boolean get() = leads.any { it.second.isNotEmpty() }
}
