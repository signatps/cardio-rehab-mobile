package pl.cardioscp.rehab.ecg

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.roundToInt
import kotlin.math.sqrt

enum class MorphologyQuality {
    Good,
    Provisional,
    Unreliable,
}

/** Lokalny punkt odniesienia QRS w jednym odprowadzeniu (w obrębie globalnego zdarzenia). */
data class LeadQrsAnchor(
    val lead: Lead,
    val localRef: Int,
    val aligned: Boolean,
    val morphologyQuality: MorphologyQuality,
)

/**
 * Jedno zdarzenie rytmu: globalny indeks próbki + kotwice lokalne w dobrych kanałach.
 * Delineacja lokalna (np. V6) nie dodaje nowych zdarzeń — tylko kotwice.
 */
data class QrsRhythmEvent(
    val globalIndex: Int,
    val anchors: Map<Lead, LeadQrsAnchor>,
)

/** Pozycje załamków jednego zespołu (indeksy próbek) — do znakowania na papierze. */
data class WaveMark(
    /**
     * Marker zdarzenia QRS po konsensusie międzykanałowym (fiducjał rytmu).
     * Nie jest to samo co anatomiczne R — nie wolno go „przeskakiwać” na T/P.
     */
    val qrsEvent: Int,
    val p: Int?,
    val q: Int?,
    /** Anatomiczne R w granicach QRS (może być null przy QS). */
    val r: Int?,
    val s: Int?,
    val t: Int?,
    val qrsOn: Int? = null,
    val qrsOff: Int? = null,
    val tOff: Int? = null,
    val pOn: Int? = null,
) {
    /**
     * Nadaje się do morfologii: wyznaczone granice QRS.
     * Q i S nie są obowiązkowe (QS / rS / RSR′).
     */
    val morphologyReady: Boolean
        get() = qrsOn != null && qrsOff != null && qrsOff > qrsOn

    @Deprecated("Użyj morphologyReady — Q/S nie są wymagane", ReplaceWith("morphologyReady"))
    val isComplete: Boolean get() = morphologyReady && p != null && t != null
}

enum class QrsCandidateDecision {
    Accepted,
    Rejected,
    Uncertain,
}

/**
 * Diagnostyka jednego kandydata QRS (po scaleniu kanałów ±[EcgAnalysisEngine.CROSS_LEAD_MERGE_MS]).
 *
 * @param energy zintegrowana energia zbocza w [sourceLead] (jednostki względne).
 * @param threshold próg kandydata w [sourceLead].
 * @param confirmingLeads mierzone kanały detekcji z kandydatem w tym samym zespole.
 * @param linkedGlobalIndex indeks próbki zaakceptowanego QRS, z którym kandydat jest
 *   powiązany (Accepted: on sam; duplikat / T: najbliższy zaakceptowany zespół).
 */
data class QrsCandidateDiag(
    val id: Int,
    val sampleIndex: Int,
    val timeMs: Int,
    val sourceLead: Lead,
    val energy: Double,
    val threshold: Double,
    val confirmingLeads: List<Lead>,
    val decision: QrsCandidateDecision,
    val reason: String,
    val linkedGlobalIndex: Int?,
)

/** Granice QRS jednego zdarzenia rytmu zagregowane z kanałów morfologii. */
data class QrsBoundaryDiag(
    val globalIndex: Int,
    val qrsOn: Int?,
    val qrsOff: Int?,
    val qrsMs: Int?,
    val leadsUsed: List<Lead>,
    val aggregation: String,
    val status: MeasurementStatus,
    val reason: String?,
)

data class EcgAnalysis(
    val heartRateBpm: Int?,
    val rrMs: Int?,
    /** Odstęp PQ (P→QRS), historycznie też PR. */
    val prMs: Int?,
    val qrsMs: Int?,
    val qtMs: Int?,
    /** QTc Bazett (ms). */
    val qtcMs: Int?,
    /** QTc Fridericia (ms). */
    val qtcFMs: Int? = null,
    val axisDegrees: Int?,
    /** Uniesienie (+) / obniżenie (−) ST w mV względem linii izoelektrycznej. */
    val stMv: Double?,
    val rhythm: String,
    val findings: List<String>,
    /** Wszystkie zaakceptowane QRS rytmu (konsensus międzykanałowy). */
    val rPeaks: IntArray,
    val analysisLead: Lead = Lead.II,
    /** Automatyczne znakowanie PQRST na wybranym odprowadzeniu. */
    val waveMarks: List<WaveMark> = emptyList(),
    val hrMinBpm: Int? = null,
    val hrAvgBpm: Int? = null,
    val hrMaxBpm: Int? = null,
    val calibration: CalibrationInfo = CalibrationInfo.unknown(),
    val recordingMode: RecordingMode = RecordingMode.Rest,
    val algorithmVersion: String = EcgAnalysisEngine.ALGORITHM_VERSION,
    val prStatus: MeasurementStatus = MeasurementStatus.NotMeasurable,
    val qrsStatus: MeasurementStatus = MeasurementStatus.NotMeasurable,
    val qtStatus: MeasurementStatus = MeasurementStatus.NotMeasurable,
    val stStatus: MeasurementStatus = MeasurementStatus.NotMeasurable,
    val axisStatus: MeasurementStatus = MeasurementStatus.NotMeasurable,
    /**
     * Indeksy w [rPeaks] użyte do pomiarów morfologii (PQ/QRS/QT/ST)
     * — wspólne dla zapisu, niezależne od analysisLead.
     */
    val measurementPeakIndices: IntArray = IntArray(0),
    /**
     * QRS użyte do reprezentanta / uśrednienia — wspólny zestaw próbek w czasie;
     * zmiana analysisLead zmienia tylko przebieg nakładki, nie listę fiducjałów.
     * Puste, gdy < 2 wyrównanych i wiarygodnych zespołów (bez fallbacku do rytmu).
     */
    val representativePeaks: IntArray = IntArray(0),
    /** Jakość morfologii na [analysisLead] (dla V6 niezależna od HR z dobrych kanałów). */
    val morphologyQuality: MorphologyQuality = MorphologyQuality.Good,
    /** Zdarzenia rytmu z kotwicami per-lead (globalny indeks + lokalne ref). */
    val rhythmEvents: List<QrsRhythmEvent> = emptyList(),
    /** Każdy kandydat QRS z decyzją i powodem (przyjęty / odrzucony / niepewny). */
    val detectionDiagnostics: List<QrsCandidateDiag> = emptyList(),
    /** Zagregowane granice QRS dla każdego zdarzenia rytmu. */
    val boundaryDiagnostics: List<QrsBoundaryDiag> = emptyList(),
    val prReason: String? = null,
    val qrsReason: String? = null,
    val qtReason: String? = null,
    val stReason: String? = null,
    val axisReason: String? = null,
) {
    /** Alias: pełna lista QRS rytmu. */
    val rhythmPeaks: IntArray get() = rPeaks

    val measurementPeaks: IntArray
        get() = IntArray(measurementPeakIndices.size) { i -> rPeaks[measurementPeakIndices[i]] }

    val pqMs: Int? get() = prMs
    val heartRateStats: HeartRateStats?
        get() {
            val min = hrMinBpm ?: return null
            val avg = hrAvgBpm ?: heartRateBpm ?: return null
            val max = hrMaxBpm ?: return null
            val rr = rrMs ?: return null
            return HeartRateStats(min, avg, max, rr, rPeaks.size.coerceAtLeast(1) - 1)
        }

    fun summaryLines(): List<Pair<String, String>> = listOf(
        "Częstość" to (heartRateBpm?.let { "$it/min" } ?: "—"),
        "HR min/avg/max" to when {
            hrMinBpm != null && hrAvgBpm != null && hrMaxBpm != null ->
                "$hrMinBpm / $hrAvgBpm / $hrMaxBpm"
            else -> "—"
        },
        "RR" to (rrMs?.let { "$it ms" } ?: "—"),
        "PQ" to formatMeas(pqMs, prStatus, " ms"),
        "QRS" to formatMeas(qrsMs, qrsStatus, " ms"),
        "QT" to formatMeas(qtMs, qtStatus, " ms"),
        "QTcB" to formatMeas(qtcMs, qtStatus, " ms"),
        "QTcF" to formatMeas(qtcFMs, qtStatus, " ms"),
        "ST" to formatSt(),
        "Oś" to formatMeas(axisDegrees, axisStatus, "°"),
    )

    /** Tabela wartości; powody statusów są osobno w [measurementReasons] / [findings]. */
    fun measurementTable(): List<Pair<String, String>> = listOf(
        "HR" to when {
            hrMinBpm != null && hrAvgBpm != null && hrMaxBpm != null ->
                "$hrMinBpm / $hrAvgBpm / $hrMaxBpm"
            heartRateBpm != null -> "$heartRateBpm /min"
            else -> "—"
        },
        "PQ" to formatMeas(pqMs, prStatus, " ms"),
        "QRS" to formatMeas(qrsMs, qrsStatus, " ms"),
        "QT" to formatMeas(qtMs, qtStatus, " ms"),
        "QTcB" to formatMeas(qtcMs, qtStatus, " ms"),
        "QTcF" to formatMeas(qtcFMs, qtStatus, " ms"),
        "ST" to formatSt(),
        "Oś" to formatMeas(axisDegrees, axisStatus, "°"),
        "R-R" to (rrMs?.let { "$it ms" } ?: "—"),
    )

    /** Powody statusu Provisional / NotMeasurable dla pomiarów morfologii. */
    fun measurementReasons(): List<Pair<String, String>> = listOfNotNull(
        prReason?.let { "PQ" to it },
        qrsReason?.let { "QRS" to it },
        qtReason?.let { "QT" to it },
        stReason?.let { "ST" to it },
        axisReason?.let { "Oś" to it },
    )

    /** Pełny tekst diagnostyczny: pomiary, powody, każdy kandydat QRS i granice zespołów. */
    fun diagnosticReportText(): String = buildString {
        appendLine("CardioSCP — raport diagnostyczny analizy EKG")
        appendLine(
            "Algorytm: $algorithmVersion · odprowadzenie: ${analysisLead.label} · morfologia: $morphologyQuality",
        )
        appendLine(
            "Kalibracja: ${calibration.source}${calibration.gain?.let { " (gain $it)" } ?: ""} · tryb: $recordingMode",
        )
        appendLine("Rytm: $rhythm")
        appendLine()
        appendLine("Pomiary:")
        for ((name, value) in measurementTable()) appendLine("  $name: $value")
        val reasons = measurementReasons()
        if (reasons.isNotEmpty()) {
            appendLine("Powody statusów:")
            for ((name, reason) in reasons) appendLine("  $name — $reason")
        }
        appendLine()
        val accepted = detectionDiagnostics.count { it.decision == QrsCandidateDecision.Accepted }
        val rejected = detectionDiagnostics.count { it.decision == QrsCandidateDecision.Rejected }
        val uncertain = detectionDiagnostics.count { it.decision == QrsCandidateDecision.Uncertain }
        appendLine(
            "Detekcja QRS: ${detectionDiagnostics.size} kandydatów (przyjęte $accepted, odrzucone $rejected, " +
                "niepewne $uncertain); rytm=${rPeaks.size}, pomiary=${measurementPeakIndices.size}, " +
                "reprezentant=${representativePeaks.size}",
        )
        for (d in detectionDiagnostics) {
            val link = d.linkedGlobalIndex?.let { " → QRS@$it" } ?: ""
            appendLine(
                "  [${d.id}] ${d.timeMs} ms (próbka ${d.sampleIndex}) ${d.sourceLead.label} " +
                    "E=${"%.3g".format(d.energy)} próg=${"%.3g".format(d.threshold)} " +
                    "kanały=${d.confirmingLeads.joinToString(",") { it.label }} " +
                    "${d.decision}: ${d.reason}$link",
            )
        }
        appendLine()
        appendLine("Granice QRS (${boundaryDiagnostics.size}):")
        for (b in boundaryDiagnostics) {
            appendLine(
                "  QRS@${b.globalIndex}: on=${b.qrsOn ?: "—"} off=${b.qrsOff ?: "—"} " +
                    "QRS=${b.qrsMs?.let { "$it ms" } ?: "—"} ${b.aggregation} " +
                    "[${b.leadsUsed.joinToString(",") { it.label }}] ${b.status}" +
                    (b.reason?.let { " — $it" } ?: ""),
            )
        }
        appendLine()
        appendLine("Wnioski:")
        for (f in findings) appendLine("  • $f")
    }

    private fun formatSt(): String = when {
        stStatus == MeasurementStatus.NotMeasurable || stMv == null -> "—"
        stStatus == MeasurementStatus.Provisional -> "${"%+.2f".format(stMv)} mV?"
        else -> "${"%+.2f".format(stMv)} mV"
    }

    private fun formatMeas(value: Int?, status: MeasurementStatus, unit: String): String =
        when {
            status == MeasurementStatus.NotMeasurable || value == null -> "—"
            status == MeasurementStatus.Provisional -> "$value$unit?"
            else -> "$value$unit"
        }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EcgAnalysis) return false
        return heartRateBpm == other.heartRateBpm && rrMs == other.rrMs && findings == other.findings &&
            rPeaks.contentEquals(other.rPeaks) && analysisLead == other.analysisLead
    }

    override fun hashCode(): Int = heartRateBpm.hashCode() * 31 + rPeaks.contentHashCode()
}

