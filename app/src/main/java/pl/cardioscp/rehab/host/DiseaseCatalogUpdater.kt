package pl.cardioscp.rehab.host

import android.content.Context
import pl.cardioscp.rehab.clinic.DiseaseCatalog
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.zip.GZIPOutputStream

/**
 * Aktualizacja katalogu ICD:
 * - **ICD-9** z publicznego portalu NFZ (`slowniki.nfz.gov.pl`),
 * - **ICD-10 PL** z publicznego dumpa CSIOZ/MZ.
 */
object DiseaseCatalogUpdater {
    const val NFZ_ICD9_URL =
        "https://slowniki.nfz.gov.pl/WersjeSlownikow/PobierzXml?kodSlownika=ICD9"
    const val ICD10_JSON_URL =
        "https://raw.githubusercontent.com/basiekjusz/icd-pl/main/json/icd10_pl.json"

    data class Result(
        val index: DiseaseCatalog.Index,
        val message: String,
    )

    fun refresh(context: Context): Result {
        val icd9Xml = httpGet(NFZ_ICD9_URL)
        val icd9 = DiseaseCatalog.parseNfzIcd9Xml(icd9Xml)
        require(icd9.size >= 100) { "ICD-9 NFZ: za mało pozycji (${icd9.size})" }
        val gen = Regex("""nr_gen="([^"]+)"""").find(icd9Xml)?.groupValues?.getOrNull(1).orEmpty()
        val pub = Regex("""czas_pub="([^"]+)"""").find(icd9Xml)?.groupValues?.getOrNull(1).orEmpty()

        val icd10Raw = httpGet(ICD10_JSON_URL)
        val icd10 = DiseaseCatalog.parseIcd10JsonList(icd10Raw)
        require(icd10.size >= 100) { "ICD-10: za mało pozycji (${icd10.size})" }

        val asOf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("Europe/Warsaw")
        }.format(Date())
        val json = DiseaseCatalog.mergeToJson(
            icd10 = icd10,
            icd9 = icd9,
            asOf = asOf,
            icd9NfzGen = gen,
            icd9NfzPub = pub,
        )
        val gzip = gzipBytes(json.toByteArray(Charsets.UTF_8))
        val index = DiseaseCatalogLoader.saveUpdate(context, gzip)
        return Result(
            index = index,
            message = "Zaktualizowano ICD: ${index.meta.count} pozycji " +
                "(ICD-10 ${icd10.size}, ICD-9 NFZ ${icd9.size}" +
                (if (gen.isNotBlank()) ", gen $gen" else "") + ").",
        )
    }

    private fun httpGet(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 45_000
            readTimeout = 90_000
            instanceFollowRedirects = true
            requestMethod = "GET"
            setRequestProperty(
                "User-Agent",
                "ProPLUS-CardioRehab/1.0 (ICD catalog refresh)",
            )
            setRequestProperty("Accept", "*/*")
        }
        try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                error("HTTP $code dla $url: ${body.take(200)}")
            }
            return body
        } finally {
            conn.disconnect()
        }
    }

    private fun gzipBytes(raw: ByteArray): ByteArray {
        val bos = ByteArrayOutputStream()
        GZIPOutputStream(bos).use { it.write(raw) }
        return bos.toByteArray()
    }
}
