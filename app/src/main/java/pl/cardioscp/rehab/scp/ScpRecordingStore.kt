package pl.cardioscp.rehab.scp

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ScpRecording(
    val file: File,
    val displayName: String,
    val sizeBytes: Long,
    val modifiedAtMs: Long,
)

/**
 * Persists complete `.scp` files under app-private storage.
 * Never stores partial fragment buffers — only finished downloads.
 */
class ScpRecordingStore(context: Context) {
    private val dir: File = File(context.filesDir, "recordings").also { it.mkdirs() }

    fun saveComplete(bytes: ByteArray, deviceSerial: String? = null): ScpRecording {
        require(bytes.isNotEmpty()) { "empty SCP" }
        val stamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
        val serial = deviceSerial?.takeIf { it.isNotBlank() }?.let { "_$it" } ?: ""
        val name = "ECG_${stamp}${serial}.scp"
        val file = File(dir, name)
        file.writeBytes(bytes)
        return ScpRecording(
            file = file,
            displayName = name,
            sizeBytes = bytes.size.toLong(),
            modifiedAtMs = file.lastModified(),
        )
    }

    fun list(): List<ScpRecording> =
        dir.listFiles { f -> f.isFile && f.extension.equals("scp", ignoreCase = true) }
            .orEmpty()
            .sortedByDescending { it.lastModified() }
            .map {
                ScpRecording(
                    file = it,
                    displayName = it.name,
                    sizeBytes = it.length(),
                    modifiedAtMs = it.lastModified(),
                )
            }

    fun read(file: File): ByteArray {
        require(file.exists() && file.canonicalPath.startsWith(dir.canonicalPath)) {
            "recording outside store"
        }
        return file.readBytes()
    }
}