object EcgAnalysisEngine {
    const val DISCLAIMER =
        "Opis wspomagający. Nie zastępuje oceny lekarza i nie jest rozpoznaniem."

    /** Wersja algorytmu — zapisuj przy ponownej analizie. */
    const val ALGORITHM_VERSION = "1.2.2-eho12"

    /**
     * Preferowane kanały rytmu (I, II, V5, V6) — wyłącznie mierzone, nigdy wyprowadzane.
     * Decyzja implementacyjna wymagająca walidacji.
     */
    val PREFERRED_RHYTHM_LEADS: List<Lead> = listOf(Lead.I, Lead.II, Lead.V5, Lead.V6)

    /**
     * Półszerokość morfologicznego klastra zespołu (ms) do szukania wierzchołka wokół zdarzenia QRS.
     * Filtry toru analizy to filtfilt (zero-phase), więc to NIE jest kompensacja opóźnienia filtra —
     * wyłącznie zakres „tego samego zespołu”, nieobejmujący T.
     */
    const val FILTER_COMPLEX_HALF_MS = 40

    @Deprecated("Użyj PREFERRED_RHYTHM_LEADS", ReplaceWith("PREFERRED_RHYTHM_LEADS"))
    val GOLDEN_FOUR: List<Lead> get() = PREFERRED_RHYTHM_LEADS

    /**
     * Okres rozruchu zapisu (dryft / ruch). Próbki zostają w zapisie;
     * detekcja rytmu startuje po tym czasie (nie dotyczy każdego okna live).
     */
    const val RHYTHM_SKIP_SEC = 2.0

    /** Tor detekcji: LP 32 Hz tłumi fałszywą energię HF (mięśnie). */
    val DETECTION_FILTER: EcgPreset = EcgPreset.HZ32

    /** Tor morfologii: izolinia + notch 50 Hz, bez LP 32 Hz — ostrzejsze zbocza QRS do delineacji. */
    val MORPHOLOGY_FILTER: EcgPreset = EcgPreset.CLINICAL

    /** Zgodność wstecz: filtr toru detekcji (okna jakości / live). */
    val ANALYSIS_FILTER: EcgPreset get() = DETECTION_FILTER

    /** Dwa kandydaty bliżej niż ten odstęp to ten sam zespół (jedyny bezwarunkowy próg czasu). */
    const val DUPLICATE_GUARD_MS = 70

    /** Strefa wczesnych kandydatów — klasyfikacja QRS vs T zamiast odrzucania. */
    const val EARLY_ZONE_FROM_MS = 120
    const val EARLY_ZONE_TO_MS = 450

    /** Tolerancja scalania kandydatów tego samego zespołu między kanałami. */
    const val CROSS_LEAD_MERGE_MS = 40

    private const val NORMAL_ZONE_MIN_REF_RATIO = 0.12
    private const val EARLY_ACCEPT_PREV_RATIO = 0.5
    private const val EARLY_REJECT_PREV_RATIO = 0.35
    private const val TAIL_ACCEPT_PREV_RATIO = 0.8
    private const val REPLACE_PREV_RATIO = 2.5
    private const val MIN_NORMAL_SNR = 2.0
    private const val MIN_EARLY_SNR = 3.0
    private const val MIN_COMPLEX_PTP_MV = 0.02

    @Volatile
    private var lastDiagnostics: List<QrsCandidateDiag> = emptyList()

    /** Diagnostyka ostatniego wywołania [detectRRhythm] / [analyze]. */
    fun lastDetectionDiagnostics(): List<QrsCandidateDiag> = lastDiagnostics

    /** Tor detekcji rytmu: FIR 32 Hz na każdym kanale. */
    fun prepareDetectionLeads(leadsMv: Map<Lead, DoubleArray>): Map<Lead, DoubleArray> =
        leadsMv.mapValues { (_, samples) -> EcgFilters.apply(DETECTION_FILTER, samples) }

    /** Tor morfologii (delineacja / oś): izolinia + notch 50 Hz. */
    fun prepareMorphologyLeads(leadsMv: Map<Lead, DoubleArray>): Map<Lead, DoubleArray> =
        leadsMv.mapValues { (_, samples) -> EcgFilters.apply(MORPHOLOGY_FILTER, samples) }

    /** Zgodność wstecz — tor morfologii. */
    fun prepareAnalysisLeads(leadsMv: Map<Lead, DoubleArray>): Map<Lead, DoubleArray> =
        prepareMorphologyLeads(leadsMv)

    /** Kanały detekcji: mierzone I/II/V5/V6; gdy brak — inne mierzone. Nigdy wyprowadzane. */
    fun detectionLeadsFor(available: Set<Lead>): List<Lead> {
        val preferred = PREFERRED_RHYTHM_LEADS.filter { it in available && it.measured }
        return preferred.ifEmpty { Lead.measuredOrder.filter { it in available } }
    }

    fun blocked(
        message: String,
        analysisLead: Lead = Lead.II,
        rPeaks: IntArray = IntArray(0),
        calibration: CalibrationInfo = CalibrationInfo.unknown(),
        recordingMode: RecordingMode = RecordingMode.Rest,
        detectionDiagnostics: List<QrsCandidateDiag> = emptyList(),
    ) = EcgAnalysis(
        heartRateBpm = null,
        rrMs = null,
        prMs = null,
        qrsMs = null,
        qtMs = null,
        qtcMs = null,
        qtcFMs = null,
        axisDegrees = null,
        stMv = null,
        rhythm = message,
        findings = listOf(message),
        rPeaks = rPeaks,
        analysisLead = analysisLead,
        waveMarks = emptyList(),
        calibration = calibration,
        recordingMode = recordingMode,
        detectionDiagnostics = detectionDiagnostics,
        prReason = message,
        qrsReason = message,
        qtReason = message,
        stReason = message,
        axisReason = message,
    )

