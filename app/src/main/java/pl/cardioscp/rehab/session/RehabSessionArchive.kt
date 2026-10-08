package pl.cardioscp.rehab.session

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import pl.cardioscp.rehab.scp.ScpRecording
import pl.cardioscp.rehab.scp.ScpRecordingStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class ArchivedEcgSlot(
    val id: String,
    val label: String,
    val fileName: String,
    val capturedAtMs: Long,
)

data class ArchivedRehabSession(
    val id: String,
    val sessionNumber: Int,
    val title: String,
    val startedAtMs: Long,
    val ecgs: List<ArchivedEcgSlot>,
)

/** Archiwum sesji rehab z listą zapisanych EKG (zakładka EKG). */
class RehabSessionArchive(
    context: Context,
    private val recordingStore: ScpRecordingStore,
) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private var sessions: List<ArchivedRehabSession> = load()

    fun list(): List<ArchivedRehabSession> = sessions.sortedByDescending { it.sessionNumber }

    fun resolveRecording(slot: ArchivedEcgSlot): ScpRecording? =
        recordingStore.list().firstOrNull { it.file.name == slot.fileName }

    fun archiveFromLiveSession(
        entries: List<SessionEcgEntry>,
        startedAtMs: Long,
        title: String = "Sesja rehabilitacji",
    ): ArchivedRehabSession? {
        if (entries.isEmpty()) return null
        val nextNum = (sessions.maxOfOrNull { it.sessionNumber } ?: 0) + 1
        val archived = ArchivedRehabSession(
            id = UUID.randomUUID().toString(),
            sessionNumber = nextNum,
            title = title,
            startedAtMs = startedAtMs,
            ecgs = entries.map { e ->
                ArchivedEcgSlot(
                    id = UUID.randomUUID().toString(),
                    label = e.label,
                    fileName = e.recording.file.name,
                    capturedAtMs = e.capturedAtMs,
                )
            },
        )
        sessions = sessions + archived
        persist()
        return archived
    }

    /** Gdy brak zarchiwizowanych sesji — pogrupuj istniejące pliki SCP w demo-sesje. */
    fun ensureSeedFromRecordings() {
        if (sessions.isNotEmpty()) return
        val files = recordingStore.list()
        if (files.isEmpty()) {
            // Puste placeholdery — użytkownik zobaczy numery po pierwszej sesji.
            return
        }
        val labels = listOf(
            "EKG spoczynkowe",
            "EKG szczyt 1",
            "EKG szczyt 2",
            "EKG po wysiłku",
        )
        sessions = files.chunked(4).take(6).mapIndexed { index, chunk ->
            ArchivedRehabSession(
                id = "seed-$index",
                sessionNumber = index + 1,
                title = "Sesja rehabilitacji",
                startedAtMs = chunk.minOf { it.modifiedAtMs },
                ecgs = chunk.mapIndexed { j, rec ->
                    ArchivedEcgSlot(
                        id = "seed-$index-$j",
                        label = labels.getOrElse(j) { "EKG ${j + 1}" },
                        fileName = rec.file.name,
                        capturedAtMs = rec.modifiedAtMs,
                    )
                },
            )
        }
        persist()
    }

    fun sessionDateLabel(session: ArchivedRehabSession): String =
        SimpleDateFormat("d.MM.yyyy HH:mm", Locale("pl")).format(Date(session.startedAtMs))

    private fun persist() {
        val arr = JSONArray()
        sessions.forEach { s ->
            arr.put(
                JSONObject()
                    .put("id", s.id)
                    .put("sessionNumber", s.sessionNumber)
                    .put("title", s.title)
                    .put("startedAtMs", s.startedAtMs)
                    .put(
                        "ecgs",
                        JSONArray().also { eArr ->
                            s.ecgs.forEach { e ->
                                eArr.put(
                                    JSONObject()
                                        .put("id", e.id)
                                        .put("label", e.label)
                                        .put("fileName", e.fileName)
                                        .put("capturedAtMs", e.capturedAtMs),
                                )
                            }
                        },
                    ),
            )
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    private fun load(): List<ArchivedRehabSession> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val ecgsArr = o.getJSONArray("ecgs")
                    val ecgs = buildList {
                        for (j in 0 until ecgsArr.length()) {
                            val e = ecgsArr.getJSONObject(j)
                            add(
                                ArchivedEcgSlot(
                                    id = e.getString("id"),
                                    label = e.getString("label"),
                                    fileName = e.getString("fileName"),
                                    capturedAtMs = e.optLong("capturedAtMs"),
                                ),
                            )
                        }
                    }
                    add(
                        ArchivedRehabSession(
                            id = o.getString("id"),
                            sessionNumber = o.getInt("sessionNumber"),
                            title = o.optString("title", "Sesja rehabilitacji"),
                            startedAtMs = o.getLong("startedAtMs"),
                            ecgs = ecgs,
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    companion object {
        private const val PREFS = "rehab_session_archive"
        private const val KEY = "sessions_v1"
    }
}
