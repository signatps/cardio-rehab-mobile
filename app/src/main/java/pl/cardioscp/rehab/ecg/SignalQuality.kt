package pl.cardioscp.rehab.ecg

import kotlin.math.abs
import kotlin.math.sqrt

enum class SignalQualityKind {
    /** Sygnał fizjologiczny — wolno liczyć PQRST. */
    Good,
    /** Izolinia / brak kontaktu elektrod. */
    Isoline,
    /** Sygnał testowy sinus (kalibracja / 50 Hz bez QRS). */
    SinusTest,
    /** Zaszumiony / niemożliwy do analizy. */
    Poor,
    /** Za krótki fragment. */
    Insufficient,
}

data class SignalQuality(
    val kind: SignalQualityKind,
    val message: String,
    val peakToPeakMv: Double = 0.0,
) {
    val allowsAnalysis: Boolean get() = kind == SignalQualityKind.Good
}

/**
 * Bramka jakości sygnału przed analizą PQRST.
 *
 * Kolejność jak w narzędziu serwisowym EHO12: najpierw odrzuć izolinię
 * i sygnał sinus (test/kalibracja), dopiero potem dopuszczaj analizę zespołów.
 */
object SignalQualityGate {
    private const val IsolinePpMv = 0.08
    private const val SaturationPpMv = 5.5

    fun assess(
        leadsMv: Map<Lead, DoubleArray>,
        samplingHz: Int,
        source: String? = null,
        analysisLead: Lead = Lead.II,
    ): SignalQuality {
        if (source.equals("TEST", ignoreCase = true)) {
            return SignalQuality(
                SignalQualityKind.SinusTest,
                "Sygnał testowy aplikacji — bez automatycznej analizy PQRST.",
            )
        }
        val lead = leadsMv[analysisLead] ?: leadsMv[Lead.II]
            ?: return SignalQuality(SignalQualityKind.Insufficient, "Brak odprowadzenia do oceny jakości.")
        if (lead.size < samplingHz) {
            return SignalQuality(SignalQualityKind.Insufficient, "Za mało próbek do oceny jakości sygnału.")
        }

        // Ostatnie do 4 s — jakość bieżącego kontaktu elektrod.
        val window = lead.copyOfRange((lead.size - samplingHz * 4).coerceAtLeast(0), lead.size)
        val pp = peakToPeak(window)
        if (pp < IsolinePpMv) {
            return SignalQuality(
                SignalQualityKind.Isoline,
                "Izolinia — sprawdź elektrody i styk pacjenta.",
                pp,
            )
        }
        if (looksLikeSinus(window, samplingHz)) {
            return SignalQuality(
                SignalQualityKind.SinusTest,
                "Wykryto sygnał sinus (test/kalibracja) — bez analizy PQRST.",
                pp,
            )
        }
        // Jakość QRS na torze analizy (32 Hz); preferowane kanały gdy dostępne.
        val prepared = EcgAnalysisEngine.prepareAnalysisLeads(leadsMv)
        val peaks = if (prepared.keys.any { it in EcgAnalysisEngine.PREFERRED_RHYTHM_LEADS }) {
            EcgAnalysisEngine.detectRRhythm(prepared, samplingHz)
        } else {
            val winPrep = EcgFilters.apply(
                EcgAnalysisEngine.ANALYSIS_FILTER,
                window,
            )
            EcgAnalysisEngine.detectR(winPrep, samplingHz)
        }
        if (peaks.size < 2) {
            return SignalQuality(
                SignalQualityKind.Poor,
                "Brak wiarygodnych zespołów QRS — popraw kontakt elektrod.",
                pp,
            )
        }
        // Peak-to-peak wysoki ≠ nasycenie ADC — tylko ostrzeżenie jakości.
        if (pp > SaturationPpMv) {
            return SignalQuality(
                SignalQualityKind.Poor,
                "Bardzo duża amplituda peak-to-peak — sprawdź elektrody / artefakty (niekoniecznie clipping ADC).",
                pp,
            )
        }
        return SignalQuality(
            SignalQualityKind.Good,
            "Sygnał dostateczny do analizy rytmu.",
            pp,
        )
    }

    private fun peakToPeak(signal: DoubleArray): Double {
        if (signal.isEmpty()) return 0.0
        var min = signal[0]
        var max = signal[0]
        for (v in signal) {
            if (v < min) min = v
            if (v > max) max = v
        }
        return max - min
    }

    /**
     * Sygnał sinus: stała częstotliwość przejść przez zero (zwykle ~10 lub ~50 Hz)
     * i mała zmienność amplitudy wierzchołków — bez morfologii QRS.
     */
    private fun looksLikeSinus(signal: DoubleArray, fs: Int): Boolean {
        val mean = signal.average()
        val y = DoubleArray(signal.size) { signal[it] - mean }
        val rms = sqrt(y.sumOf { it * it } / y.size.coerceAtLeast(1))
        if (rms < 0.03) return false

        val crossings = ArrayList<Int>()
        for (i in 1 until y.size) {
            if ((y[i - 1] < 0 && y[i] >= 0) || (y[i - 1] > 0 && y[i] <= 0)) {
                crossings += i
            }
        }
        if (crossings.size < 8) return false
        val halfPeriods = IntArray(crossings.size - 1) { crossings[it + 1] - crossings[it] }
        val medianHalf = median(halfPeriods).coerceAtLeast(1)
        val cv = coefficientOfVariation(halfPeriods)
        val freqHz = fs.toDouble() / (2.0 * medianHalf)
        val sinusBand = freqHz in 8.0..12.5 || freqHz in 45.0..55.0
        if (!sinusBand || cv > 0.12) return false

        // Amplitudy lokalnych ekstremów niemal równe → sinus, nie QRS.
        val peaks = ArrayList<Double>()
        var i = 1
        while (i < y.size - 1) {
            if (y[i] >= y[i - 1] && y[i] >= y[i + 1] && y[i] > 0.4 * rms) {
                peaks += y[i]
                i += (fs * 0.05).toInt().coerceAtLeast(2)
            } else {
                i++
            }
        }
        if (peaks.size < 4) return false
        val peakCv = coefficientOfVariation(peaks.map { (it * 1000).toInt() }.toIntArray())
        // QRS ma ostrzejsze szczyty — energia pochodnej względem RMS jest wyższa.
        var derivEnergy = 0.0
        for (k in 1 until y.size) {
            val d = y[k] - y[k - 1]
            derivEnergy += d * d
        }
        derivEnergy /= y.size
        val sharpness = sqrt(derivEnergy) / rms.coerceAtLeast(1e-6)
        return peakCv < 0.18 && sharpness < 0.55
    }

    private fun median(values: IntArray): Int {
        if (values.isEmpty()) return 0
        val sorted = values.sorted()
        return sorted[sorted.size / 2]
    }

    private fun coefficientOfVariation(values: IntArray): Double {
        if (values.isEmpty()) return 1.0
        val mean = values.average()
        if (abs(mean) < 1e-9) return 1.0
        val variance = values.sumOf { (it - mean) * (it - mean) } / values.size
        return sqrt(variance) / abs(mean)
    }
}