    fun analyze(
        leadsMv: Map<Lead, DoubleArray>,
        samplingHz: Int,
        analysisLead: Lead = Lead.II,
        calibration: CalibrationInfo = CalibrationInfo.unknown(),
        recordingMode: RecordingMode = RecordingMode.Rest,
    ): EcgAnalysis {
        val fs = samplingHz
        if (fs <= 0) {
            return blocked(
                "Nieprawidłowa częstotliwość próbkowania.",
                analysisLead,
                calibration = calibration,
                recordingMode = recordingMode,
            )
        }
        // Dwa tory: detekcja (32 Hz) do rytmu, morfologia (izolinia + 50 Hz) do delineacji/osi.
        val detectionLeads = prepareDetectionLeads(leadsMv)
        val morphLeads = prepareMorphologyLeads(leadsMv)
        if (!morphLeads.containsKey(analysisLead)) {
            return blocked(
                "Brak odprowadzenia ${analysisLead.label}.",
                analysisLead,
                calibration = calibration,
                recordingMode = recordingMode,
            )
        }

        val detection = detectRhythmInternal(detectionLeads, fs, null)
        lastDiagnostics = detection.diagnostics
        val peaksAll = detection.peaks
        if (peaksAll.size < 3) {
            return blocked(
                "Za mało zespołów QRS, żeby zmierzyć rytm. Oceń zapis wzrokowo.",
                analysisLead,
                peaksAll,
                calibration,
                recordingMode,
                detection.diagnostics,
            )
        }
        val rrAll = IntArray(peaksAll.size - 1) { peaksAll[it + 1] - peaksAll[it] }
        val rrAllMs = IntArray(rrAll.size) { samplesToMs(rrAll[it], fs) }
        val hrStats = HeartRateStatsEngine.fromRrMs(rrAllMs, keepAtypical = true)
            ?: HeartRateStatsEngine.fromPeaks(peaksAll, fs)
        val rrMs = hrStats?.rrMedianMs ?: samplesToMs(median(rrAll), fs)
        val rrMedianSamples = msToSamples(rrMs, fs)
        val hr = hrStats?.avgBpm ?: (60_000.0 / rrMs.coerceAtLeast(1)).roundToInt()
        val irregular = coefficientOfVariation(rrAll) > 0.12

        val morphCtx = HashMap<Lead, MorphLead>()
        fun ctxFor(lead: Lead): MorphLead = morphCtx.getOrPut(lead) { morphLead(lead, morphLeads.getValue(lead)) }
        val beatCache = HashMap<Lead, List<Beat>>()
        fun beatsFor(lead: Lead): List<Beat> = beatCache.getOrPut(lead) {
            val ctx = ctxFor(lead)
            peaksAll.indices.map { k ->
                measureBeat(
                    ctx,
                    peaksAll[k],
                    peaksAll.getOrNull(k - 1),
                    peaksAll.getOrNull(k + 1),
                    fs,
                    rrMs,
                    calibration,
                )
            }
        }

        // Ołów referencyjny granic (II/I) — niezależny od analysisLead UI.
        val boundaryLead = when {
            morphLeads.containsKey(Lead.II) -> Lead.II
            morphLeads.containsKey(Lead.I) -> Lead.I
            else -> analysisLead
        }
        val anchorLeads = (listOf(boundaryLead) + PREFERRED_RHYTHM_LEADS)
            .distinct()
            .filter { morphLeads.containsKey(it) }
        val rhythmEvents = peaksAll.indices.map { k ->
            QrsRhythmEvent(
                globalIndex = peaksAll[k],
                anchors = anchorLeads.associateWith { lead ->
                    val b = beatsFor(lead)[k]
                    LeadQrsAnchor(lead, b.localRef, b.aligned, b.morphologyQuality)
                },
            )
        }
        val boundaryDiagnostics = peaksAll.indices.map { k ->
            aggregateBoundaries(peaksAll[k], anchorLeads.map { it to beatsFor(it)[k] }, fs)
        }

        val referenceBeats = beatsFor(boundaryLead)
        val morphIdx = referenceBeats.indices.filter { referenceBeats[it].morphologyReady }.toIntArray()

        // Markery na analysisLead — fiducjał = globalny indeks; bez nowych zdarzeń rytmu.
        val displayBeats = beatsFor(analysisLead)
        val marks = displayBeats.map { it.toWaveMark() }
        val leadMorphQuality = aggregateMorphologyQuality(displayBeats.map { it.morphologyQuality })

        val amplitudesOk = calibration.amplitudesTrusted
        val morphologyAllowed = recordingMode == RecordingMode.Rest || morphIdx.size >= 5
        val morphBlockedForLead = leadMorphQuality == MorphologyQuality.Unreliable

        // Reprezentant: tylko wyrównane, niebędące Unreliable w ołowiu referencyjnym. <2 → brak.
        val alignedEvents = rhythmEvents.filter { ev ->
            val a = ev.anchors[boundaryLead]
            a != null && a.aligned && a.morphologyQuality != MorphologyQuality.Unreliable
        }.map { it.globalIndex }.toIntArray()
        val representativePeaks = if (alignedEvents.size < 2) {
            IntArray(0)
        } else {
            selectRepresentativePeaks(morphLeads.getValue(boundaryLead), alignedEvents, fs, rrMs)
        }

        val leadLabel = analysisLead.label
        val blockReason: String? = when {
            !morphologyAllowed ->
                "Tryb ruchu: za mało zespołów z granicami QRS (${morphIdx.size} < 5)."
            morphBlockedForLead ->
                "Morfologia $leadLabel niewiarygodna — HR zachowane z kanałów detekcji, interwał nieoznaczalny."
            else -> null
        }
        val ready = displayBeats.filter { it.morphologyReady }
        var prAgg: Agg<Int>
        var qrsAgg: Agg<Int>
        var qtAgg: Agg<Int>
        var stAgg: Agg<Double>
        if (blockReason != null || ready.isEmpty()) {
            val why = blockReason ?: "Brak zespołów z wyznaczonymi granicami QRS na $leadLabel."
            prAgg = Agg.none(why)
            qrsAgg = Agg.none(why)
            qtAgg = Agg.none(why)
            stAgg = Agg.none(why)
        } else {
            prAgg = aggregateMeasurement(ready, { it.prMs }, { it.prStatus }, { it.prReason })
            qrsAgg = aggregateMeasurement(ready, { it.qrsMs }, { it.qrsStatus }, { it.qrsReason })
            qtAgg = if (recordingMode == RecordingMode.Motion) {
                Agg.none("Tryb ruchu — QT niedostępne do czasu walidacji.")
            } else {
                aggregateMeasurement(ready, { it.qtMs }, { it.qtStatus }, { it.qtReason })
            }
            stAgg = when {
                recordingMode == RecordingMode.Motion -> Agg.none("Tryb ruchu — ST niedostępne do czasu walidacji.")
                !amplitudesOk -> Agg.none("Nieznana kalibracja — ST niedostępne.")
                else -> aggregateMeasurement(ready, { it.stMv }, { it.stStatus }, { it.stReason })
            }
        }
        if (leadMorphQuality == MorphologyQuality.Provisional) {
            val why = "Morfologia $leadLabel prowizoryczna."
            prAgg = prAgg.downgraded(why)
            qrsAgg = qrsAgg.downgraded(why)
            qtAgg = qtAgg.downgraded(why)
            stAgg = stAgg.downgraded(why)
        }

        val rrSec = (rrMs / 1000.0).coerceIn(0.3, 2.0)
        val qtcB = qtAgg.value?.let { (it / sqrt(rrSec)).roundToInt() }
        val qtcF = qtAgg.value?.let { (it / cbrt(rrSec)).roundToInt() }

        // Oś: niezależna od analysisLead (I/aVF + globalne granice).
        val axis = when {
            recordingMode == RecordingMode.Motion ->
                AxisResult(null, MeasurementStatus.NotMeasurable, "Tryb ruchu — oś niedostępna do czasu walidacji.")
            !amplitudesOk ->
                AxisResult(null, MeasurementStatus.NotMeasurable, "Nieznana kalibracja — oś niedostępna.")
            morphIdx.isEmpty() ->
                AxisResult(null, MeasurementStatus.NotMeasurable, "Brak zespołów z granicami QRS.")
            else -> computeAxis(
                morphLeads,
                peaksAll,
                boundaryDiagnostics,
                referenceBeats,
                if (morphLeads.containsKey(Lead.I)) beatsFor(Lead.I) else null,
                fs,
                rrMedianSamples,
            )
        }

        val pFound = ready.isNotEmpty() && prAgg.value != null && ready.count { it.pFound } * 2 >= ready.size

        val findings = mutableListOf<String>()
        findings += "Algorytm $ALGORITHM_VERSION · rytm=${peaksAll.size} · pomiary=${morphIdx.size} · reprezentant=${representativePeaks.size}."
        val diag = detection.diagnostics
        findings += "Detekcja QRS: kandydaci=${diag.size}, przyjęte=${diag.count { it.decision == QrsCandidateDecision.Accepted }}, " +
            "odrzucone=${diag.count { it.decision == QrsCandidateDecision.Rejected }}, " +
            "niepewne=${diag.count { it.decision == QrsCandidateDecision.Uncertain }}."
        findings += "Morfologia $leadLabel: $leadMorphQuality."
        if (morphBlockedForLead) {
            findings += "PQ/QRS/QT dla $leadLabel nieoznaczalne — zachowano HR z dobrych kanałów."
        }
        if (!amplitudesOk) {
            findings += "Nieznana kalibracja — ST i progi mV niedostępne."
        }
        if (recordingMode == RecordingMode.Motion) {
            findings += "Tryb ruchu — morfologia/ST ograniczone do czasu walidacji."
        }
        if (representativePeaks.isEmpty()) {
            findings += "Reprezentant niedostępny — brak ≥2 wyrównanych, wiarygodnych zespołów."
        }
        val rhythm = when {
            irregular -> "Rytm niemiarowy (CV RR). Do opisu lekarza — bez automatycznego AF."
            pFound && hr in 50..100 -> "Rytm miarowy, częstość typowa, widoczny załamek P."
            hr in 50..100 -> "Rytm miarowy. Załamek P niepewny lub nieoznaczony."
            hr < 50 -> "Częstość wolna."
            else -> "Częstość szybka."
        }
        if (hr < 50) findings += "Uwaga: częstość < 50/min (próg konfigurowalny)."
        if (hr > 100) findings += "Uwaga: częstość > 100/min (próg konfigurowalny)."
        qrsAgg.value?.let {
            if (it >= 120 && qrsAgg.status == MeasurementStatus.Valid) findings += "Szeroki QRS (≥ 120 ms)."
        }
        qtcB?.let {
            if (qtAgg.status == MeasurementStatus.Valid) {
                if (it >= 460) findings += "QTcB ≥ 460 ms — oceń zapis (próg konfigurowalny)."
                if (it in 1..349) findings += "QTcB < 350 ms — oceń zapis (próg konfigurowalny)."
            }
        }
        if (stAgg.status == MeasurementStatus.Valid) {
            stAgg.value?.let {
                if (it >= 0.1) findings += "ST ≥ +0,1 mV (próg pomiarowy — nie rozpoznanie niedokrwienia)."
                if (it <= -0.1) findings += "ST ≤ −0,1 mV (próg pomiarowy — nie rozpoznanie niedokrwienia)."
            }
        }
        if (axis.status == MeasurementStatus.Valid) {
            axis.value?.let {
                when {
                    it < -30 && it >= -90 -> findings += "Oś odchylona w lewo."
                    it > 90 && it <= 180 -> findings += "Oś odchylona w prawo."
                }
            }
        }
        if (findings.none {
                it.startsWith("Uwaga") || it.startsWith("Szeroki") || it.startsWith("QTc") ||
                    it.startsWith("ST") || it.startsWith("Oś")
            }
        ) {
            findings += "Bez automatycznych odchyleń w dostępnych pomiarach."
        }
        listOf(
            "PQ" to prAgg.reason,
            "QRS" to qrsAgg.reason,
            "QT" to qtAgg.reason,
            "ST" to stAgg.reason,
            "oś" to axis.reason,
        ).forEach { (name, reason) -> if (reason != null) findings += "Pomiar $name — $reason" }

        return EcgAnalysis(
            heartRateBpm = hr,
            rrMs = rrMs,
            prMs = prAgg.value,
            qrsMs = qrsAgg.value,
            qtMs = qtAgg.value,
            qtcMs = qtcB,
            qtcFMs = qtcF,
            axisDegrees = axis.value,
            stMv = stAgg.value,
            rhythm = rhythm,
            findings = findings,
            rPeaks = peaksAll,
            analysisLead = analysisLead,
            waveMarks = marks,
            hrMinBpm = hrStats?.minBpm,
            hrAvgBpm = hrStats?.avgBpm ?: hr,
            hrMaxBpm = hrStats?.maxBpm,
            calibration = calibration,
            recordingMode = recordingMode,
            prStatus = prAgg.status,
            qrsStatus = qrsAgg.status,
            qtStatus = qtAgg.status,
            stStatus = stAgg.status,
            axisStatus = axis.status,
            measurementPeakIndices = morphIdx,
            representativePeaks = representativePeaks,
            morphologyQuality = leadMorphQuality,
            rhythmEvents = rhythmEvents,
            detectionDiagnostics = detection.diagnostics,
            boundaryDiagnostics = boundaryDiagnostics,
            prReason = prAgg.reason,
            qrsReason = qrsAgg.reason,
            qtReason = qtAgg.reason,
            stReason = stAgg.reason,
            axisReason = axis.reason,
        )
    }

    // ------------------------------------------------------------------
    // Detekcja QRS
    // ------------------------------------------------------------------

