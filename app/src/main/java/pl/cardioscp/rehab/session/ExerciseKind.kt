package pl.cardioscp.rehab.session

/**
 * Rodzaj ćwiczenia w fazie wysiłku.
 * Docelowo programowany z platformy; na razie domyślnie Nordic walking.
 */
enum class ExerciseKind {
    NORDIC_WALKING,
    ;

    val displayNamePl: String
        get() = when (this) {
            NORDIC_WALKING -> "Nordic walking"
        }
}

/** Wskazówka wizualna / tekstowa w trakcie treningu. */
enum class TrainingCoachVisual {
    /** Wysiłek — duży piktogram rodzaju ćwiczenia + „Ćwicz”. */
    EXERCISE,
    /** Tuż przed EKG szczytowym — zatrzymaj się. */
    STOP_BEFORE_PEAK_ECG,
    /** Trwa zapis EKG — pozostań nieruchomo. */
    HOLD_STILL_ECG,
    /** Odpoczynek. */
    REST,
}
