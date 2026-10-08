package pl.cardioscp.rehab.clinic

/** Lokalny katalog leków kardiologicznych (analog wyszukiwarki RPL w DSD — bez sieci). */
object DrugCatalog {
    data class Hit(
        val id: String,
        val name: String,
        val substance: String,
        val defaultDose: String,
    ) {
        val label: String get() = if (substance.isBlank()) name else "$name ($substance)"
    }

    private val ALL = listOf(
        Hit("bisoprolol", "Bisoprolol", "bisoprolol", "5 mg"),
        Hit("metoprolol", "Metoprolol", "metoprolol", "50 mg"),
        Hit("carvedilol", "Carvedilol", "carvedilol", "12.5 mg"),
        Hit("ramipril", "Ramipril", "ramipril", "5 mg"),
        Hit("perindopril", "Perindopril", "perindopril", "5 mg"),
        Hit("losartan", "Losartan", "losartan", "50 mg"),
        Hit("valsartan", "Valsartan", "valsartan", "80 mg"),
        Hit("amlodypina", "Amlodypina", "amlodypina", "5 mg"),
        Hit("furosemid", "Furosemid", "furosemid", "40 mg"),
        Hit("torasemid", "Torasemid", "torasemid", "10 mg"),
        Hit("spironolakton", "Spironolakton", "spironolakton", "25 mg"),
        Hit("eplerenon", "Eplerenon", "eplerenon", "25 mg"),
        Hit("asa", "ASA", "kwas acetylosalicylowy", "75 mg"),
        Hit("clopidogrel", "Klopidogrel", "klopidogrel", "75 mg"),
        Hit("atorwastatyna", "Atorwastatyna", "atorwastatyna", "40 mg"),
        Hit("rosuwastatyna", "Rosuwastatyna", "rosuwastatyna", "20 mg"),
        Hit("empagliflozyna", "Empagliflozyna", "empagliflozyna", "10 mg"),
        Hit("dapagliflozyna", "Dapagliflozyna", "dapagliflozyna", "10 mg"),
        Hit("digoksyna", "Digoksyna", "digoksyna", "0.125 mg"),
        Hit("warfarin", "Warfarin", "warfaryna", "5 mg"),
        Hit("apixaban", "Apiksaban", "apiksaban", "5 mg"),
        Hit("rivaroxaban", "Rivaroksaban", "rivaroksaban", "20 mg"),
        Hit("nitrogliceryna", "Nitrogliceryna", "nitrogliceryna", "0.5 mg"),
        Hit("amiodaron", "Amiodaron", "amiodaron", "200 mg"),
    )

    fun search(query: String, limit: Int = 24): List<Hit> {
        val q = query.trim().lowercase()
        if (q.length < 2) return emptyList()
        return ALL.filter {
            it.name.lowercase().contains(q) || it.substance.lowercase().contains(q)
        }.take(limit)
    }

    val PRESET_TIMES = listOf("08:00", "12:00", "18:00", "20:00")
}
