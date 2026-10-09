package pl.cardioscp.rehab.clinic

/** Słownik powodów pominięcia dawki (jak DSD-mobile). */
object DoseSkipReasons {
    val ALL: List<String> = listOf(
        "Zapomniałem / nie było mnie",
        "Nudności / złe samopoczucie",
        "Brak leku w domu",
        "Lekarz odstawił / zmienił dawkę",
        "Kolizja z posiłkiem lub innym lekiem",
        "Inny powód",
    )

    fun isKnown(reason: String): Boolean =
        ALL.any { it.equals(reason.trim(), ignoreCase = true) }
}