    /**
     * Detekcja QRS w jednym kanale (np. HR live): ten sam detektor kandydatów
     * i klasyfikator wczesnych zespołów co [detectRRhythm], bez potwierdzenia międzykanałowego.
     *
     * @param skipStartSamples pomiń początek zapisu (artefakty ruchu / dryft izolinii).
     */
    fun detectR(signal: DoubleArray, fs: Int, skipStartSamples: Int = 0): IntArray =
        detectCore(listOf(Lead.II to signal), fs, skipStartSamples).peaks

    /**
     * Konsensus QRS z mierzonych kanałów preferowanych (I, II, V5, V6): kandydaci per kanał,
     * scalanie ±40 ms, klasyfikacja wczesnych kandydatów (QRS vs T). Diagnostyka → [lastDetectionDiagnostics].
     */
    fun detectRRhythm(
        leadsMv: Map<Lead, DoubleArray>,
        fs: Int,
        skipStartSamples: Int? = null,
    ): IntArray {
        val result = detectRhythmInternal(leadsMv, fs, skipStartSamples)
        lastDiagnostics = result.diagnostics
        return result.peaks
    }

    private fun detectRhythmInternal(
        leadsMv: Map<Lead, DoubleArray>,
        fs: Int,
        skipStartSamples: Int?,
    ): DetectionResult {
        if (fs <= 0) return DetectionResult(IntArray(0), emptyList())
        val skip = skipStartSamples ?: (RHYTHM_SKIP_SEC * fs).roundToInt().coerceAtLeast(0)
        val leads = detectionLeadsFor(leadsMv.keys)
        if (leads.isEmpty()) return DetectionResult(IntArray(0), emptyList())
        return detectCore(leads.map { it to leadsMv.getValue(it) }, fs, skip)
    }

    private class LeadCandidate(
        val lead: Lead,
        val energyIdx: Int,
        val tip: Int,
        val energy: Double,
        val slopeEnergy: Double,
        val preSkip: Boolean,
    )

    private class LeadDetection(
        val lead: Lead,
        val x: DoubleArray,
        val threshold: Double,
        val candidates: List<LeadCandidate>,
        val refSlopeEnergy: Double,
        val slopeNoise: Double,
    ) {
        fun ratioRef(slopeEnergy: Double): Double = slopeEnergy / (refSlopeEnergy + 1e-12)
        val quality: Double get() = refSlopeEnergy / (slopeNoise * slopeNoise + 1e-9)
    }

    private class MergedCandidate(
        val members: List<LeadCandidate>,
        val globalTip: Int,
        val source: LeadCandidate,
        val preSkip: Boolean,
    )

    private class DetectionResult(
        val peaks: IntArray,
        val diagnostics: List<QrsCandidateDiag>,
    )

    private fun detectCore(
        leads: List<Pair<Lead, DoubleArray>>,
        fs: Int,
        skipStartSamples: Int,
    ): DetectionResult {
        if (fs <= 0) return DetectionResult(IntArray(0), emptyList())
        val dets = leads
            .mapNotNull { (lead, sig) -> findLeadCandidates(lead, sig, fs, skipStartSamples) }
            .filter { d -> d.candidates.any { !it.preSkip } }
        if (dets.isEmpty()) return DetectionResult(IntArray(0), emptyList())
        val byLead = dets.associateBy { it.lead }
        val primary = dets.maxByOrNull { it.quality } ?: return DetectionResult(IntArray(0), emptyList())
        val nLeads = dets.size
        val minConfirm = if (nLeads >= 3) 2 else 1
        val signalLen = dets.minOf { it.x.size }
        val skip = skipStartSamples.coerceIn(0, (signalLen - fs / 2).coerceAtLeast(0))
        val complexHalf = msToSamples(FILTER_COMPLEX_HALF_MS, fs)
        val snapHalf = msToSamples(FILTER_COMPLEX_HALF_MS / 2, fs)
        val seHalf = msToSamples(36, fs)

        val merged = clusterCandidates(dets.flatMap { it.candidates }, msToSamples(CROSS_LEAD_MERGE_MS, fs))
            .map { members ->
                val source = members.maxByOrNull { byLead.getValue(it.lead).ratioRef(it.slopeEnergy) }!!
                val tips = members.map { it.tip }.sorted()
                val medTip = tips[(tips.size - 1) / 2]
                val snapLead = if (members.any { it.lead == primary.lead }) primary else byLead.getValue(source.lead)
                val tip = refineWithinComplex(snapLead.x, medTip, snapHalf).coerceIn(0, signalLen - 1)
                MergedCandidate(members, tip, source, tip < skip)
            }
            .sortedBy { it.globalTip }

        val decisions = arrayOfNulls<QrsCandidateDecision>(merged.size)
        val reasons = arrayOfNulls<String>(merged.size)
        val accepted = ArrayList<Int>()
        val acceptedRefRatio = HashMap<Int, Double>()
        val acceptedWeakBasis = HashMap<Int, Boolean>()

        fun decide(ci: Int, decision: QrsCandidateDecision, reason: String) {
            decisions[ci] = decision
            reasons[ci] = reason
        }

        for (ci in merged.indices) {
            val c = merged[ci]
            if (c.preSkip) {
                decide(ci, QrsCandidateDecision.Rejected, "Okres rozruchu zapisu (< ${samplesToMs(skip, fs)} ms).")
                continue
            }
            val srcDet = byLead.getValue(c.source.lead)
            val ptp = peakToPeak(srcDet.x, c.source.tip, complexHalf)
            if (ptp < MIN_COMPLEX_PTP_MV) {
                decide(ci, QrsCandidateDecision.Rejected, "Amplituda ${"%.3f".format(ptp)} mV poniżej progu szumu.")
                continue
            }
            val ratioRef = c.members.associate { it.lead to byLead.getValue(it.lead).ratioRef(it.slopeEnergy) }
            val medRef = medianD(ratioRef.values)
            val votesRef = ratioRef.values.count { it >= NORMAL_ZONE_MIN_REF_RATIO }
            val snr = localSnr(srcDet, c.source.tip, fs)
            val prevIdx = accepted.lastOrNull()
            val prev = prevIdx?.let { merged[it] }
            val dtMs = prev?.let { samplesToMs(c.globalTip - it.globalTip, fs) }
            val leadsTxt = c.members.joinToString(",") { it.lead.label }

            when {
                prev != null && dtMs != null && dtMs < DUPLICATE_GUARD_MS -> decide(
                    ci,
                    QrsCandidateDecision.Rejected,
                    "Ten sam zespół co poprzedni (Δ=$dtMs ms < $DUPLICATE_GUARD_MS ms) — scalony.",
                )
                prev == null || dtMs == null || dtMs >= EARLY_ZONE_TO_MS -> when {
                    votesRef >= minConfirm && snr >= MIN_NORMAL_SNR -> {
                        val head = if (dtMs == null) "Pierwszy zespół" else "Δ=$dtMs ms"
                        decide(
                            ci,
                            QrsCandidateDecision.Accepted,
                            "$head; QRS-podobny w $votesRef/$nLeads kan., energia ${pct(medRef)} referencji, SNR ${f1(snr)}.",
                        )
                        acceptedRefRatio[ci] = medRef
                        acceptedWeakBasis[ci] = accepted.isEmpty()
                        accepted += ci
                    }
                    c.members.size < minConfirm -> decide(
                        ci,
                        QrsCandidateDecision.Uncertain,
                        "Widoczny tylko w kanale $leadsTxt — możliwy artefakt, bez potwierdzenia.",
                    )
                    snr < MIN_NORMAL_SNR -> decide(
                        ci,
                        QrsCandidateDecision.Uncertain,
                        "Niski lokalny SNR (${f1(snr)}) — niepewny zespół.",
                    )
                    else -> decide(
                        ci,
                        QrsCandidateDecision.Rejected,
                        "Energia zbocza ${pct(medRef)} referencji QRS w $leadsTxt — fala P/T lub szum.",
                    )
                }
                else -> {
                    val ratioPrev = c.members.associate { m ->
                        val d = byLead.getValue(m.lead)
                        val prevLocal = refineWithinComplex(d.x, prev.globalTip, complexHalf)
                        m.lead to m.slopeEnergy / (slopeEnergy(d.x, prevLocal, seHalf) + 1e-12)
                    }
                    val medPrev = medianD(ratioPrev.values)
                    val votesQrs = c.members.count { m ->
                        (ratioRef[m.lead] ?: 0.0) >= NORMAL_ZONE_MIN_REF_RATIO &&
                            (ratioPrev[m.lead] ?: 0.0) >= EARLY_ACCEPT_PREV_RATIO
                    }
                    val tail = dtMs < EARLY_ZONE_FROM_MS
                    val needPrev = if (tail) TAIL_ACCEPT_PREV_RATIO else EARLY_ACCEPT_PREV_RATIO
                    val separated = !tail || quietGapBetween(srcDet.x, prev.globalTip, c.globalTip, fs)
                    val qrsLike = votesQrs >= minConfirm && medPrev >= needPrev && snr >= MIN_EARLY_SNR && separated
                    when {
                        qrsLike -> {
                            val canReplace = medPrev >= REPLACE_PREV_RATIO &&
                                (acceptedRefRatio[prevIdx] ?: 1.0) < 0.5 &&
                                acceptedWeakBasis[prevIdx] == true
                            if (canReplace) {
                                accepted.removeAt(accepted.lastIndex)
                                decide(
                                    prevIdx,
                                    QrsCandidateDecision.Rejected,
                                    "Zastąpiony silniejszym zespołem po $dtMs ms (energia ×${f1(medPrev)}) — prawdopodobnie P/T.",
                                )
                            }
                            decide(
                                ci,
                                QrsCandidateDecision.Accepted,
                                "Wczesny zespół Δ=$dtMs ms: QRS-podobny w $votesQrs/$nLeads kan., " +
                                    "energia ${pct(medPrev)} poprzedniego QRS, SNR ${f1(snr)}.",
                            )
                            acceptedRefRatio[ci] = medRef
                            acceptedWeakBasis[ci] = true
                            accepted += ci
                        }
                        medPrev < EARLY_REJECT_PREV_RATIO -> decide(
                            ci,
                            QrsCandidateDecision.Rejected,
                            "T-podobny: Δ=$dtMs ms, energia zbocza ${pct(medPrev)} poprzedniego QRS.",
                        )
                        tail && !separated -> decide(
                            ci,
                            QrsCandidateDecision.Rejected,
                            "Ogon poprzedniego zespołu (Δ=$dtMs ms, brak izolinii pomiędzy).",
                        )
                        else -> decide(
                            ci,
                            QrsCandidateDecision.Uncertain,
                            "Niejednoznaczny: Δ=$dtMs ms, energia ${pct(medPrev)} poprzedniego, " +
                                "QRS-podobny w $votesQrs/$nLeads kan., SNR ${f1(snr)}.",
                        )
                    }
                }
            }
        }

        val peaks = accepted.map { merged[it].globalTip }.toIntArray()
        val linkTol = msToSamples(600, fs)
        val diagnostics = merged.indices.map { ci ->
            val c = merged[ci]
            val decision = decisions[ci] ?: QrsCandidateDecision.Uncertain
            val linked = if (decision == QrsCandidateDecision.Accepted) {
                c.globalTip
            } else {
                nearestPeak(peaks, c.globalTip, linkTol)
            }
            QrsCandidateDiag(
                id = ci + 1,
                sampleIndex = c.globalTip,
                timeMs = samplesToMs(c.globalTip, fs),
                sourceLead = c.source.lead,
                energy = c.source.energy,
                threshold = byLead.getValue(c.source.lead).threshold,
                confirmingLeads = c.members.map { it.lead }.distinct(),
                decision = decision,
                reason = reasons[ci] ?: "Brak decyzji.",
                linkedGlobalIndex = linked,
            )
        }
        return DetectionResult(peaks, diagnostics)
    }

