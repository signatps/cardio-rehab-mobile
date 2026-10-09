package pl.cardioscp.rehab.session

/**
 * Pełna skala Borga RPE 6–20 (wysiłek odczuwany) — standard w kardiorehabilitacji.
 */
data class BorgLevel(
    val score: Int,
    val labelPl: String,
)

object BorgScale {
    val levels: List<BorgLevel> = listOf(
        BorgLevel(6, "Brak wysiłku"),
        BorgLevel(7, "Niezwykle lekki"),
        BorgLevel(8, ""),
        BorgLevel(9, "Bardzo lekki"),
        BorgLevel(10, ""),
        BorgLevel(11, "Lekki"),
        BorgLevel(12, ""),
        BorgLevel(13, "Dość ciężki"),
        BorgLevel(14, ""),
        BorgLevel(15, "Ciężki"),
        BorgLevel(16, ""),
        BorgLevel(17, "Bardzo ciężki"),
        BorgLevel(18, ""),
        BorgLevel(19, "Niezwykle ciężki"),
        BorgLevel(20, "Wysiłek maksymalny"),
    )

    fun isValid(score: Int): Boolean = score in 6..20

    fun labelFor(score: Int): String =
        levels.firstOrNull { it.score == score }?.labelPl.orEmpty()

    fun summaryPl(score: Int?): String {
        if (score == null || !isValid(score)) return "brak"
        val label = labelFor(score)
        return if (label.isBlank()) "$score" else "$score — $label"
    }
}
