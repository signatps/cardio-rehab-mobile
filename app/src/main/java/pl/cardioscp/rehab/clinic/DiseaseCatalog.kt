package pl.cardioscp.rehab.clinic

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.util.zip.GZIPInputStream

/**
 * Lokalny katalog kodów **ICD-10 PL** (rozpoznania) oraz **ICD-9** (procedury NFZ)
 * — ten sam dump co DSD-mobile `icd_pl.json.gz`.
 */
object DiseaseCatalog {
    enum class System { ICD10, ICD9 }

    data class Meta(
        val source: String,
        val sourceUrlIcd9: String,
        val sourceUrlIcd10: String,
        val portal: String,
        val asOf: String,
        val count: Int,
        val icd9NfzGen: String = "",
        val icd9NfzPub: String = "",
    )

    data class Hit(
        val system: System,
        val code: String,
        val name: String,
    ) {
        val label: String get() = "${systemLabel(system)} $code · $name"
        val systemTag: String get() = systemLabel(system)
    }

    class Index(
        val meta: Meta,
        private val hay: List<IndexedRow>,
    ) {
        fun search(
            query: String,
            limit: Int = 25,
            system: System? = null,
        ): List<Hit> {
            val q = MedCatalog.fold(query)
            if (q.length < 2) return emptyList()
            val scored = ArrayList<Scored>(64)
            for (row in hay) {
                if (system != null && row.system != system) continue
                val rank = when {
                    MedCatalog.fold(row.code).startsWith(q) -> 0
                    row.codeFold.contains(q) -> 1
                    row.nameFold.startsWith(q) -> 2
                    q in row.nameFold -> 3
                    q in row.blob -> 4
                    else -> continue
                }
                scored += Scored(rank, row.name.length, row)
            }
            scored.sortWith(
                compareBy<Scored> { it.rank }
                    .thenBy { it.nameLen }
                    .thenBy { it.row.code }
                    .thenBy { it.row.name.lowercase() },
            )
            val out = ArrayList<Hit>(limit.coerceAtMost(50))
            val seen = HashSet<String>()
            for (s in scored) {
                val key = "${s.row.system}|${s.row.code}"
                if (!seen.add(key)) continue
                out += Hit(s.row.system, s.row.code, s.row.name)
                if (out.size >= limit) break
            }
            return out
        }
    }

    data class IndexedRow(
        val system: System,
        val code: String,
        val name: String,
        val codeFold: String,
        val nameFold: String,
        val blob: String,
    )

    private data class Scored(val rank: Int, val nameLen: Int, val row: IndexedRow)

    fun systemLabel(system: System): String = when (system) {
        System.ICD10 -> "ICD-10"
        System.ICD9 -> "ICD-9"
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
            if (arr.length() < 3) continue
            val sys = when (arr.optString(0).uppercase()) {
                "ICD10", "ICD-10" -> System.ICD10
                "ICD9", "ICD-9" -> System.ICD9
                else -> continue
            }
            val code = arr.optString(1).trim()
            val name = arr.optString(2).trim()
            if (code.isBlank() || name.isBlank()) continue
            val nameFold = MedCatalog.fold(name)
            val codeFold = MedCatalog.fold(code)
            hay += IndexedRow(
                system = sys,
                code = code,
                name = name,
                codeFold = codeFold,
                nameFold = nameFold,
                blob = "$codeFold $nameFold",
            )
        }
        val meta = Meta(
            source = root.optString("source").ifBlank { "ICD PL" },
            sourceUrlIcd9 = root.optString("source_url_icd9"),
            sourceUrlIcd10 = root.optString("source_url_icd10"),
            portal = root.optString("portal"),
            asOf = root.optString("as_of"),
            count = hay.size,
            icd9NfzGen = root.optString("icd9_nfz_gen"),
            icd9NfzPub = root.optString("icd9_nfz_pub"),
        )
        return Index(meta, hay)
    }

    /** Parsuje XML słownika ICD-9 z portalu NFZ (`pozycja_slownika`). */
    fun parseNfzIcd9Xml(xml: String): List<Hit> {
        val out = ArrayList<Hit>(8192)
        val re = Regex(
            """<pozycja_slownika\s+kod="([^"]+)"\s+nazwa="([^"]+)"(?:\s+status="([^"]*)")?""",
            RegexOption.IGNORE_CASE,
        )
        for (m in re.findAll(xml)) {
            val code = m.groupValues[1].trim()
            val name = m.groupValues[2]
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .trim()
            val status = m.groupValues.getOrNull(3)?.trim().orEmpty()
            if (status.isNotEmpty() && !status.equals("A", ignoreCase = true)) continue
            if (code.isBlank() || name.isBlank()) continue
            out += Hit(System.ICD9, code, name)
        }
        return out
    }

    /** JSON listy `{code,name}` (ICD-10 PL). */
    fun parseIcd10JsonList(raw: String): List<Hit> {
        val arr = JSONArray(raw)
        val out = ArrayList<Hit>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val code = o.optString("code").trim()
            val name = o.optString("name").trim()
            if (code.isBlank() || name.isBlank()) continue
            out += Hit(System.ICD10, code, name)
        }
        return out
    }

    fun mergeToJson(
        icd10: List<Hit>,
        icd9: List<Hit>,
        asOf: String,
        icd9NfzGen: String = "",
        icd9NfzPub: String = "",
    ): String {
        val items = JSONArray()
        for (h in icd10 + icd9) {
            val sys = when (h.system) {
                System.ICD10 -> "ICD10"
                System.ICD9 -> "ICD9"
            }
            items.put(JSONArray().put(sys).put(h.code).put(h.name))
        }
        return JSONObject()
            .put("source", "NFZ slowniki ICD-9 + ICD-10 PL (CSIOZ/MZ)")
            .put(
                "source_url_icd9",
                "https://slowniki.nfz.gov.pl/WersjeSlownikow/PobierzXml?kodSlownika=ICD9",
            )
            .put("source_url_icd10", "https://github.com/basiekjusz/icd-pl (CSIOZ)")
            .put("portal", "https://slowniki.nfz.gov.pl/")
            .put("as_of", asOf)
            .put("icd9_nfz_gen", icd9NfzGen)
            .put("icd9_nfz_pub", icd9NfzPub)
            .put("count", icd10.size + icd9.size)
            .put("items", items)
            .toString()
    }
}