    /**
     * Kandydaci w jednym kanale: energia pochodnej → wycentrowana integracja → lokalne maksima
     * powyżej progu (unikalne w ±[DUPLICATE_GUARD_MS]/2) → wierzchołek w obrębie zespołu.
     */
    private fun findLeadCandidates(lead: Lead, signal: DoubleArray, fs: Int, skipStartSamples: Int): LeadDetection? {
        val n = signal.size
        if (n < fs || n < 8) return null
        val skip = skipStartSamples.coerceIn(0, (n - fs / 2).coerceAtLeast(0))
        val x = detrend(signal, fs)
        val energy = DoubleArray(n)
        for (i in 1 until n - 1) {
            val d = (x[i + 1] - x[i - 1]) * 0.5
            energy[i] = d * d
        }
        val integrated = centeredSum(energy, msToSamples(100, fs).coerceAtLeast(3))
        val threshold = percentile(integrated.copyOfRange(skip, n), 0.92) * 0.25
        if (threshold <= 1e-12) return null

        val guardHalf = (msToSamples(DUPLICATE_GUARD_MS, fs) / 2).coerceAtLeast(1)
        val complexHalf = msToSamples(FILTER_COMPLEX_HALF_MS, fs)
        val seHalf = msToSamples(36, fs)
        // Szukaj energii lekko przed skip — zespół na granicy nie może „przeskoczyć” na T.
        val lookback = msToSamples(100, fs)
        val candidates = ArrayList<LeadCandidate>()
        var i = (skip - lookback).coerceAtLeast(1)
        while (i < n - 1) {
            if (integrated[i] >= threshold && isLocalMax(integrated, i, guardHalf)) {
                val tip = refineWithinComplex(x, i, complexHalf)
                candidates += LeadCandidate(
                    lead = lead,
                    energyIdx = i,
                    tip = tip,
                    energy = integrated[i],
                    slopeEnergy = slopeEnergy(x, tip, seHalf),
                    preSkip = tip < skip,
                )
                i += guardHalf
            } else {
                i++
            }
        }
        val post = candidates.filter { !it.preSkip }
        val refSe = if (post.isEmpty()) 0.0 else percentile(post.map { it.slopeEnergy }.toDoubleArray(), 0.75)
        val diffs = DoubleArray((n - 1 - skip).coerceAtLeast(0)) { k -> abs(x[skip + k + 1] - x[skip + k]) }
        val slopeNoise = 1.4826 * medianOfArray(diffs)
        return LeadDetection(lead, x, threshold, candidates, refSe, slopeNoise)
    }

    /** Grupowanie po centrum energii (±tol) — niezależne od polarności dominującego wychylenia. */
    private fun clusterCandidates(all: List<LeadCandidate>, tol: Int): List<List<LeadCandidate>> {
        if (all.isEmpty()) return emptyList()
        val sorted = all.sortedBy { it.energyIdx }
        val out = ArrayList<List<LeadCandidate>>()
        var current = ArrayList<LeadCandidate>()
        var anchor = sorted.first().energyIdx
        for (c in sorted) {
            if (current.isNotEmpty() && c.energyIdx - anchor > tol) {
                out += onePerLead(current)
                current = ArrayList()
                anchor = c.energyIdx
            }
            current += c
        }
        if (current.isNotEmpty()) out += onePerLead(current)
        return out
    }

    private fun onePerLead(group: List<LeadCandidate>): List<LeadCandidate> =
        group.groupBy { it.lead }.values.map { g -> g.maxByOrNull { it.slopeEnergy }!! }

    private fun localSnr(d: LeadDetection, tip: Int, fs: Int): Double {
        val x = d.x
        val half = msToSamples(FILTER_COMPLEX_HALF_MS, fs)
        val from = (tip - half).coerceAtLeast(0)
        val to = (tip + half).coerceAtMost(x.size - 2)
        var m = 0.0
        for (i in from..to) {
            val v = abs(x[i + 1] - x[i])
            if (v > m) m = v
        }
        return m / (d.slopeNoise + 1e-6)
    }

    /** Czy między dwoma wierzchołkami jest odcinek izolinii (≥20 ms niskiego zbocza)? */
    private fun quietGapBetween(x: DoubleArray, a: Int, b: Int, fs: Int): Boolean {
        val from = minOf(a, b).coerceIn(0, x.size - 2)
        val to = maxOf(a, b).coerceIn(0, x.size - 2)
        if (to <= from) return false
        var maxD = 0.0
        for (i in from until to) maxD = maxOf(maxD, abs(x[i + 1] - x[i]))
        if (maxD <= 0.0) return false
        val need = msToSamples(20, fs)
        var run = 0
        for (i in from until to) {
            if (abs(x[i + 1] - x[i]) < 0.1 * maxD) {
                run++
                if (run >= need) return true
            } else {
                run = 0
            }
        }
        return false
    }

    private fun nearestPeak(peaks: IntArray, around: Int, tol: Int): Int? {
        var best: Int? = null
        var bestDist = Int.MAX_VALUE
        for (p in peaks) {
            val d = abs(p - around)
            if (d <= tol && d < bestDist) {
                bestDist = d
                best = p
            }
        }
        return best
    }

    // ------------------------------------------------------------------
    // Delineacja zespołu
    // ------------------------------------------------------------------

    private class MorphLead(
        val lead: Lead,
        val signal: DoubleArray,
        /** |pochodna| wygładzona 3 próbkami (mV/próbkę). */
        val slope: DoubleArray,
        val slopeNoise: Double,
        /** Szum HF (druga różnica) — próg końca T / P. */
        val hfNoise: Double,
    )

    private fun morphLead(lead: Lead, signal: DoubleArray): MorphLead {
        val n = signal.size
        val raw = DoubleArray(n)
        for (i in 1 until n - 1) raw[i] = abs(signal[i + 1] - signal[i - 1]) * 0.5
        val slope = DoubleArray(n)
        for (i in 1 until n - 1) slope[i] = (raw[i - 1] + raw[i] + raw[i + 1]) / 3.0
        val hf = DoubleArray((n - 2).coerceAtLeast(0)) { k ->
            val i = k + 1
            abs(signal[i] - 0.5 * (signal[i - 1] + signal[i + 1]))
        }
        return MorphLead(lead, signal, slope, 1.4826 * medianOfArray(slope), 1.4826 * medianOfArray(hf))
    }

    private class Beat(
        val qrsEvent: Int,
        val localRef: Int,
        val aligned: Boolean,
        val qrsOn: Int?,
        val qrsOff: Int?,
        val qAt: Int?,
        val rAt: Int?,
        val sAt: Int?,
        val pAt: Int?,
        val pOn: Int?,
        val tAt: Int?,
        val tOff: Int?,
        val prMs: Int?,
        val prStatus: MeasurementStatus,
        val prReason: String?,
        val qrsMs: Int?,
        val qrsStatus: MeasurementStatus,
        val qrsReason: String?,
        val qtMs: Int?,
        val qtStatus: MeasurementStatus,
        val qtReason: String?,
        val stMv: Double?,
        val stStatus: MeasurementStatus,
        val stReason: String?,
        val morphologyQuality: MorphologyQuality,
        val qualityReason: String?,
    ) {
        val morphologyReady: Boolean
            get() = qrsOn != null && qrsOff != null && qrsOff > qrsOn &&
                morphologyQuality != MorphologyQuality.Unreliable

        val pFound: Boolean get() = pAt != null

        fun toWaveMark() = WaveMark(
            qrsEvent = qrsEvent,
            p = pAt,
            q = qAt,
            r = rAt,
            s = sAt,
            t = tAt,
            qrsOn = qrsOn.takeIf { morphologyReady },
            qrsOff = qrsOff.takeIf { morphologyReady },
            tOff = tOff,
            pOn = pOn,
        )
    }

    private fun unreliableBeat(
        qrsEvent: Int,
        localRef: Int,
        aligned: Boolean,
        reason: String,
        qrsOn: Int? = null,
        qrsOff: Int? = null,
    ) = Beat(
        qrsEvent = qrsEvent,
        localRef = localRef,
        aligned = aligned,
        qrsOn = qrsOn,
        qrsOff = qrsOff,
        qAt = null,
        rAt = null,
        sAt = null,
        pAt = null,
        pOn = null,
        tAt = null,
        tOff = null,
        prMs = null,
        prStatus = MeasurementStatus.NotMeasurable,
        prReason = reason,
        qrsMs = null,
        qrsStatus = MeasurementStatus.NotMeasurable,
        qrsReason = reason,
        qtMs = null,
        qtStatus = MeasurementStatus.NotMeasurable,
        qtReason = reason,
        stMv = null,
        stStatus = MeasurementStatus.NotMeasurable,
        stReason = reason,
        morphologyQuality = MorphologyQuality.Unreliable,
        qualityReason = reason,
    )

