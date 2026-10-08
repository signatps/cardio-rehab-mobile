package pl.cardioscp.rehab.scp

/**
 * SCP-ECG / paper geometry helpers.
 *
 * Section 6 AVM is nanovolts per LSB (EN 1064). EHO-MINI uses AVM=7100.
 * Paper defaults match clinical strips: **25 mm/s**, **10 mm/mV**.
 */
object EcgScale {
    /** SCP sample → millivolts. */
    fun sampleToMv(sample: Int, avmNvPerLsb: Int): Float =
        sample * (avmNvPerLsb.toFloat() / 1_000_000f)

    fun mvToSample(mv: Float, avmNvPerLsb: Int): Int =
        if (avmNvPerLsb == 0) 0 else (mv * 1_000_000f / avmNvPerLsb).toInt()

    data class Paper(
        /** mm of paper per second of signal (25 or 50). */
        val speedMmPerSec: Float = 25f,
        /** mm of paper per millivolt (5, 10, or 20). */
        val gainMmPerMv: Float = 10f,
    ) {
        fun timeToMm(seconds: Float): Float = seconds * speedMmPerSec
        fun mvToMm(mv: Float): Float = mv * gainMmPerMv
        fun samplesToMm(sampleCount: Int, samplingHz: Int): Float {
            if (samplingHz <= 0) return 0f
            return timeToMm(sampleCount.toFloat() / samplingHz)
        }
    }

    /** Small square = 1 mm; large = 5 mm (standard ECG paper). */
    const val MINOR_MM = 1f
    const val MAJOR_MM = 5f

    /** Visible vertical span per lead strip (±mV around baseline). */
    const val CHANNEL_HALF_SPAN_MV = 2f
}
