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
 * EKG kwalifikacyjne → ciśnienie → [waga] → ankieta → dopuszczenie →
 * trening (profil interwałowy: spoczynek / wysiłek / szczyt / odpoczynek) → podsumowanie.
 * Offline: każde EKG = Offline+GetScp. Online: ciągły strumień, fragmenty z taśmy.
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
    private var onlineLiveJob: Job? = null
    @Volatile private var skipCurrentPhase: Boolean = false
    @Volatile private var abortTraining: Boolean = false

    fun start(
        includeWeight: Boolean = true,
        plan: TrainingPlan = TrainingPlan(),
        ecgMode: EcgAcquisitionMode = EcgAcquisitionMode.OFFLINE,
    ) {
        trainingJob?.cancel()
        admissionJob?.cancel()
        pulseWatchJob?.cancel()
        abortTraining = false
        _state.value = RehabSessionState(
            step = RehabStep.INTRO,
            includeWeight = includeWeight,
            ecgMode = ecgMode,
            trainingPlan = plan,
        )
    }

    fun cancel() {
        val mode = _state.value?.ecgMode
        abortTraining = true
        trainingJob?.cancel()
        admissionJob?.cancel()
        pulseWatchJob?.cancel()
        onlineLiveJob?.cancel()
        trainingJob = null
        admissionJob = null
        pulseWatchJob = null
        onlineLiveJob = null
        bleMeasure.release()
        scope.launch {
            if (mode == EcgAcquisitionMode.ONLINE) {
                runCatching { sessionController.stopOnlineSession() }
            } else {
                sessionController.stop()
            }
        }
        _state.value = null
    }

    fun setGateError(message: String) {
        update {
            it.copy(
                step = RehabStep.INTRO,
                busy = false,
                error = message,
                statusMessage = null,
            )
        }
    }

    fun beginBaselineEcg() {
        val s = _state.value ?: return
        if (deviceClient.connectionState.value !is EhoMiniConnectionState.Connected) {
            update { it.copy(error = "Połącz najpierw EHO-Mini (SPP).") }
            return
        }
        scope.launch {
            // 1) STOP + Get(electrodes) do urządzenia, potem dopełnij do min. 2 s
            update {
                it.copy(
                    step = RehabStep.ECG_BASELINE,
                    baselineEcgPhase = BaselineEcgPhase.ELECTRODE_CHECK,
                    busy = true,
                    error = null,
                    statusMessage = "Sprawdzanie elektrod — pozostań nieruchomo",
                )
            }
            val checkStartedAt = System.currentTimeMillis()
            val afterCheck = runCatching {
                sessionController.refreshElectrodes(timeoutMs = 3_500L)
            }.getOrDefault(sessionController.electrodeStatus.value)
            val elapsed = System.currentTimeMillis() - checkStartedAt
            delay((2_000L - elapsed).coerceAtLeast(0L))
            if (!afterCheck.known) {
                update {
                    it.copy(
                        step = RehabStep.INTRO,
                        baselineEcgPhase = null,
                        busy = false,
                        error = "Brak odpowiedzi urządzenia na sprawdzenie elektrod (Get 0x02).",
                        statusMessage = null,
                    )
                }
                return@launch
            }
            if (!afterCheck.allAttached) {
                update {
                    it.copy(
                        step = RehabStep.INTRO,
                        baselineEcgPhase = null,
                        busy = false,
                        error = "Nie można rozpocząć EKG — ${afterCheck.summaryPl}",
                        statusMessage = null,
                    )
                }
                return@launch
            }
            // 2) Ludzik pomiaru EKG + zapis
            update {
                it.copy(
                    baselineEcgPhase = BaselineEcgPhase.ACQUIRING,
                    busy = true,
                    error = null,
                    statusMessage = "Pozostań nieruchomo — trwa zapis EKG (${s.trainingPlan.acquireSec}s)",
                    ecgAcquireRemainingSec = s.trainingPlan.acquireSec,
                )
            }
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
                            baselineEcgPhase = null,
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
                            baselineEcgPhase = null,
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
            // Krótka przerwa — GATT ciśnienia musi się domknąć zanim skan wagi (Samsung dual-mode).
            scope.launch {
                delay(600)
                val cur = _state.value ?: return@launch
                if (cur.includeWeight && cur.vitals.weight == null &&
                    cur.step != RehabStep.SURVEY &&
                    cur.step != RehabStep.SUMMARY
                ) {
                    startWeightMeasure()
                }
            }
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
            SurveyOutcome.PASS -> showReadyToStartTraining()
        }
    }

    /** Po kwalifikacji + pomiarach + ankiecie — decyzja użytkownika o starcie treningu. */
    private fun showReadyToStartTraining() {
        admissionJob?.cancel()
        update {
            it.copy(
                step = RehabStep.ADMISSION_WAIT,
                error = null,
                admissionRemainingSec = null,
                statusMessage = "Kwalifikacja zakończona — naciśnij Rozpocznij sesję, gdy będziesz gotowy.",
            )
        }
    }

    fun confirmStartTraining() {
        val s = _state.value ?: return
        if (s.step != RehabStep.ADMISSION_WAIT) return
        startTraining()
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
        val timeline = TrainingPlanner.timeline(s.trainingPlan)
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
                    // Nie dubluj phase.label w statusMessage — widać go w panelu treningu.
                    statusMessage = null,
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
        finishProtocolAfterTraining()
        update {
            it.copy(
                step = RehabStep.BORG,
                training = null,
                busy = false,
                liveEcg = null,
                statusMessage = if (abortTraining) {
                    "Trening przerwany — oceń odczuwany wysiłek"
                } else {
                    "Trening zakończony — oceń odczuwany wysiłek (Borg)"
                },
            )
        }
    }

    fun selectBorgScore(score: Int) {
        if (!BorgScale.isValid(score)) return
        update { it.copy(borgScore = score, error = null) }
    }

    fun submitBorg() {
        val s = _state.value ?: return
        if (s.borgScore == null || !BorgScale.isValid(s.borgScore)) {
            update { it.copy(error = "Wybierz wartość skali Borga (6–20)") }
            return
        }
        update {
            it.copy(
                step = RehabStep.SUMMARY,
                busy = false,
                error = null,
                statusMessage = "Borg ${BorgScale.summaryPl(it.borgScore)}",
            )
        }
    }

    private suspend fun finishProtocolAfterTraining() {
        val mode = _state.value?.ecgMode ?: EcgAcquisitionMode.OFFLINE
        if (mode == EcgAcquisitionMode.ONLINE) {
            runCatching { sessionController.stopOnlineSession() }
            onlineLiveJob?.cancel()
            onlineLiveJob = null
        } else {
            sessionController.stop()
        }
    }

    private suspend fun runExerciseWithPulse(
        phase: TrainingPhase,
        limit: CycleHeartRateLimit?,
    ) {
        val bpmSamples = mutableListOf<Int>()
        // Online: tętno z R-R na przebiegu EKG (nie GetPulse). Offline: brak strumienia → brak BPM.
        ensureHrSourceForTraining()
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
        pulseWatchJob?.cancel()
        pulseWatchJob = null
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
        ensureHrSourceForTraining()
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
        pulseWatchJob?.cancel()
        pulseWatchJob = null
        delay(200)
    }

    /**
     * Tętno w treningu wyłącznie z R-R na strumieniu Online (nasze algorytmy).
     * GetPulse nie jest używany.
     */
    private suspend fun ensureHrSourceForTraining() {
        if ((_state.value?.ecgMode ?: EcgAcquisitionMode.OFFLINE) == EcgAcquisitionMode.ONLINE) {
            ensureOnlineSessionRunning()
        }
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
        update {
            val t = it.training
            it.copy(
                training = t?.copy(
                    phaseElapsedSec = 0,
                    phaseRemainingSec = plan.acquireSec,
                ),
            )
        }
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

    /**
     * Offline: Init → Offline → Done → End → GetScp → zapis.
     * Online: upewnij się, że sesja Online trwa; poczekaj [totalSeconds];
     * wytnij fragment z taśmy → SCP w aplikacji (bez Offline / GetScp z urządzenia).
     */
    private suspend fun acquireEcg(label: String, totalSeconds: Int): Result<ScpRecording> {
        return runCatching {
            if (deviceClient.connectionState.value !is EhoMiniConnectionState.Connected) {
                error("Brak połączenia SPP z EHO-Mini")
            }
            val electrodes = runCatching { sessionController.refreshElectrodes() }
                .getOrDefault(sessionController.electrodeStatus.value)
            if (!electrodes.allAttached) {
                error(electrodes.summaryPl)
            }
            val userId = userIdProvider()
            val mode = _state.value?.ecgMode ?: EcgAcquisitionMode.OFFLINE
            val serial = (deviceClient.connectionState.value as? EhoMiniConnectionState.Connected)
                ?.deviceName?.takeLast(6)
            val tickJob = scope.launch { tickEcgAcquireCountdown(totalSeconds) }
            try {
                when (mode) {
                    EcgAcquisitionMode.ONLINE -> {
                        ensureOnlineSessionRunning()
                        update {
                            it.copy(
                                statusMessage = "$label: zapis Online ${totalSeconds}s…",
                                liveEcg = sessionController.liveEcg.value,
                                ecgAcquireRemainingSec = totalSeconds,
                            )
                        }
                        val bytes = sessionController.captureOnlineFragment(totalSeconds)
                        recordingStore.saveComplete(bytes, serial)
                    }
                    EcgAcquisitionMode.OFFLINE -> {
                        update {
                            it.copy(
                                statusMessage = "$label: zapis Offline ${totalSeconds}s…",
                                liveEcg = null,
                                ecgAcquireRemainingSec = totalSeconds,
                            )
                        }
                        sessionController.runEcgOfflineCreate(userId, totalSeconds = totalSeconds)
                        update { it.copy(statusMessage = "$label: pobieranie SCP…") }
                        val bytes = sessionController.runScpDownload()
                        recordingStore.saveComplete(bytes, serial)
                    }
                }
            } finally {
                tickJob.cancel()
                update { it.copy(ecgAcquireRemainingSec = null) }
            }
        }.recoverCatching { e ->
            throw IllegalStateException("$label: ${e.message}", e)
        }
    }

    private suspend fun tickEcgAcquireCountdown(totalSeconds: Int) {
        val total = totalSeconds.coerceAtLeast(1)
        update {
            val t = it.training
            it.copy(
                ecgAcquireRemainingSec = total,
                training = t?.copy(
                    acquiringEcg = true,
                    phaseElapsedSec = 0,
                    phaseRemainingSec = total,
                ),
            )
        }
        for (elapsed in 0 until total) {
            if (abortTraining) break
            delay(1_000)
            val remain = (total - elapsed - 1).coerceAtLeast(0)
            update {
                val t = it.training
                it.copy(
                    ecgAcquireRemainingSec = remain,
                    training = t?.copy(
                        acquiringEcg = true,
                        phaseElapsedSec = elapsed + 1,
                        phaseRemainingSec = remain,
                    ),
                )
            }
        }
    }

    private suspend fun ensureOnlineSessionRunning() {
        if (!sessionController.onlineSessionActive) {
            sessionController.startOnlineSession()
        }
        if (onlineLiveJob?.isActive != true) {
            onlineLiveJob?.cancel()
            onlineLiveJob = scope.launch {
                sessionController.liveEcg.collect { snap ->
                    update { it.copy(liveEcg = snap) }
                }
            }
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
            scope.launch { finishProtocolAfterTraining() }
            update {
                it.copy(
                    step = RehabStep.BORG,
                    training = null,
                    liveEcg = null,
                    statusMessage = "Trening przerwany — oceń odczuwany wysiłek",
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