    /**
     * Delineacja względem **zdarzenia QRS** z konsensusu (qrsEvent).
     * Lokalne doprecyzowanie tylko w aktywności tego samego zespołu; granice z aktywności
     * zbocza + amplitudy (izolinia ≥ [quietRunLength] próbek, marker na POCZĄTKU izolinii).
     * Nie wymusza kompletnego PQRST — brak P / końca T daje NotMeasurable z powodem.
     */
    private fun measureBeat(
        ml: MorphLead,
        qrsEvent: Int,
        prevQrs: Int?,
        nextQrs: Int?,
        fs: Int,
        rrMs: Int,
        calibration: CalibrationInfo,
    ): Beat {
        val s = ml.signal
        val a = ml.slope
        val n = s.size
        if (n < 16) return unreliableBeat(qrsEvent, qrsEvent, false, "Za krótki sygnał.")
        val ev = qrsEvent.coerceIn(1, n - 2)
        val half = msToSamples(FILTER_COMPLEX_HALF_MS, fs)
        val quietLen = quietRunLength(fs)
        val label = ml.lead.label

        val baseFrom = (ev - msToSamples(300, fs)).coerceAtLeast(0)
        val baseTo = (ev - msToSamples(60, fs)).coerceAtLeast(0)
        val base = if (baseTo > baseFrom) medianRange(s, baseFrom, baseTo) else medianRange(s, (ev - 2 * half).coerceAtLeast(0), ev)
        fun x(i: Int) = s[i] - base

        val wFrom = (ev - half).coerceAtLeast(1)
        val wTo = (ev + half).coerceAtMost(n - 2)
        var maxSlope = 0.0
        var rAmp = 0.0
        for (i in wFrom..wTo) {
            if (a[i] > maxSlope) maxSlope = a[i]
            val v = abs(x(i))
            if (v > rAmp) rAmp = v
        }
        val localRef = refineWithinComplex(s, ev, half, base)
        val activityFloor = maxOf(4.0 * ml.slopeNoise, 1.0 / fs)
        if (maxSlope < activityFloor) {
            return unreliableBeat(qrsEvent, localRef, false, "Brak aktywności QRS w $label w oknie zdarzenia.")
        }
        val slopeThr = maxOf(0.12 * maxSlope, 2.0 * ml.slopeNoise).coerceAtMost(0.25 * maxSlope)
        val ampThr = maxOf(0.10 * rAmp, 0.02)
        val quietOn = { i: Int -> a[i] < slopeThr && abs(x(i)) < ampThr }
        val quietOff = { i: Int ->
            a[i] < slopeThr && (abs(x(i)) < ampThr || (a[i] < 0.5 * slopeThr && abs(x(i)) < 0.5 * rAmp))
        }

        val lo = minOf(localRef, ev)
        val hi = maxOf(localRef, ev)
        val connected = !hasQuietRun(lo, hi, quietLen, quietOn)
        val aligned = abs(localRef - ev) <= half && connected
        if (!connected) {
            return unreliableBeat(
                qrsEvent,
                localRef,
                false,
                "Lokalny wierzchołek $label poza aktywnością zespołu (Δ=${samplesToMs(abs(localRef - ev), fs)} ms).",
            )
        }

        val maxPre = msToSamples((rrMs * 0.35).roundToInt().coerceIn(80, 160), fs)
        val maxPost = msToSamples((rrMs * 0.40).roundToInt().coerceIn(100, 200), fs)
        val onLimit = maxOf(0, ev - maxPre, prevQrs?.let { (it + ev) / 2 } ?: 0)
        val offLimit = minOf(n - 1, ev + maxPost, nextQrs?.let { (it + ev) / 2 } ?: (n - 1))
        val qrsOn = walkQuiet(lo, -1, onLimit, quietLen, quietOn)
        val qrsOff = walkQuiet(hi, 1, offLimit, quietLen, quietOff)
        if (qrsOn == null || qrsOff == null || qrsOff <= qrsOn) {
            val which = if (qrsOn == null) "początku" else "końca"
            return unreliableBeat(
                qrsEvent,
                localRef,
                aligned,
                "Nie wyznaczono $which QRS w $label (brak izolinii ≥ $quietLen próbek).",
                qrsOn,
                qrsOff,
            )
        }

        // Q / R / S wyłącznie w granicach zespołu.
        var rIdx = qrsOn
        var rVal = Double.NEGATIVE_INFINITY
        for (i in qrsOn..qrsOff) {
            val v = x(i)
            if (v > rVal) {
                rVal = v
                rIdx = i
            }
        }
        val rAt = rIdx.takeIf { rVal >= maxOf(0.08 * rAmp, 0.02) }
        val qThr = maxOf(0.012, 0.015 * rAmp)
        val qAt = (if (rAt != null) argMinIn(s, qrsOn, rAt - 1) else argMinIn(s, qrsOn, qrsOff))
            ?.takeIf { x(it) < -qThr && isLocalMin(s, it) }
        val sAt = rAt?.let { argMinIn(s, it + 1, qrsOff) }
            ?.takeIf { x(it) < -qThr && isLocalMin(s, it) }

        val qrsMsVal = samplesToMs(qrsOff - qrsOn, fs)
        var quality = MorphologyQuality.Good
        var qualityReason: String? = null
        if (qrsMsVal !in 50..160) {
            quality = MorphologyQuality.Provisional
            qualityReason = "QRS $qrsMsVal ms w $label — nietypowa szerokość, wynik prowizoryczny."
        }
        val qrsStatus = if (quality == MorphologyQuality.Good) MeasurementStatus.Valid else MeasurementStatus.Provisional
        val isoPr = medianRange(s, (qrsOn - msToSamples(30, fs)).coerceAtLeast(0), (qrsOn - 1).coerceAtLeast(0))

        // P: od końca poprzedniego T do qrsOn; dowolna polarność.
        var pAt: Int? = null
        var pOn: Int? = null
        var prMs: Int? = null
        var prStatus = MeasurementStatus.NotMeasurable
        var prReason: String?
        val pTo = (qrsOn - msToSamples(20, fs)).coerceAtMost(n - 2)
        var pFrom = qrsOn - msToSamples(300, fs)
        if (prevQrs != null) {
            pFrom = maxOf(pFrom, prevQrs + minOf(((ev - prevQrs) * 0.6).roundToInt(), msToSamples(480, fs)))
        }
        pFrom = pFrom.coerceAtLeast(1)
        if (pTo - pFrom < msToSamples(40, fs)) {
            prReason = "Okno P zbyt krótkie (nakładanie z poprzednim T)."
        } else {
            val pBase = 0.5 * (
                medianRange(s, pFrom, minOf(pFrom + msToSamples(40, fs), pTo)) +
                    medianRange(s, pTo, (qrsOn - 1).coerceAtLeast(pTo))
                )
            val pThr = maxOf(0.02, 0.04 * rAmp, 4.0 * ml.hfNoise)
            var posI = -1
            var posV = 0.0
            var negI = -1
            var negV = 0.0
            for (i in pFrom..pTo) {
                val v = s[i] - pBase
                if (s[i] >= s[i - 1] && s[i] >= s[i + 1] && v > posV) {
                    posV = v
                    posI = i
                }
                if (s[i] <= s[i - 1] && s[i] <= s[i + 1] && v < negV) {
                    negV = v
                    negI = i
                }
            }
            val minW = msToSamples(20, fs)
            val maxW = msToSamples(160, fs)
            val posOk = posI >= 0 && posV >= pThr && waveWidthOk(s, pBase, posI, posV, minW, maxW)
            val negOk = negI >= 0 && -negV >= pThr && waveWidthOk(s, pBase, negI, negV, minW, maxW)
            val biphasic = posOk && negOk && abs(posI - negI) <= msToSamples(100, fs)
            val chosen = when {
                posOk && negOk -> if (posV >= -negV) posI else negI
                posOk -> posI
                negOk -> negI
                else -> -1
            }
            if (chosen < 0) {
                prReason = "Nie wykryto załamka P (dodatni / ujemny / dwufazowy)."
            } else {
                pAt = chosen
                val first = if (biphasic) minOf(posI, negI) else chosen
                val pAmp = if (biphasic) maxOf(posV, -negV) else abs(s[chosen] - pBase)
                val onThr = maxOf(0.25 * pAmp, 2.0 * ml.hfNoise, 0.01)
                val pOnLimit = maxOf(1, pFrom - msToSamples(60, fs), prevQrs?.let { it + msToSamples(200, fs) } ?: 1)
                pOn = if (first > pOnLimit) {
                    walkQuiet(first, -1, pOnLimit, quietLen) { i -> abs(s[i] - pBase) < onThr }
                } else {
                    null
                }
                if (pOn == null) {
                    prReason = "Brak wiarygodnego początku P."
                } else {
                    val pr = samplesToMs(qrsOn - pOn, fs)
                    prMs = pr
                    when {
                        quality == MorphologyQuality.Provisional -> {
                            prStatus = MeasurementStatus.Provisional
                            prReason = qualityReason
                        }
                        pr !in 80..300 -> {
                            prStatus = MeasurementStatus.Provisional
                            prReason = "PQ $pr ms poza typowym zakresem — do weryfikacji."
                        }
                        else -> {
                            prStatus = MeasurementStatus.Valid
                            prReason = null
                        }
                    }
                }
            }
        }

        // T: po qrsOff do ~0,55 RR / przed kolejnym zespołem; dowolna polarność.
        var tAt: Int? = null
        var tOff: Int? = null
        var qtMs: Int? = null
        var qtStatus = MeasurementStatus.NotMeasurable
        var qtReason: String?
        val rrLocal = nextQrs?.let { it - ev } ?: msToSamples(rrMs, fs)
        val tFrom = qrsOff + msToSamples(20, fs)
        var tTo = ev + (0.55 * rrLocal).roundToInt()
        if (nextQrs != null) tTo = minOf(tTo, nextQrs - msToSamples(100, fs))
        tTo = minOf(tTo, n - 2)
        if (tTo - tFrom < msToSamples(40, fs)) {
            qtReason = "Okno T zbyt krótkie (koniec zapisu / kolejny zespół)."
        } else {
            val tThr = maxOf(0.02, 0.04 * rAmp, 4.0 * ml.hfNoise)
            val idealMs = (0.28 * samplesToMs(rrLocal, fs)).roundToInt().coerceIn(160, 320)
            val ideal = ev + msToSamples(idealMs, fs)
            val minW = msToSamples(24, fs)
            val maxW = msToSamples(320, fs)
            var best = -1
            var bestScore = Double.NEGATIVE_INFINITY
            for (i in tFrom..tTo) {
                val v = s[i] - isoPr
                val isExt = (v > 0 && s[i] >= s[i - 1] && s[i] >= s[i + 1]) ||
                    (v < 0 && s[i] <= s[i - 1] && s[i] <= s[i + 1])
                if (!isExt || abs(v) < tThr) continue
                if (!waveWidthOk(s, isoPr, i, v, minW, maxW)) continue
                val score = abs(v) / rAmp.coerceAtLeast(1e-6) - 0.002 * abs(samplesToMs(i - ideal, fs))
                if (score > bestScore) {
                    bestScore = score
                    best = i
                }
            }
            if (best < 0) {
                qtReason = "Brak wyraźnego załamka T."
            } else {
                tAt = best
                val tAmp = abs(s[best] - isoPr)
                var tMaxSlope = 0.0
                for (i in tFrom..minOf(best + msToSamples(150, fs), n - 2)) tMaxSlope = maxOf(tMaxSlope, a[i])
                val endAmp = maxOf(0.15 * tAmp, 3.0 * ml.hfNoise, 0.01)
                val endSlope = maxOf(0.35 * tMaxSlope, 2.0 * ml.slopeNoise)
                val endLimit = minOf(
                    n - 1,
                    best + msToSamples(260, fs),
                    nextQrs?.let { it - msToSamples(60, fs) } ?: (n - 1),
                )
                tOff = if (endLimit > best) {
                    walkQuiet(best, 1, endLimit, quietLen) { i -> abs(s[i] - isoPr) < endAmp && a[i] < endSlope }
                } else {
                    null
                }
                val end = tOff
                if (end == null) {
                    qtReason = "Nie wyznaczono końca T (szum / nakładanie z P)."
                } else {
                    val qt = samplesToMs(end - qrsOn, fs)
                    qtMs = qt
                    when {
                        quality == MorphologyQuality.Provisional -> {
                            qtStatus = MeasurementStatus.Provisional
                            qtReason = qualityReason
                        }
                        rrMs < 400 -> {
                            qtStatus = MeasurementStatus.Provisional
                            qtReason = "RR < 400 ms — QT/QTc prowizoryczne."
                        }
                        qt !in 200..600 -> {
                            qtStatus = MeasurementStatus.Provisional
                            qtReason = "QT $qt ms poza typowym zakresem — do weryfikacji."
                        }
                        else -> {
                            qtStatus = MeasurementStatus.Valid
                            qtReason = null
                        }
                    }
                }
            }
        }

        // ST w J+60/80 względem izolinii PR.
        var stMv: Double? = null
        var stStatus = MeasurementStatus.NotMeasurable
        var stReason: String?
        if (!calibration.amplitudesTrusted) {
            stReason = "Nieznana kalibracja — ST niedostępne."
        } else if (quality != MorphologyQuality.Good) {
            stReason = qualityReason ?: "Morfologia prowizoryczna — ST nieoznaczalne."
        } else {
            val j = qrsOff + msToSamples(if (rrMs < 600) 60 else 80, fs)
            val win = msToSamples(8, fs)
            val ok = j + win < n &&
                (tAt == null || j < tAt - msToSamples(20, fs)) &&
                (nextQrs == null || j < nextQrs - msToSamples(200, fs))
            if (!ok) {
                stReason = "Punkt pomiaru ST nachodzi na T / kolejny zespół."
            } else {
                var sum = 0.0
                var cnt = 0
                for (i in (j - win).coerceAtLeast(qrsOff)..(j + win)) {
                    sum += s[i] - isoPr
                    cnt++
                }
                stMv = ((sum / cnt) * 100.0).roundToInt() / 100.0
                if (rrMs < 500) {
                    stStatus = MeasurementStatus.Provisional
                    stReason = "RR < 500 ms — ST prowizoryczne."
                } else {
                    stStatus = MeasurementStatus.Valid
                    stReason = null
                }
            }
        }

        return Beat(
            qrsEvent = qrsEvent,
            localRef = localRef,
            aligned = aligned,
            qrsOn = qrsOn,
            qrsOff = qrsOff,
            qAt = qAt,
            rAt = rAt,
            sAt = sAt,
            pAt = pAt,
            pOn = pOn,
            tAt = tAt,
            tOff = tOff,
            prMs = prMs,
            prStatus = prStatus,
            prReason = prReason,
            qrsMs = qrsMsVal,
            qrsStatus = qrsStatus,
            qrsReason = qualityReason,
            qtMs = qtMs,
            qtStatus = qtStatus,
            qtReason = qtReason,
            stMv = stMv,
            stStatus = stStatus,
            stReason = stReason,
            morphologyQuality = quality,
            qualityReason = qualityReason,
        )
    }

