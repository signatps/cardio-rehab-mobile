package pl.cardioscp.rehab.host

import android.content.Context
import pl.cardioscp.rehab.clinic.DiseaseCatalog
import java.io.File
import java.io.IOException

/**
 * Katalog ICD-10/9 PL: bundlowany asset + opcjonalna aktualizacja w plikach aplikacji.
 *
 * AAPT często **rozpakowuje** `*.gz` i zapisuje jako `icd_pl.json` (bez `.gz`).
 */
object DiseaseCatalogLoader {
    private val ASSET_CANDIDATES = listOf(
        "icd_pl.json.gz",
        "icd_pl.json",
    )
    const val UPDATE_FILE = "icd_pl_updated.json.gz"

    @Volatile
    private var cached: DiseaseCatalog.Index? = null

    fun get(context: Context): DiseaseCatalog.Index {
        cached?.let { return it }
        synchronized(this) {
            cached?.let { return it }
            val updated = File(context.filesDir, UPDATE_FILE)
            if (updated.isFile && updated.length() > 100) {
                runCatching {
                    val index = decode(updated.readBytes())
                    cached = index
                    return index
                }
            }
            var last: Exception? = null
            for (name in ASSET_CANDIDATES) {
                try {
                    val bytes = context.assets.open(name).use { it.readBytes() }
                    val index = decode(bytes)
                    cached = index
                    return index
                } catch (e: Exception) {
                    last = e
                }
            }
            throw IOException(
                last?.message ?: "brak assets/icd_pl.json(.gz)",
                last,
            )
        }
    }

    fun invalidate() {
        cached = null
    }

    fun saveUpdate(context: Context, gzipBytes: ByteArray): DiseaseCatalog.Index {
        val file = File(context.filesDir, UPDATE_FILE)
        file.writeBytes(gzipBytes)
        invalidate()
        return get(context)
    }

    /** Usuwa aktualizację i wraca do bundlowanego assetu z APK. */
    fun reloadFromAssets(context: Context): DiseaseCatalog.Index {
        File(context.filesDir, UPDATE_FILE).delete()
        invalidate()
        return get(context)
    }

    private fun decode(bytes: ByteArray): DiseaseCatalog.Index =
        if (isGzip(bytes)) DiseaseCatalog.loadGzip(bytes)
        else DiseaseCatalog.loadJson(bytes.toString(Charsets.UTF_8))

    private fun isGzip(bytes: ByteArray): Boolean =
        bytes.size >= 2 &&
            (bytes[0].toInt() and 0xFF) == 0x1f &&
            (bytes[1].toInt() and 0xFF) == 0x8b
}
