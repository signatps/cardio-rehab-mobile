package pl.cardioscp.rehab.clinic

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.text.Normalizer
import java.util.zip.GZIPInputStream

/**
 * Lokalny katalog leków z **RPL** (Rejestr Produktów Leczniczych, CeZ / MZ)
 * — ten sam dump co DSD-mobile `rpl_medications.json.gz`.
 */
object MedCatalog {
    data class Meta(
        val source: String,
        val sourceUrl: String,
        val portal: String,
        val asOf: String,
        val count: Int,
    )

    data class Hit(
        val id: String,
        val name: String,
        val inn: String,
        val form: String,
        val strength: String,
    ) {
        val label: String
            get() = listOf(name, strength, form).filter { it.isNotBlank() }.joinToString(" · ")
    }

    class Index(
        val meta: Meta,
        private val hay: List<IndexedRow>,
    ) {
        fun search(query: String, limit: Int = 20): List<Hit> {
            val q = fold(query)
            if (q.length < 2) return emptyList()
            val scored = ArrayList<Scored>(64)
            for (row in hay) {
                val rank = when {
                    row.nameFold.startsWith(q) -> 0
                    row.innFold.startsWith(q) -> 1
                    q in row.nameFold -> 2
                    q in row.innFold -> 3
                    q in row.blob -> 4
                    else -> continue
                }
                scored += Scored(rank, row.name.length, row)
            }
            scored.sortWith(
                compareBy<Scored> { it.rank }
                    .thenBy { it.nameLen }
                    .thenBy { it.row.name.lowercase() }
                    .thenBy { it.row.form.lowercase() }
                    .thenBy { it.row.strength.lowercase() },
            )
            val out = ArrayList<Hit>(limit.coerceAtMost(50))
            val seen = HashSet<String>()
            for (s in scored) {
                val key = "${s.row.name.lowercase()}|${s.row.form.lowercase()}|${s.row.strength.lowercase()}"
                if (!seen.add(key)) continue
                out += Hit(s.row.id, s.row.name, s.row.inn, s.row.form, s.row.strength)
                if (out.size >= limit) break
            }
            return out
        }
    }

    data class IndexedRow(
        val id: String,
        val name: String,
        val inn: String,
        val form: String,
        val strength: String,
        val nameFold: String,
        val innFold: String,
        val blob: String,
    )

    private data class Scored(val rank: Int, val nameLen: Int, val row: IndexedRow)

    val PRESET_TIMES: List<String> = listOf("08:00", "12:00", "18:00", "20:00", "22:00")

    fun fold(text: String): String {
        val raw = Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
        return buildString(raw.length) {
            for (ch in raw) {
                if (Character.getType(ch) != Character.NON_SPACING_MARK.toInt()) append(ch)
            }
        }
    }

    fun loadGzip(bytes: ByteArray): Index {
        val raw = GZIPInputStream(ByteArrayInputStream(bytes)).bufferedReader(Charsets.UTF_8).use { it.readText() }
        return loadJson(raw)
    }

    fun loadJson(raw: String): Index {
        val root = JSONObject(raw)
        val items = root.optJSONArray("items") ?: JSONArray()
        val hay = ArrayList<IndexedRow>(items.length())
        for (i in 0 until items.length()) {
            val arr = items.optJSONArray(i) ?: continue
            if (arr.length() < 5) continue
            val id = arr.optString(0)
            val name = arr.optString(1)
            val inn = arr.optString(2)
            val form = arr.optString(3)
            val strength = arr.optString(4)
            val nameFold = fold(name)
            val innFold = fold(inn)
            val blob = fold("$name $inn $form $strength")
            hay += IndexedRow(id, name, inn, form, strength, nameFold, innFold, blob)
        }
        val meta = Meta(
            source = root.optString("source", "RPL"),
            sourceUrl = root.optString("source_url"),
            portal = root.optString("portal"),
            asOf = root.optString("as_of"),
            count = hay.size,
        )
        return Index(meta, hay)
    }
}
