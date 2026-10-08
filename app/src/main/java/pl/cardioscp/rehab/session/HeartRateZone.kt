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

/** Kolor wartości BPM na wskaźniku. */
enum class HeartRateValueTone {
    /** W środku strefy docelowej. */
    IN_ZONE,
    /** W strefie, ale blisko min/max. */
    NEAR_EDGE,
    /** Poza limitem. */
    OUT_OF_ZONE,
    /** Brak pomiaru. */
    WAITING,
}

object HeartRateCoach {
    /** Margines „blisko granicy” (bpm) wewnątrz strefy → żółty. */
    const val NEAR_EDGE_BPM = 5

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

    fun valueTone(
        bpm: Int?,
        limit: CycleHeartRateLimit?,
        nearMargin: Int = NEAR_EDGE_BPM,
    ): HeartRateValueTone {
        if (limit == null) return HeartRateValueTone.WAITING
        val pulse = bpm ?: return HeartRateValueTone.WAITING
        if (pulse <= 0) return HeartRateValueTone.WAITING
        if (pulse < limit.minBpm || pulse > limit.maxBpm) return HeartRateValueTone.OUT_OF_ZONE
        val nearLow = pulse <= limit.minBpm + nearMargin
        val nearHigh = pulse >= limit.maxBpm - nearMargin
        return if (nearLow || nearHigh) HeartRateValueTone.NEAR_EDGE else HeartRateValueTone.IN_ZONE
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
                minBpm = 80 + (c - 1) * 5,
                maxBpm = 90 + (c - 1) * 5,
            )
        }
}
