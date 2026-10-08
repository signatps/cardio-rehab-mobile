package pl.cardioscp.rehab.host

import android.content.Context
import pl.cardioscp.rehab.clinic.MedCatalog
import java.io.File
import java.io.IOException

/**
 * Ładuje bundlowany katalog RPL (MZ) z assets.
 * Opcjonalna aktualizacja: plik w `filesDir` (jak ICD w DSD) — po zapisie ma pierwszeństwo.
 *
 * AAPT często **rozpakowuje** `*.gz` i zapisuje jako `rpl_medications.json` (bez `.gz`).
 */
object MedCatalogLoader {
    private val ASSET_CANDIDATES = listOf(
        "rpl_medications.json.gz",
        "rpl_medications.json",
    )
    const val UPDATE_FILE = "rpl_medications_updated.json.gz"

    @Volatile
    private var cached: MedCatalog.Index? = null

    fun get(context: Context): MedCatalog.Index {
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
                last?.message ?: "brak assets/rpl_medications.json(.gz)",
                last,
            )
        }
    }

    fun invalidate() {
        cached = null
    }

    /** Zapisuje opcjonalną aktualizację katalogu (gzip JSON telekiosk/DSD). */
    fun saveUpdate(context: Context, gzipBytes: ByteArray): MedCatalog.Index {
        val file = File(context.filesDir, UPDATE_FILE)
        file.writeBytes(gzipBytes)
        invalidate()
        return get(context)
    }

    /** Usuwa aktualizację i wraca do bundlowanego assetu z APK. */
    fun reloadFromAssets(context: Context): MedCatalog.Index {
        File(context.filesDir, UPDATE_FILE).delete()
        invalidate()
        return get(context)
    }

    private fun decode(bytes: ByteArray): MedCatalog.Index =
        if (isGzip(bytes)) MedCatalog.loadGzip(bytes)
        else MedCatalog.loadJson(bytes.toString(Charsets.UTF_8))

    private fun isGzip(bytes: ByteArray): Boolean =
        bytes.size >= 2 &&
            (bytes[0].toInt() and 0xFF) == 0x1f &&
            (bytes[1].toInt() and 0xFF) == 0x8b
}
