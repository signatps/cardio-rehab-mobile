package pl.cardioscp.rehab.session

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import pl.cardioscp.rehab.ble.BleMeasureController
import pl.cardioscp.rehab.ble.VitalMeasureType
import pl.cardioscp.rehab.ble.VitalReading
import pl.cardioscp.rehab.bluetooth.EhoMiniConnectionState
import pl.cardioscp.rehab.bluetooth.EhoMiniDeviceClient
import pl.cardioscp.rehab.bluetooth.ProtocolSessionController
import pl.cardioscp.rehab.scp.ScpRecording
import pl.cardioscp.rehab.scp.ScpRecordingStore

/**
 * Orkiestracja sesji rehabilitacji:
 * EKG → ciśnienie → [waga HF] → ankieta → trening sekwencyjny → podsumowanie.
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

    /** Gdy true, HomeViewModel nie otwiera automatycznie przeglądarki po SCP. */
    val suppressAutoViewer: Boolean
        get() = _state.value != null && _state.value?.step != RehabStep.SUMMARY

    private var trainingJob: Job? = null
    @Volatile private var skipCurrentPhase: Boolean = false

    fun start(includeWeight: Boolean = true, plan: TrainingPlan = TrainingPlan()) {
        trainingJob?.cancel()
        _state.value = RehabSessionState(
            step = RehabStep.INTRO,
            includeWeight = includeWeight,
            trainingPlan = plan,
        )
    }

    fun cancel() {
        trainingJob?.cancel()
        trainingJob = null
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
        update { it.copy(step = RehabStep.ECG_BASELINE, busy = true, error = null, statusMessage = "Akwizycja EKG spoczynkowego…") }
        scope.launch {
            val result = acquireEcg(
                label = "EKG spoczynkowe",
                totalSeconds = s.trainingPlan.acquireSec,
            )
            result.fold(
                onSuccess = { rec ->
                    update {
                        it.copy(
                            busy = false,
                            statusMessage = "Zapisano ${rec.displayName}",
                            ecgEntries = it.ecgEntries + SessionEcgEntry("EKG spoczynkowe", rec),
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
        update {
            it.copy(surveyAnswers = it.surveyAnswers + (questionId to yes))
        }
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
            SurveyOutcome.PASS -> {
                update { it.copy(error = null, statusMessage = "Ankieta OK — start treningu") }
                startTraining()
            }
        }
    }

    fun finishDisqualified() {
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
        update {
            it.copy(
                step = RehabStep.TRAINING,
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
        trainingJob = scope.launch { runTraining(timeline, s.trainingPlan) }
    }

    private suspend fun runTraining(timeline: TrainingTimeline, plan: TrainingPlan) {
        for (phase in timeline.phases) {
            skipCurrentPhase = false
            update {
                it.copy(
                    training = TrainingLiveState(
                        phase = phase,
                        phaseElapsedSec = 0,
                        phaseRemainingSec = phase.durationSec,
                        message = phase.label,
                    ),
                )
            }
            if (phase.ecgAtStart && phase.ecgLabel != null) {
                runEcgSlot(phase.ecgLabel)
            }
            for (elapsed in 0 until phase.durationSec) {
                if (skipCurrentPhase) break
                delay(1_000)
                update {
                    val t = it.training ?: return@update it
                    it.copy(
                        training = t.copy(
                            phaseElapsedSec = elapsed + 1,
                            phaseRemainingSec = (phase.durationSec - elapsed - 1).coerceAtLeast(0),
                            pausedForEvent = false,
                        ),
                    )
                }
            }
            if (TrainingPlanner.needsEndOfLastExerciseEcg(phase, plan)) {
                runEcgSlot("EKG przed koniec treningu")
            }
        }
        sessionController.stop()
        update {
            it.copy(
                step = RehabStep.SUMMARY,
                training = null,
                busy = false,
                statusMessage = "Trening zakończony",
            )
        }
    }

    private suspend fun runEcgSlot(label: String) {
        update {
            val t = it.training
            it.copy(
                busy = true,
                training = t?.copy(acquiringEcg = true, acquireLabel = label, message = label),
                statusMessage = label,
            )
        }
        val plan = _state.value?.trainingPlan ?: TrainingPlan()
        val result = acquireEcg(label = label, totalSeconds = plan.acquireSec)
        result.fold(
            onSuccess = { rec ->
                update {
                    val t = it.training
                    it.copy(
                        busy = false,
                        ecgEntries = it.ecgEntries + SessionEcgEntry(label, rec),
                        training = t?.copy(acquiringEcg = false, acquireLabel = null),
                        statusMessage = "Zapisano $label",
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
                            acquireLabel = null,
                            message = "Błąd EKG: ${e.message}",
                        ),
                        error = e.message,
                    )
                }
            },
        )
    }

    /**
     * ECG Offline (totalSeconds) → GetScp → zapis lokalny (bez auto-viewer).
     */
    private suspend fun acquireEcg(label: String, totalSeconds: Int): Result<ScpRecording> {
        return runCatching {
            if (deviceClient.connectionState.value !is EhoMiniConnectionState.Connected) {
                error("Brak połączenia SPP z EHO-Mini")
            }
            val userId = userIdProvider()
            sessionController.startEcgOfflineCreateScenario(userId, totalSeconds = totalSeconds)
            withTimeout((totalSeconds + 45L) * 1_000) {
                sessionController.status.first {
                    it is ProtocolSessionController.Status.Finished ||
                        it is ProtocolSessionController.Status.Failed
                }
            }.let { st ->
                if (st is ProtocolSessionController.Status.Failed) {
                    error(st.reason)
                }
            }
            delay(300)
            sessionController.startScpDownload()
            val bytes = withTimeout(120_000) {
                sessionController.lastScpBytes.first { it != null && it.isNotEmpty() }!!
            }
            // Wait download finished
            withTimeout(60_000) {
                sessionController.status.first {
                    it is ProtocolSessionController.Status.Finished ||
                        it is ProtocolSessionController.Status.Failed
                }
            }.let { st ->
                if (st is ProtocolSessionController.Status.Failed) {
                    error(st.reason)
                }
            }
            val serial = (deviceClient.connectionState.value as? EhoMiniConnectionState.Connected)
                ?.deviceName?.takeLast(6)
            recordingStore.saveComplete(bytes, serial).also {
                // Prefer readable label in session list; file keeps timestamp name.
            }
        }.recoverCatching { e ->
            throw IllegalStateException("$label: ${e.message}", e)
        }
    }

    fun openSessionEcg(entry: SessionEcgEntry) {
        onOpenViewer(entry.recording)
    }

    fun reportEcgEvent() {
        // PDF: event w wysiłku → odpoczynek; w odpoczynku → koniec po potwierdzeniu.
        val s = _state.value ?: return
        val t = s.training ?: return
        when (t.phase.kind) {
            TrainingPhaseKind.EXERCISE -> {
                update {
                    it.copy(
                        training = t.copy(
                            pausedForEvent = true,
                            message = "Zdarzenie EKG — przejdź do odpoczynku (potwierdź).",
                        ),
                    )
                }
            }
            TrainingPhaseKind.REST, TrainingPhaseKind.POST_TRAINING -> {
                update {
                    it.copy(
                        training = t.copy(
                            pausedForEvent = true,
                            message = "Zdarzenie EKG — zakończyć trening?",
                        ),
                    )
                }
            }
        }
    }

    fun confirmEventAction(endTraining: Boolean) {
        val s = _state.value ?: return
        val t = s.training ?: return
        if (!t.pausedForEvent) return
        if (endTraining || t.phase.kind != TrainingPhaseKind.EXERCISE) {
            trainingJob?.cancel()
            sessionController.stop()
            update {
                it.copy(
                    step = RehabStep.SUMMARY,
                    training = null,
                    statusMessage = "Trening przerwany (zdarzenie EKG)",
                )
            }
        } else {
            // PDF: zdarzenie w wysiłku → przejdź do odpoczynku.
            skipCurrentPhase = true
            update {
                it.copy(
                    training = t.copy(
                        pausedForEvent = false,
                        phaseRemainingSec = 0,
                        message = "Przejście do odpoczynku…",
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
