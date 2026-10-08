package pl.cardioscp.rehab.session

import pl.cardioscp.rehab.ble.VitalReading
import pl.cardioscp.rehab.scp.ScpRecording

enum class RehabStep {
    INTRO,
    ECG_BASELINE,
    VITALS_BP,
    VITALS_WEIGHT,
    SURVEY,
    SURVEY_DISQUALIFIED,
    ADMISSION_WAIT,
    TRAINING,
    SUMMARY,
}

data class SessionEcgEntry(
    val label: String,
    val recording: ScpRecording,
    val capturedAtMs: Long = System.currentTimeMillis(),
)

data class SessionVitals(
    val bloodPressure: VitalReading? = null,
    val bloodPressureNote: String = "",
    val weight: VitalReading? = null,
    val weightNote: String = "",
)

data class TrainingLiveState(
    val phase: TrainingPhase,
    val phaseElapsedSec: Int,
    val phaseRemainingSec: Int,
    val acquiringEcg: Boolean = false,
    val measuringPulse: Boolean = false,
    val pulseBpm: Int? = null,
    val message: String = "",
    val pausedForEvent: Boolean = false,
    /** Aktualny limit tętna cyklu (tylko w fazie wysiłku). */
    val heartRateLimit: CycleHeartRateLimit? = null,
    /** Cue coachingu tętna — steruje migającym napisem i TTS. */
    val heartRateCue: HeartRateCoachCue = HeartRateCoachCue.WAITING,
)

data class RehabSessionState(
    val step: RehabStep = RehabStep.INTRO,
    val includeWeight: Boolean = true,
    val trainingPlan: TrainingPlan = TrainingPlan(),
    val vitals: SessionVitals = SessionVitals(),
    val surveyAnswers: Map<String, Boolean> = emptyMap(),
    val ecgEntries: List<SessionEcgEntry> = emptyList(),
    val training: TrainingLiveState? = null,
    /** Podsumowanie tętna per cykl (po zakończeniu wysiłków). */
    val cycleHrSummaries: List<CycleHrSummary> = emptyList(),
    val admissionRemainingSec: Int? = null,
    val busy: Boolean = false,
    val statusMessage: String? = null,
    val error: String? = null,
    val startedAtMs: Long = System.currentTimeMillis(),
)
