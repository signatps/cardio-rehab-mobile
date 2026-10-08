package pl.cardioscp.rehab.clinic

/** Lokalny katalog ICD (skrót jak DSD — bez pobierania NFZ). */
object DiseaseCatalog {
    data class Hit(
        val code: String,
        val name: String,
        val system: String = "ICD-10",
    ) {
        val label: String get() = "$code · $name"
    }

    private val ALL = listOf(
        Hit("I10", "Nadciśnienie tętnicze samoistne"),
        Hit("I11", "Choroba nadciśnieniowa serca"),
        Hit("I20", "Dławica piersiowa"),
        Hit("I21", "Ostry zawał mięśnia sercowego"),
        Hit("I25", "Przewlekła choroba niedokrwienna serca"),
        Hit("I48", "Migotanie i trzepotanie przedsionków"),
        Hit("I50.0", "Zastoinowa niewydolność serca"),
        Hit("I50.1", "Niewydolność lewokomorowa"),
        Hit("I50.9", "Niewydolność serca, nieokreślona"),
        Hit("E11", "Cukrzyca typu 2"),
        Hit("E78", "Zaburzenia gospodarki lipidowej"),
        Hit("N18", "Przewlekła choroba nerek"),
        Hit("J44", "Inna przewlekła obturacyjna choroba płuc"),
        Hit("I63", "Zawał mózgu"),
        Hit("I70", "Miażdżyca"),
        Hit("I35", "Niewydolność zastawki aortalnej"),
        Hit("I34", "Niewydolność zastawki mitralnej"),
        Hit("I42", "Kardiomiopatia"),
        Hit("R00.0", "Tachykardia, nieokreślona"),
        Hit("R00.1", "Bradykardia, nieokreślona"),
        Hit("Z95.1", "Obecność pomostów aortalno-wieńcowych"),
        Hit("Z95.5", "Obecność wszczepu/przeszczepu wieńcowego"),
    )

    fun search(query: String, limit: Int = 24): List<Hit> {
        val q = query.trim().lowercase()
        if (q.length < 2) return emptyList()
        return ALL.filter {
            it.code.lowercase().contains(q) || it.name.lowercase().contains(q)
        }.take(limit)
    }
}