    private fun aggregateBoundaries(globalIndex: Int, beats: List<Pair<Lead, Beat>>, fs: Int): QrsBoundaryDiag {
        val usable = beats.filter { (_, b) -> b.morphologyReady }
        if (usable.isEmpty()) {
            val why = mostCommon(beats.mapNotNull { it.second.qualityReason })
            return QrsBoundaryDiag(
                globalIndex = globalIndex,
                qrsOn = null,
                qrsOff = null,
                qrsMs = null,
                leadsUsed = emptyList(),
                aggregation = "brak",
                status = MeasurementStatus.NotMeasurable,
                reason = why ?: "Brak odprowadzeń z granicami QRS.",
            )
        }
        val ons = usable.map { it.second.qrsOn!! }.sorted()
        val offs = usable.map { it.second.qrsOff!! }.sorted()
        val on = ons[(ons.size - 1) / 2]
        val off = offs[offs.size / 2]
        val leadsUsed = usable.map { it.first }
        if (off <= on) {
            return QrsBoundaryDiag(
                globalIndex, null, null, null, leadsUsed, "mediana(${usable.size})",
                MeasurementStatus.NotMeasurable, "Niespójne granice między odprowadzeniami.",
            )
        }
        val qrsMs = samplesToMs(off - on, fs)
        val (status, reason) = when {
            usable.size < 2 -> MeasurementStatus.Provisional to "Granice z jednego odprowadzenia."
            qrsMs !in 50..160 -> MeasurementStatus.Provisional to "QRS $qrsMs ms — nietypowa szerokość."
            else -> MeasurementStatus.Valid to null
        }
        val aggregation = if (usable.size == 1) "pojedyncze(${leadsUsed.first().label})" else "mediana(${usable.size})"
        return QrsBoundaryDiag(globalIndex, on, off, qrsMs, leadsUsed, aggregation, status, reason)
    }

    // ------------------------------------------------------------------
    // Agregacja pomiarów
    // ------------------------------------------------------------------

    private data class Agg<T>(
        val value: T?,
        val status: MeasurementStatus,
        val reason: String?,
        val beats: Int,
    ) {
        fun downgraded(why: String): Agg<T> =
            if (status == MeasurementStatus.Valid) copy(status = MeasurementStatus.Provisional, reason = why) else this

        companion object {
            fun <T> none(reason: String): Agg<T> = Agg(null, MeasurementStatus.NotMeasurable, reason, 0)
        }
    }

    /** Mediana z ≥3 poprawnych zespołów; inaczej środkowy użyteczny zespół (Provisional). */
    private fun <T : Comparable<T>> aggregateMeasurement(
        beats: List<Beat>,
        value: (Beat) -> T?,
        status: (Beat) -> MeasurementStatus,
        reason: (Beat) -> String?,
    ): Agg<T> {
        val valid = beats.mapNotNull { b -> if (status(b) == MeasurementStatus.Valid) value(b) else null }
        if (valid.size >= 3) {
            return Agg(valid.sorted()[valid.size / 2], MeasurementStatus.Valid, null, valid.size)
        }
        val usable = beats.filter { status(it) != MeasurementStatus.NotMeasurable && value(it) != null }
        if (usable.isNotEmpty()) {
            val mid = usable[usable.size / 2]
            val why = if (valid.isEmpty()) {
                reason(mid) ?: "Pomiar prowizoryczny."
            } else {
                "Tylko ${valid.size} poprawne zespoły (< 3) — wartość ze środkowego zespołu."
            }
            return Agg(value(mid), MeasurementStatus.Provisional, why, usable.size)
        }
        return Agg(null, MeasurementStatus.NotMeasurable, mostCommon(beats.mapNotNull(reason)) ?: "Brak pomiaru.", 0)
    }

    private fun mostCommon(values: List<String>): String? =
        values.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key

    private fun aggregateMorphologyQuality(qualities: List<MorphologyQuality>): MorphologyQuality {
        if (qualities.isEmpty()) return MorphologyQuality.Unreliable
        val bad = qualities.count { it == MorphologyQuality.Unreliable }
        val prov = qualities.count { it == MorphologyQuality.Provisional }
        return when {
            bad * 2 >= qualities.size -> MorphologyQuality.Unreliable
            bad + prov > qualities.size / 2 -> MorphologyQuality.Provisional
            else -> MorphologyQuality.Good
        }
    }

    private data class AxisResult(
        val value: Int?,
        val status: MeasurementStatus,
        val reason: String?,
    )

    /**
     * Oś z pól QRS w I i aVF (globalne granice), uśrednionych po zespołach gotowych do morfologii
     * o RR bliskim mediany — nie z pojedynczego zespołu o największym |R|.
     */
    private fun computeAxis(
        morph: Map<Lead, DoubleArray>,
        peaks: IntArray,
        boundaries: List<QrsBoundaryDiag>,
        referenceBeats: List<Beat>,
        leadIBeats: List<Beat>?,
        fs: Int,
        rrMedianSamples: Int,
    ): AxisResult {
        val leadI = morph[Lead.I]
            ?: return AxisResult(null, MeasurementStatus.NotMeasurable, "Brak odprowadzenia I.")
        val leadVf = morph[Lead.AVF]
            ?: morph[Lead.II]?.let { ii -> DoubleArray(minOf(ii.size, leadI.size)) { ii[it] - leadI[it] / 2.0 } }
            ?: return AxisResult(null, MeasurementStatus.NotMeasurable, "Brak odprowadzenia aVF.")
        val n = minOf(leadI.size, leadVf.size)
        val rrTol = 0.2 * rrMedianSamples

        fun rrNear(k: Int): Boolean {
            val adjacent = listOfNotNull(
                if (k > 0) peaks[k] - peaks[k - 1] else null,
                if (k < peaks.size - 1) peaks[k + 1] - peaks[k] else null,
            )
            return adjacent.isNotEmpty() && adjacent.all { abs(it - rrMedianSamples) <= rrTol }
        }

        fun areas(k: Int): Pair<Double, Double>? {
            val b = boundaries[k]
            if (b.status == MeasurementStatus.NotMeasurable) return null
            if (!referenceBeats[k].morphologyReady) return null
            if (leadIBeats != null && leadIBeats[k].morphologyQuality == MorphologyQuality.Unreliable) return null
            val on = b.qrsOn ?: return null
            val off = b.qrsOff ?: return null
            if (on < 0 || off >= n || off - on < (fs / 100).coerceAtLeast(2)) return null
            val isoFrom = (on - msToSamples(40, fs)).coerceAtLeast(0)
            val isoTo = (on - 2).coerceAtLeast(isoFrom)
            var isoI = 0.0
            var isoVf = 0.0
            for (i in isoFrom..isoTo) {
                isoI += leadI[i]
                isoVf += leadVf[i]
            }
            val cnt = (isoTo - isoFrom + 1).toDouble()
            isoI /= cnt
            isoVf /= cnt
            var areaI = 0.0
            var areaVf = 0.0
            for (i in on..off) {
                areaI += leadI[i] - isoI
                areaVf += leadVf[i] - isoVf
            }
            val toMvMs = 1000.0 / fs
            areaI *= toMvMs
            areaVf *= toMvMs
            if (abs(areaI) + abs(areaVf) < 1.0) return null
            return areaI to areaVf
        }

        var relaxed = false
        var used = peaks.indices.filter { rrNear(it) }.mapNotNull { areas(it) }
        if (used.isEmpty()) {
            used = peaks.indices.mapNotNull { areas(it) }
            relaxed = true
        }
        if (used.isEmpty()) {
            return AxisResult(null, MeasurementStatus.NotMeasurable, "Brak zespołów z wiarygodnymi polami QRS w I/aVF.")
        }
        val meanI = used.sumOf { it.first } / used.size
        val meanVf = used.sumOf { it.second } / used.size
        if (abs(meanI) < 1e-6 && abs(meanVf) < 1e-6) {
            return AxisResult(null, MeasurementStatus.NotMeasurable, "Pola QRS w I/aVF bliskie zeru.")
        }
        val scale = 2.0 / sqrt(3.0)
        val axis = Math.toDegrees(atan2(meanVf * scale, meanI))
        val spread = used.maxOf { (ai, av) ->
            val beatAxis = Math.toDegrees(atan2(av * scale, ai))
            abs(((beatAxis - axis + 540.0) % 360.0) - 180.0)
        }
        val (status, reason) = when {
            relaxed -> MeasurementStatus.Provisional to "Brak zespołów o RR bliskim mediany — oś prowizoryczna."
            used.size < 3 -> MeasurementStatus.Provisional to "Oś z ${used.size} zespołów (< 3)."
            spread > 30.0 -> MeasurementStatus.Provisional to "Rozrzut osi między zespołami ${spread.roundToInt()}°."
            else -> MeasurementStatus.Valid to null
        }
        return AxisResult(axis.roundToInt(), status, reason)
    }

