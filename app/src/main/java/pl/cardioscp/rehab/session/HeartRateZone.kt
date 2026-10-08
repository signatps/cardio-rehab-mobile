package pl.cardioscp.rehab.session

/** Limit tętna dla jednego cyklu wysiłku. */
data class CycleHeartRateLimit(
    val cycle: Int,
    val minBpm: Int,
    val maxBpm: Int,
) {
    init {
        require(cycle >= 1)
        require(minBpm in 40..200)
        require(maxBpm in 40..220)
        require(minBpm < maxBpm) { "minBpm ($minBpm) musi być < maxBpm ($maxBpm)" }
    }
}

enum class HeartRateCoachCue {
    /** Tętno poniżej progu — pacjent ma przyspieszyć. */
    SPEED_UP,
    /** Tętno powyżej progu — pacjent ma zwolnić. */
    SLOW_DOWN,
    /** W strefie docelowej. */
    IN_ZONE,
    /** Brak wiarygodnego pomiaru. */
    WAITING,
}

object HeartRateCoach {
    fun evaluate(bpm: Int?, limit: CycleHeartRateLimit?): HeartRateCoachCue {
        if (limit == null) return HeartRateCoachCue.WAITING
        val pulse = bpm ?: return HeartRateCoachCue.WAITING
        if (pulse <= 0) return HeartRateCoachCue.WAITING
        return when {
            pulse < limit.minBpm -> HeartRateCoachCue.SPEED_UP
            pulse > limit.maxBpm -> HeartRateCoachCue.SLOW_DOWN
            else -> HeartRateCoachCue.IN_ZONE
        }
    }

    fun screenText(cue: HeartRateCoachCue): String? = when (cue) {
        HeartRateCoachCue.SPEED_UP -> "PRZYSPIESZ"
        HeartRateCoachCue.SLOW_DOWN -> "ZWOLNIJ"
        HeartRateCoachCue.IN_ZONE -> null
        HeartRateCoachCue.WAITING -> null
    }

    fun speakText(cue: HeartRateCoachCue): String? = when (cue) {
        HeartRateCoachCue.SPEED_UP -> "Przyspiesz"
        HeartRateCoachCue.SLOW_DOWN -> "Zwolnij"
        HeartRateCoachCue.IN_ZONE -> null
        HeartRateCoachCue.WAITING -> null
    }

    fun defaultLimits(cycles: Int): List<CycleHeartRateLimit> =
        (1..cycles).map { c ->
            CycleHeartRateLimit(
                cycle = c,
                minBpm = 90 + (c - 1) * 5,
                maxBpm = 120 + (c - 1) * 5,
            )
        }
}
