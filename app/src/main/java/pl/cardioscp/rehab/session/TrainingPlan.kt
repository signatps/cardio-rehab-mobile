package pl.cardioscp.rehab.session

/**
 * Trening sekwencyjny interwałowy (PDF):
 * Wysiłek×N ↔ Odpoczynek×(N-1) + 2 min po treningu.
 * Akwizycja EKG trwa [ta] s, startuje [ts] s przed końcem fazy.
 */
data class TrainingPlan(
    val cycles: Int = 3,
    /** Czas wysiłku [s]. */
    val exerciseSec: Int = 60,
    /** Czas odpoczynku [s]. */
    val restSec: Int = 60,
    /** Czas akwizycji EKG [s] (ta). */
    val acquireSec: Int = 10,
    /** Offset startu akwizycji przed końcem fazy [s] (ts); 0 = na początku fazy / w momencie zmiany. */
    val acquireLeadSec: Int = 0,
    /** ECG po treningu [s]. */
    val postTrainingSec: Int = 120,
) {
    init {
        require(cycles >= 1)
        require(exerciseSec > 2 * acquireSec) {
            "tw > 2·ta (exerciseSec=$exerciseSec, acquireSec=$acquireSec)"
        }
        require(restSec > 2 * acquireSec) {
            "to > 2·ta (restSec=$restSec, acquireSec=$acquireSec)"
        }
        require(acquireLeadSec in 0..acquireSec)
    }
}

enum class TrainingPhaseKind {
    EXERCISE,
    REST,
    POST_TRAINING,
}

data class TrainingPhase(
    val index: Int,
    val kind: TrainingPhaseKind,
    val cycle: Int,
    val durationSec: Int,
    val label: String,
    /** Czy na początku tej fazy uruchomić akwizycję EKG. */
    val ecgAtStart: Boolean,
    val ecgLabel: String?,
)

data class TrainingTimeline(
    val phases: List<TrainingPhase>,
    val totalSec: Int,
)

object TrainingPlanner {
    /**
     * Interwał: W1 → O1 → W2 → O2 → W3 → 2 min po.
     * ECG: start treningu + przed każdą zmianą fazy + 2 min po (jak PDF).
     */
    fun interval(plan: TrainingPlan): TrainingTimeline {
        val phases = mutableListOf<TrainingPhase>()
        var idx = 0
        for (c in 1..plan.cycles) {
            phases += TrainingPhase(
                index = idx++,
                kind = TrainingPhaseKind.EXERCISE,
                cycle = c,
                durationSec = plan.exerciseSec,
                label = "Wysiłek $c/${plan.cycles}",
                ecgAtStart = true,
                ecgLabel = if (c == 1) "EKG start treningu" else "EKG przed Wysiłek $c",
            )
            if (c < plan.cycles) {
                phases += TrainingPhase(
                    index = idx++,
                    kind = TrainingPhaseKind.REST,
                    cycle = c,
                    durationSec = plan.restSec,
                    label = "Odpoczynek $c/${plan.cycles - 1}",
                    ecgAtStart = true,
                    ecgLabel = "EKG przed Odpoczynek $c",
                )
            }
        }
        // ECG na końcu ostatniego wysiłku (przed „koniec treningu”) — osobna krótka faza 0 s nie;
        // pobieramy ECG na starcie post-training oraz na końcu ostatniego W przez ecgAtEnd flag.
        // PDF: ECG przed koniec treningu (koniec W3) + 2 min po.
        // Realizacja: na końcu ostatniego W uruchamiamy ECG (przez marker na REST-less),
        // potem POST z ECG na początku (2 min po startuje po końcu W).
        phases += TrainingPhase(
            index = idx,
            kind = TrainingPhaseKind.POST_TRAINING,
            cycle = plan.cycles,
            durationSec = plan.postTrainingSec,
            label = "2 min po treningu",
            ecgAtStart = true,
            ecgLabel = "EKG 2 min po treningu",
        )
        // Marker: ECG na końcu ostatniego wysiłku — TrainingRunner sprawdza kind transition.
        return TrainingTimeline(phases = phases, totalSec = phases.sumOf { it.durationSec })
    }

    /** Czy przy wyjściu z fazy wysiłku (ostatni cykl) trzeba jeszcze ECG „przed koniec”. */
    fun needsEndOfLastExerciseEcg(phase: TrainingPhase, plan: TrainingPlan): Boolean =
        phase.kind == TrainingPhaseKind.EXERCISE && phase.cycle == plan.cycles
}