    /**
     * Wybór reprezentanta: MSE względem mediany kształtu, tylko pełne okna.
     * Zwraca globalne indeksy QRS — wspólne dla wszystkich odprowadzeń UI. <2 → pusta tablica.
     */
    private fun selectRepresentativePeaks(
        signal: DoubleArray,
        candidates: IntArray,
        fs: Int,
        rrMs: Int,
    ): IntArray {
        if (candidates.size < 2) return IntArray(0)
        val pre = msToSamples(minOf(200, (rrMs * 0.35).toInt().coerceAtLeast(80)), fs)
        val post = msToSamples(minOf(450, (rrMs * 0.45).toInt().coerceAtLeast(120)), fs)
        val usable = candidates.filter { it - pre >= 0 && it + post < signal.size }
        if (usable.size < 2) return IntArray(0)
        val len = pre + post + 1
        val beats = usable.map { peak -> DoubleArray(len) { i -> signal[peak - pre + i] } }
        val med = DoubleArray(len) { i ->
            val col = beats.map { it[i] }.sorted()
            col[col.size / 2]
        }
        val errors = beats.map { beat ->
            var e = 0.0
            for (i in 0 until len) {
                val d = beat[i] - med[i]
                e += d * d
            }
            e / len
        }
        val errMed = errors.sorted()[errors.size / 2].coerceAtLeast(1e-6)
        val kept = usable.filterIndexed { idx, _ -> errors[idx] <= errMed * 4.0 }
        return if (kept.size >= 2) kept.toIntArray() else IntArray(0)
    }

    // ------------------------------------------------------------------
    // Narzędzia sygnałowe
    // ------------------------------------------------------------------

    /** Długość odcinka izolinii: ≥8 próbek i ≥20 ms. */
    private fun quietRunLength(fs: Int): Int = maxOf(8, msToSamples(20, fs))

    /**
     * Idź od [start] w kierunku [step] do [limit]; zwróć **początek** pierwszego odcinka
     * ciszy o długości ≥ [len] (pierwsza cicha próbka od strony zespołu). Brak → null.
     */
    private inline fun walkQuiet(start: Int, step: Int, limit: Int, len: Int, quiet: (Int) -> Boolean): Int? {
        if ((step < 0 && start <= limit) || (step > 0 && start >= limit)) return null
        var i = start
        var run = 0
        var first = start
        while (i != limit) {
            i += step
            if (quiet(i)) {
                if (run == 0) first = i
                run++
                if (run >= len) return first
            } else {
                run = 0
            }
        }
        return if (run >= maxOf(3, len / 2)) first else null
    }

    private inline fun hasQuietRun(from: Int, to: Int, len: Int, quiet: (Int) -> Boolean): Boolean {
        var run = 0
        for (i in from..to) {
            if (quiet(i)) {
                run++
                if (run >= len) return true
            } else {
                run = 0
            }
        }
        return false
    }

    /** Szerokość fali w połowie amplitudy w [minW, maxW] — odrzuca szpilki i dryft. */
    private fun waveWidthOk(s: DoubleArray, base: Double, idx: Int, peak: Double, minW: Int, maxW: Int): Boolean {
        val halfLevel = 0.5 * peak
        fun beyond(i: Int) = if (peak > 0) s[i] - base < halfLevel else s[i] - base > halfLevel
        var l = idx
        while (l > 0 && idx - l <= maxW && !beyond(l)) l--
        var r = idx
        while (r < s.lastIndex && r - idx <= maxW && !beyond(r)) r++
        if (idx - l > maxW || r - idx > maxW) return false
        val width = r - l
        return width in minW..maxW
    }

    private fun argMinIn(s: DoubleArray, from: Int, to: Int): Int? {
        val lo = from.coerceAtLeast(0)
        val hi = to.coerceAtMost(s.lastIndex)
        if (hi < lo) return null
        var best = lo
        for (i in lo..hi) if (s[i] < s[best]) best = i
        return best
    }

    private fun isLocalMin(s: DoubleArray, i: Int): Boolean =
        i > 0 && i < s.lastIndex && s[i] <= s[i - 1] && s[i] <= s[i + 1]

    private fun isLocalMax(a: DoubleArray, i: Int, half: Int): Boolean {
        val v = a[i]
        for (j in (i - half).coerceAtLeast(0)..(i + half).coerceAtMost(a.lastIndex)) {
            if (j < i && a[j] >= v) return false
            if (j > i && a[j] > v) return false
        }
        return true
    }

    /** Suma kwadratów pierwszej różnicy w ±[half] — miara „ostrości” zespołu. */
    private fun slopeEnergy(x: DoubleArray, at: Int, half: Int): Double {
        val from = (at - half).coerceAtLeast(0)
        val to = (at + half).coerceAtMost(x.lastIndex)
        var e = 0.0
        for (i in from until to) {
            val d = x[i + 1] - x[i]
            e += d * d
        }
        return e
    }

    private fun peakToPeak(x: DoubleArray, center: Int, half: Int): Double {
        val from = (center - half).coerceAtLeast(0)
        val to = (center + half).coerceAtMost(x.lastIndex)
        if (to < from) return 0.0
        var lo = x[from]
        var hi = x[from]
        for (i in from..to) {
            if (x[i] < lo) lo = x[i]
            if (x[i] > hi) hi = x[i]
        }
        return hi - lo
    }

    /**
     * Doprecyzowanie w obrębie **tego samego** QRS (±halfWidth).
     * Wybór ekstremum o największej |amp − base| (lekka kara za odległość) — nie najbliższego
     * słabego Q/S, które „przechwytywałoby” fiducjał rytmu.
     */
    private fun refineWithinComplex(x: DoubleArray, around: Int, halfWidth: Int, base: Double = 0.0): Int {
        if (x.size < 3) return around.coerceIn(0, (x.size - 1).coerceAtLeast(0))
        val center = around.coerceIn(0, x.lastIndex)
        val from = (center - halfWidth).coerceAtLeast(1)
        val to = (center + halfWidth).coerceAtMost(x.lastIndex - 1)
        if (to <= from) return center
        var bestAbs = -1.0
        for (i in from..to) {
            val v = abs(x[i] - base)
            if (v > bestAbs) bestAbs = v
        }
        if (bestAbs < 0.05) return center
        val floor = (0.35 * bestAbs).coerceAtLeast(0.05)
        var bestExt = -1
        var bestExtScore = Double.NEGATIVE_INFINITY
        for (i in from..to) {
            val mid = x[i]
            val isMax = mid >= x[i - 1] && mid >= x[i + 1]
            val isMin = mid <= x[i - 1] && mid <= x[i + 1]
            if (!isMax && !isMin) continue
            val amp = abs(mid - base)
            if (amp < floor) continue
            val score = amp - 0.015 * abs(i - center)
            if (score > bestExtScore) {
                bestExtScore = score
                bestExt = i
            }
        }
        if (bestExt >= 0) return bestExt
        var best = center
        var bestA = abs(x[center] - base)
        for (i in from..to) {
            val v = abs(x[i] - base)
            if (v > bestA) {
                bestA = v
                best = i
            }
        }
        return best
    }

    /** Usunięcie wolnej składowej (wycentrowana MA) — tor detektora QRS. */
    private fun detrend(signal: DoubleArray, fs: Int, windowMs: Int = 400): DoubleArray {
        val baseline = centeredMovingAverage(signal, msToSamples(windowMs, fs).coerceAtLeast(5))
        return DoubleArray(signal.size) { signal[it] - baseline[it] }
    }

    private fun prefixSums(signal: DoubleArray): DoubleArray {
        val p = DoubleArray(signal.size + 1)
        for (i in signal.indices) p[i + 1] = p[i] + signal[i]
        return p
    }

    private fun centeredMovingAverage(signal: DoubleArray, window: Int): DoubleArray {
        val p = prefixSums(signal)
        val h = window / 2
        return DoubleArray(signal.size) { i ->
            val from = (i - h).coerceAtLeast(0)
            val to = (i + h).coerceAtMost(signal.lastIndex)
            (p[to + 1] - p[from]) / (to - from + 1)
        }
    }

    private fun centeredSum(signal: DoubleArray, window: Int): DoubleArray {
        val p = prefixSums(signal)
        val h = window / 2
        return DoubleArray(signal.size) { i ->
            val from = (i - h).coerceAtLeast(0)
            val to = (i + h).coerceAtMost(signal.lastIndex)
            p[to + 1] - p[from]
        }
    }

    private fun medianRange(s: DoubleArray, from: Int, to: Int): Double {
        if (s.isEmpty()) return 0.0
        val lo = from.coerceIn(0, s.lastIndex)
        val hi = to.coerceIn(lo, s.lastIndex)
        val copy = s.copyOfRange(lo, hi + 1)
        copy.sort()
        return copy[copy.size / 2]
    }

    private fun medianOfArray(values: DoubleArray): Double {
        if (values.isEmpty()) return 0.0
        val copy = values.copyOf()
        copy.sort()
        return copy[copy.size / 2]
    }

    private fun medianD(values: Collection<Double>): Double {
        if (values.isEmpty()) return 0.0
        val sorted = values.sorted()
        return sorted[sorted.size / 2]
    }

    private fun median(values: IntArray): Int {
        val sorted = values.sorted()
        return sorted[sorted.size / 2]
    }

    private fun percentile(values: DoubleArray, p: Double): Double {
        if (values.isEmpty()) return 0.0
        val sorted = values.copyOf()
        sorted.sort()
        val index = ((sorted.size - 1) * p).roundToInt().coerceIn(0, sorted.lastIndex)
        return sorted[index]
    }

    private fun coefficientOfVariation(values: IntArray): Double {
        if (values.isEmpty()) return 0.0
        val mean = values.average()
        if (mean == 0.0) return 0.0
        val variance = values.sumOf { (it - mean) * (it - mean) } / values.size
        return sqrt(variance) / mean
    }

    private fun cbrt(x: Double): Double = kotlin.math.exp(kotlin.math.ln(x.coerceAtLeast(1e-9)) / 3.0)

    private fun msToSamples(ms: Int, fs: Int): Int =
        ((ms / 1000.0) * fs).roundToInt().coerceAtLeast(1)

    private fun samplesToMs(samples: Int, fs: Int) = (samples * 1000.0 / fs).roundToInt()

    private fun pct(ratio: Double): String = "${(ratio * 100).roundToInt()}%"

    private fun f1(v: Double): String = if (v > 999.0) ">999" else "%.1f".format(v)
}
