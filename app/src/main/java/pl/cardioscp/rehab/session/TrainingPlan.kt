package pl.cardioscp.rehab.session

/**
 * Trening sekwencyjny zgodnie z wymaganiami sesji:
 * EKG spoczynkowe na start → ×N (ćwiczenie + puls → EKG szczyt → odpoczynek).
 */
data class TrainingPlan(
    val cycles: Int = 2,
    /** Czas wysiłku [s]. */
    val exerciseSec: Int = 15,
    /** Czas odpoczynku [s] — startuje po zakończeniu EKG szczytowego. */
    val restSec: Int = 15,
    /** Czas akwizycji EKG Offline [s]. */
    val acquireSec: Int = 5,
    /** Automatyczne dopuszczenie po ankiecie [s]. */
    val admissionWaitSec: Int = 10,
    /** Limity tętna per cykl (min–max). Brak wpisu = brak coachingu w cyklu. */
    val heartRateLimits: List<CycleHeartRateLimit> = HeartRateCoach.defaultLimits(2),
    /**
     * Rodzaj ćwiczenia (docelowo z platformy).
     * Domyślnie Nordic walking.
     */
    val exerciseKind: ExerciseKind = ExerciseKind.NORDIC_WALKING,
    /** Krótka pauza przed EKG szczytowym z komunikatem „Przerwij ćwiczenie” [s]. */
    val prePeakStopSec: Int = 3,
) {
    init {
        require(cycles >= 1)
        require(exerciseSec >= 5)
        require(restSec >= 5)
        require(acquireSec >= 2)
        require(admissionWaitSec >= 0)
        heartRateLimits.forEach { lim ->
            require(lim.cycle in 1..cycles) { "limit cyklu ${lim.cycle} poza 1..$cycles" }
        }
    }

    fun limitForCycle(cycle: Int): CycleHeartRateLimit? =
        heartRateLimits.firstOrNull { it.cycle == cycle }
}

enum class TrainingPhaseKind {
    /** EKG spoczynkowe na początku treningu. */
    ECG_REST_START,
    /** Wysiłek z pomiarem tętna z EHO-Mini. */
    EXERCISE,
    /** EKG w szczycie wysiłku (po minucie ćwiczenia). */
    ECG_PEAK,
    /** Odpoczynek po EKG szczytowym. */
    REST,
}

data class TrainingPhase(
    val index: Int,
    val kind: TrainingPhaseKind,
    val cycle: Int,
    val durationSec: Int,
    val label: String,
)

data class TrainingTimeline(
    val phases: List<TrainingPhase>,
)

object TrainingPlanner {
    /**
     * EKG spoczynkowe → (Wysiłek → EKG szczyt → Odpoczynek) × cycles
     */
    fun interval(plan: TrainingPlan): TrainingTimeline {
        val phases = mutableListOf<TrainingPhase>()
        var idx = 0
        phases += TrainingPhase(
            index = idx++,
            kind = TrainingPhaseKind.ECG_REST_START,
            cycle = 0,
            durationSec = plan.acquireSec,
            label = "EKG spoczynkowe (start treningu)",
        )
        for (c in 1..plan.cycles) {
            phases += TrainingPhase(
                index = idx++,
                kind = TrainingPhaseKind.EXERCISE,
                cycle = c,
                durationSec = plan.exerciseSec,
                label = "Wysiłek $c/${plan.cycles}",
            )
            phases += TrainingPhase(
                index = idx++,
                kind = TrainingPhaseKind.ECG_PEAK,
                cycle = c,
                durationSec = plan.acquireSec,
                label = "EKG szczyt wysiłku $c/${plan.cycles}",
            )
            phases += TrainingPhase(
                index = idx++,
                kind = TrainingPhaseKind.REST,
                cycle = c,
                durationSec = plan.restSec,
                label = "Odpoczynek $c/${plan.cycles}",
            )
        }
        return TrainingTimeline(phases = phases)
    }
}
