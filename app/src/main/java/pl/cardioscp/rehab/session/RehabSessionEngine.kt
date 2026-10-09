package pl.cardioscp.rehab.session

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import pl.cardioscp.rehab.ble.BleMeasureController
import pl.cardioscp.rehab.ble.VitalMeasureType
import pl.cardioscp.rehab.ble.VitalReading
import pl.cardioscp.rehab.bluetooth.EhoMiniConnectionState
import pl.cardioscp.rehab.bluetooth.EhoMiniDeviceClient
import pl.cardioscp.rehab.bluetooth.ProtocolSessionController
import pl.cardioscp.rehab.scp.ScpRecording
import pl.cardioscp.rehab.scp.ScpRecordingStore

/**
 * Sesja rehabilitacji:
 * EKG (Init→Offline→SCP) → ciśnienie → [waga] → ankieta → dopuszczenie 10 s →
 * EKG spoczynkowe treningu → ×N (ćwiczenie+puls → EKG szczyt → odpoczynek) → podsumowanie.
 */
class RehabSessionEngine(
    private val scope: CoroutineScope,
    private val deviceClient: EhoMiniDeviceClient,
    private val sessionController: ProtocolSessionController,
    private val recordingStore: ScpRecordingStore,
    val bleMeasure: BleMeasureController,
    private val userIdProvider: () -> String,
    private val onOpenViewer: (ScpRecording) -> Unit,
) {
    private val _state = MutableStateFlow<RehabSessionState?>(null)
    val state: StateFlow<RehabSessionState?> = _state.asStateFlow()

    val suppressAutoViewer: Boolean
        get() = _state.value != null && _state.value?.step != RehabStep.SUMMARY

    private var trainingJob: Job? = null
    private var admissionJob: Job? = null
    private var pulseWatchJob: Job? = null
    @Volatile private var skipCurrentPhase: Boolean = false
    @Volatile private var abortTraining: Boolean = false

    fun start(includeWeight: Boolean = true, plan: TrainingPlan = TrainingPlan()) {
        trainingJob?.cancel()
        admissionJob?.cancel()
        pulseWatchJob?.cancel()
        abortTraining = false
        _state.value = RehabSessionState(
            step = RehabStep.INTRO,
            includeWeight = includeWeight,
            trainingPlan = plan,
        )
    }

    fun cancel() {
        abortTraining = true
        trainingJob?.cancel()
        admissionJob?.cancel()
        pulseWatchJob?.cancel()
        trainingJob = null
        admissionJob = null
        pulseWatchJob = null
        bleMeasure.release()
        sessionController.stop()
        _state.value = null
    }

    fun beginBaselineEcg() {
        val s = _state.value ?: return
        if (deviceClient.connectionState.value !is EhoMiniConnectionState.Connected) {
            update { it.copy(error = "Połącz najpierw EHO-Mini (SPP).") }
            return
        }
        update {
            it.copy(
                step = RehabStep.ECG_BASELINE,
                busy = true,
                error = null,
                statusMessage = "Pozostań nieruchomo — trwa zapis EKG",
            )
        }
        scope.launch {
            val result = acquireEcg(
                label = "EKG kwalifikacyjne (przed sesją)",
                totalSeconds = s.trainingPlan.acquireSec,
            )
            result.fold(
                onSuccess = { rec ->
                    val trend = EcgHrTrendEngine.fromRecording(rec)
                    update {
                        it.copy(
                            busy = false,
                            statusMessage = "Zapisano EKG kwalifikacyjne",
                            ecgEntries = it.ecgEntries + SessionEcgEntry(
                                label = "EKG kwalifikacyjne (przed sesją)",
                                recording = rec,
                                hrStartBpm = trend.startBpm,
                                hrAvgBpm = trend.avgBpm,
                                hrEndBpm = trend.endBpm,
                            ),
                            step = RehabStep.VITALS_BP,
                        )
                    }
                    startBpMeasure()
                },
                onFailure = { e ->
                    update {
                        it.copy(
                            busy = false,
                            error = e.message ?: "Błąd EKG spoczynkowego",
                            statusMessage = null,
                        )
                    }
                },
            )
        }
    }

    fun startBpMeasure() {
        update { it.copy(step = RehabStep.VITALS_BP, statusMessage = "Pomiar ciśnienia BLE…") }
        bleMeasure.startMeasure(
            type = VitalMeasureType.BLOOD_PRESSURE,
            onSaved = { reading, note -> onBpSaved(reading, note) },
            onCancelled = {
                update { it.copy(statusMessage = "Pomiar ciśnienia anulowany — możesz ponowić.") }
            },
        )
    }

    private fun onBpSaved(reading: VitalReading, note: String) {
        update {
            it.copy(
                vitals = it.vitals.copy(bloodPressure = reading, bloodPressureNote = note),
                statusMessage = "Ciśnienie: ${reading.summary}",
            )
        }
        val s = _state.value ?: return
        if (s.includeWeight) {
            startWeightMeasure()
        } else {
            update { it.copy(step = RehabStep.SURVEY) }
        }
    }

    fun startWeightMeasure() {
        update { it.copy(step = RehabStep.VITALS_WEIGHT, statusMessage = "Pomiar wagi BLE…") }
        bleMeasure.startMeasure(
            type = VitalMeasureType.WEIGHT,
            onSaved = { reading, note ->
                update {
                    it.copy(
                        vitals = it.vitals.copy(weight = reading, weightNote = note),
                        statusMessage = "Waga: ${reading.summary}",
                        step = RehabStep.SURVEY,
                    )
                }
            },
            onCancelled = {
                update { it.copy(statusMessage = "Pomiar wagi anulowany — możesz ponowić lub pominąć.") }
            },
        )
    }

    fun skipWeight() {
        update { it.copy(step = RehabStep.SURVEY, statusMessage = "Pominięto wagę") }
    }

    fun answerSurvey(questionId: String, yes: Boolean) {
        update { it.copy(surveyAnswers = it.surveyAnswers + (questionId to yes), error = null) }
    }

    fun submitSurvey() {
        val s = _state.value ?: return
        when (DefaultRehabSurvey.evaluate(s.surveyAnswers)) {
            SurveyOutcome.INCOMPLETE -> update {
                it.copy(error = "Odpowiedz na wszystkie pytania.")
            }
            SurveyOutcome.DISQUALIFIED -> update {
                it.copy(
                    step = RehabStep.SURVEY_DISQUALIFIED,
                    error = null,
                    statusMessage = "Ankieta dyskwalifikuje z treningu — skontaktuj się z opiekunem.",
                )
            }
            SurveyOutcome.PASS -> startAdmissionWait()
        }
    }

    private fun startAdmissionWait() {
        val waitSec = _state.value?.trainingPlan?.admissionWaitSec ?: 10
        update {
            it.copy(
                step = RehabStep.ADMISSION_WAIT,
                error = null,
                admissionRemainingSec = waitSec,
                statusMessage = "Oczekiwanie na dopuszczenie do treningu…",
            )
        }
        admissionJob?.cancel()
        admissionJob = scope.launch {
            for (left in waitSec downTo 1) {
                update {
                    it.copy(
                        admissionRemainingSec = left,
                        statusMessage = "Dopuszczenie za ${left}s…",
                    )
                }
                delay(1_000)
            }
            update { it.copy(admissionRemainingSec = 0, statusMessage = "Dopuszczono — start treningu") }
            startTraining()
        }
    }

    fun finishDisqualified() {
        admissionJob?.cancel()
        update { it.copy(step = RehabStep.SUMMARY, statusMessage = "Sesja zakończona (dyskwalifikacja)") }
    }

    private fun startTraining() {
        val s = _state.value ?: return
        if (deviceClient.connectionState.value !is EhoMiniConnectionState.Connected) {
            update { it.copy(error = "Brak połączenia EHO-Mini — wznów SPP przed treningiem.") }
            return
        }
        val timeline = TrainingPlanner.interval(s.trainingPlan)
        val first = timeline.phases.first()
        abortTraining = false
        update {
            it.copy(
                step = RehabStep.TRAINING,
                admissionRemainingSec = null,
                training = TrainingLiveState(
                    phase = first,
                    phaseElapsedSec = 0,
                    phaseRemainingSec = first.durationSec,
                    message = first.label,
                ),
                error = null,
            )
        }
        trainingJob?.cancel()
        trainingJob = scope.launch { runTraining(timeline) }
    }

    private suspend fun runTraining(timeline: TrainingTimeline) {
        for (phase in timeline.phases) {
            if (abortTraining) break
            skipCurrentPhase = false
            update {
                it.copy(
                    training = TrainingLiveState(
                        phase = phase,
                        phaseElapsedSec = 0,
                        phaseRemainingSec = phase.durationSec,
                        message = phase.label,
                        acquiringEcg = false,
                        measuringPulse = phase.kind == TrainingPhaseKind.EXERCISE ||
                            phase.kind == TrainingPhaseKind.REST,
                        coachVisual = when (phase.kind) {
                            TrainingPhaseKind.EXERCISE -> TrainingCoachVisual.EXERCISE
                            TrainingPhaseKind.REST -> TrainingCoachVisual.REST
                            TrainingPhaseKind.ECG_PEAK -> TrainingCoachVisual.STOP_BEFORE_PEAK_ECG
                            TrainingPhaseKind.ECG_REST_START -> TrainingCoachVisual.HOLD_STILL_ECG
                        },
                    ),
                    statusMessage = phase.label,
                    busy = false,
                )
            }
            when (phase.kind) {
                TrainingPhaseKind.ECG_REST_START -> {
                    runEcgSlot(phase.label, holdStill = true)
                }
                TrainingPhaseKind.ECG_PEAK -> {
                    runPrePeakStopCue()
                    runEcgSlot(phase.label, holdStill = true)
                }
                TrainingPhaseKind.EXERCISE -> {
                    val limit = _state.value?.trainingPlan?.limitForCycle(phase.cycle)
                    runExerciseWithPulse(phase, limit)
                }
                TrainingPhaseKind.REST -> {
                    runRestWithPulse(phase)
                }
            }
        }
        pulseWatchJob?.cancel()
        sessionController.stop()
        update {
            it.copy(
                step = RehabStep.SUMMARY,
                training = null,
                busy = false,
                statusMessage = if (abortTraining) "Trening przerwany" else "Trening zakończony",
            )
        }
    }

    private suspend fun runExerciseWithPulse(
        phase: TrainingPhase,
        limit: CycleHeartRateLimit?,
    ) {
        val bpmSamples = mutableListOf<Int>()
        pulseWatchJob?.cancel()
        pulseWatchJob = scope.launch {
            sessionController.lastPulseBpm.collect { bpm ->
                if (bpm != null && bpm > 0) {
                    synchronized(bpmSamples) { bpmSamples += bpm }
                }
                update {
                    val t = it.training ?: return@update it
                    if (t.phase.index != phase.index) return@update it
                    val cue = HeartRateCoach.evaluate(bpm, limit)
                    it.copy(
                        training = t.copy(
                            pulseBpm = bpm,
                            heartRateLimit = limit,
                            heartRateCue = cue,
                            coachVisual = TrainingCoachVisual.EXERCISE,
                            message = buildExerciseMessage(phase, bpm, limit, cue),
                        ),
                    )
                }
            }
        }
        val pulseJob = scope.launch {
            runCatching {
                sessionController.runPulseFor(phase.durationSec)
            }.onFailure { e ->
                if (e is kotlinx.coroutines.CancellationException) throw e
                update {
                    it.copy(
                        statusMessage = "Puls: ${e.message ?: "błąd"} — kończę fazę",
                    )
                }
            }
        }
        for (elapsed in 0 until phase.durationSec) {
            if (skipCurrentPhase || abortTraining) break
            update {
                val t = it.training ?: return@update it
                val cue = HeartRateCoach.evaluate(t.pulseBpm, limit)
                val bpm = t.pulseBpm
                if (bpm != null && bpm > 0) {
                    synchronized(bpmSamples) { bpmSamples += bpm }
                }
                it.copy(
                    training = t.copy(
                        phaseElapsedSec = elapsed,
                        phaseRemainingSec = (phase.durationSec - elapsed).coerceAtLeast(0),
                        measuringPulse = true,
                        heartRateLimit = limit,
                        heartRateCue = cue,
                        coachVisual = TrainingCoachVisual.EXERCISE,
                        message = buildExerciseMessage(phase, t.pulseBpm, limit, cue),
                    ),
                )
            }
            delay(1_000)
        }
        if (skipCurrentPhase || abortTraining) {
            pulseJob.cancel()
            sessionController.stop()
        }
        runCatching { pulseJob.join() }
        pulseWatchJob?.cancel()
        pulseWatchJob = null
        sessionController.stop()
        val samples = synchronized(bpmSamples) { bpmSamples.toList() }
        val summary = CycleHrSummary.fromSamples(phase.cycle, limit, samples)
        update {
            it.copy(cycleHrSummaries = it.cycleHrSummaries.filterNot { c -> c.cycle == phase.cycle } + summary)
        }
        delay(200)
    }

    private fun buildExerciseMessage(
        phase: TrainingPhase,
        bpm: Int?,
        limit: CycleHeartRateLimit?,
        cue: HeartRateCoachCue,
    ): String {
        val pulsePart = bpm?.takeIf { it > 0 }?.let { "$it bpm" } ?: "oczekiwanie…"
        val zonePart = limit?.let { " · cel ${it.minBpm}–${it.maxBpm}" }.orEmpty()
        val cuePart = HeartRateCoach.screenText(cue)?.let { " · $it" }.orEmpty()
        return "${phase.label} · $pulsePart$zonePart$cuePart"
    }

    /** Odpoczynek z pomiarem tętna (bez limitu / coachingu — tylko liczba w UI). */
    private suspend fun runRestWithPulse(phase: TrainingPhase) {
        pulseWatchJob?.cancel()
        pulseWatchJob = scope.launch {
            sessionController.lastPulseBpm.collect { bpm ->
                update {
                    val t = it.training ?: return@update it
                    if (t.phase.index != phase.index) return@update it
                    val pulsePart = bpm?.takeIf { v -> v > 0 }?.let { "$it bpm" } ?: "oczekiwanie…"
                    it.copy(
                        training = t.copy(
                            pulseBpm = bpm,
                            measuringPulse = true,
                            heartRateLimit = null,
                            heartRateCue = HeartRateCoachCue.WAITING,
                            coachVisual = TrainingCoachVisual.REST,
                            message = "Odpoczynek · $pulsePart",
                        ),
                    )
                }
            }
        }
        val pulseJob = scope.launch {
            runCatching {
                sessionController.runPulseFor(phase.durationSec)
            }.onFailure { e ->
                if (e is kotlinx.coroutines.CancellationException) throw e
                update {
                    it.copy(statusMessage = "Puls: ${e.message ?: "błąd"} — kończę odpoczynek")
                }
            }
        }
        for (elapsed in 0 until phase.durationSec) {
            if (skipCurrentPhase || abortTraining) break
            update {
                val t = it.training ?: return@update it
                val pulsePart = t.pulseBpm?.takeIf { v -> v > 0 }?.let { "$it bpm" } ?: "oczekiwanie…"
                it.copy(
                    training = t.copy(
                        phaseElapsedSec = elapsed + 1,
                        phaseRemainingSec = (phase.durationSec - elapsed - 1).coerceAtLeast(0),
                        measuringPulse = true,
                        acquiringEcg = false,
                        pausedForEvent = false,
                        coachVisual = TrainingCoachVisual.REST,
                        message = "Odpoczynek · $pulsePart",
                    ),
                )
            }
            delay(1_000)
        }
        if (skipCurrentPhase || abortTraining) {
            pulseJob.cancel()
            sessionController.stop()
        }
        runCatching { pulseJob.join() }
        pulseWatchJob?.cancel()
        pulseWatchJob = null
        sessionController.stop()
        delay(200)
    }

    private suspend fun runTimedPhase(phase: TrainingPhase) {
        for (elapsed in 0 until phase.durationSec) {
            if (skipCurrentPhase || abortTraining) break
            update {
                val t = it.training ?: return@update it
                it.copy(
                    training = t.copy(
                        phaseElapsedSec = elapsed + 1,
                        phaseRemainingSec = (phase.durationSec - elapsed - 1).coerceAtLeast(0),
                        measuringPulse = false,
                        acquiringEcg = false,
                        pausedForEvent = false,
                    ),
                )
            }
            delay(1_000)
        }
    }

    /** Komunikat „Przerwij ćwiczenie” tuż przed EKG szczytowym. */
    private suspend fun runPrePeakStopCue() {
        val holdSec = (_state.value?.trainingPlan?.prePeakStopSec ?: 3).coerceIn(1, 10)
        for (elapsed in 0 until holdSec) {
            if (skipCurrentPhase || abortTraining) break
            update {
                val t = it.training ?: return@update it
                it.copy(
                    busy = false,
                    training = t.copy(
                        acquiringEcg = false,
                        measuringPulse = false,
                        coachVisual = TrainingCoachVisual.STOP_BEFORE_PEAK_ECG,
                        message = "Przerwij ćwiczenie",
                        phaseElapsedSec = elapsed + 1,
                        phaseRemainingSec = (holdSec - elapsed - 1).coerceAtLeast(0),
                    ),
                    statusMessage = "Przerwij ćwiczenie — zaraz zapis EKG",
                )
            }
            delay(1_000)
        }
    }

    private suspend fun runEcgSlot(label: String, holdStill: Boolean) {
        update {
            val t = it.training
            it.copy(
                busy = true,
                training = t?.copy(
                    acquiringEcg = true,
                    measuringPulse = false,
                    coachVisual = if (holdStill) {
                        TrainingCoachVisual.HOLD_STILL_ECG
                    } else {
                        t.coachVisual
                    },
                    message = "Pozostań nieruchomo — trwa zapis EKG",
                ),
                statusMessage = "Pozostań nieruchomo — trwa zapis EKG",
            )
        }
        val plan = _state.value?.trainingPlan ?: TrainingPlan()
        val result = acquireEcg(label = label, totalSeconds = plan.acquireSec)
        result.fold(
            onSuccess = { rec ->
                val trend = EcgHrTrendEngine.fromRecording(rec)
                update {
                    val t = it.training
                    it.copy(
                        busy = false,
                        ecgEntries = it.ecgEntries + SessionEcgEntry(
                            label = label,
                            recording = rec,
                            hrStartBpm = trend.startBpm,
                            hrAvgBpm = trend.avgBpm,
                            hrEndBpm = trend.endBpm,
                        ),
                        training = t?.copy(acquiringEcg = false),
                        statusMessage = "Zapisano $label",
                        error = null,
                    )
                }
            },
            onFailure = { e ->
                update {
                    val t = it.training
                    it.copy(
                        busy = false,
                        training = t?.copy(
                            acquiringEcg = false,
                            message = "Błąd EKG: ${e.message}",
                        ),
                        error = e.message,
                    )
                }
            },
        )
    }

    /** Init → Offline → Done → End → Init → GetScp → zapis. */
    private suspend fun acquireEcg(label: String, totalSeconds: Int): Result<ScpRecording> {
        return runCatching {
            if (deviceClient.connectionState.value !is EhoMiniConnectionState.Connected) {
                error("Brak połączenia SPP z EHO-Mini")
            }
            val userId = userIdProvider()
            update { it.copy(statusMessage = "$label: Init + EKG Offline (${totalSeconds}s)…") }
            sessionController.runEcgOfflineCreate(userId, totalSeconds = totalSeconds)
            update { it.copy(statusMessage = "$label: pobieranie SCP…") }
            val bytes = sessionController.runScpDownload()
            val serial = (deviceClient.connectionState.value as? EhoMiniConnectionState.Connected)
                ?.deviceName?.takeLast(6)
            recordingStore.saveComplete(bytes, serial)
        }.recoverCatching { e ->
            throw IllegalStateException("$label: ${e.message}", e)
        }
    }

    fun openSessionEcg(entry: SessionEcgEntry) {
        onOpenViewer(entry.recording)
    }

    fun reportEcgEvent() {
        val s = _state.value ?: return
        val t = s.training ?: return
        when (t.phase.kind) {
            TrainingPhaseKind.EXERCISE -> {
                update {
                    it.copy(
                        training = t.copy(
                            pausedForEvent = true,
                            message = "Komentarz pacjenta — przejść do EKG szczytowego / odpoczynku?",
                        ),
                    )
                }
            }
            TrainingPhaseKind.REST -> {
                update {
                    it.copy(
                        training = t.copy(
                            pausedForEvent = true,
                            message = "Komentarz pacjenta — zakończyć trening?",
                        ),
                    )
                }
            }
            TrainingPhaseKind.ECG_REST_START, TrainingPhaseKind.ECG_PEAK -> Unit
        }
    }

    fun confirmEventAction(endTraining: Boolean) {
        val s = _state.value ?: return
        val t = s.training ?: return
        if (!t.pausedForEvent) return
        if (endTraining || t.phase.kind != TrainingPhaseKind.EXERCISE) {
            abortTraining = true
            skipCurrentPhase = true
            trainingJob?.cancel()
            pulseWatchJob?.cancel()
            sessionController.stop()
            update {
                it.copy(
                    step = RehabStep.SUMMARY,
                    training = null,
                    statusMessage = "Trening przerwany (zdarzenie EKG)",
                )
            }
        } else {
            skipCurrentPhase = true
            update {
                it.copy(
                    training = t.copy(
                        pausedForEvent = false,
                        phaseRemainingSec = 0,
                        message = "Kończę wysiłek…",
                    ),
                )
            }
        }
    }

    fun dismissEventPause() {
        update {
            val t = it.training ?: return@update it
            it.copy(training = t.copy(pausedForEvent = false))
        }
    }

    private fun update(block: (RehabSessionState) -> RehabSessionState) {
        val cur = _state.value ?: return
        _state.value = block(cur)
    }
}
