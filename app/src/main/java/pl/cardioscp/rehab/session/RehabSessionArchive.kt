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
    /** Podsumowanie ciśnienia z kwalifikacji (np. „128/82 · 72/min”). */
    val bloodPressureSummary: String? = null,
    /** Podsumowanie wagi (np. „78.2 kg”) — null gdy pominięto. */
    val weightSummary: String? = null,
    /** Wynik ankiety: true = pass, false = fail, null = brak. */
    val surveyPassed: Boolean? = null,
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
        bloodPressureSummary: String? = null,
        weightSummary: String? = null,
        surveyPassed: Boolean? = null,
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
            bloodPressureSummary = bloodPressureSummary,
            weightSummary = weightSummary,
            surveyPassed = surveyPassed,
        )
        sessions = sessions + archived
        persist()
        return archived
    }

    /** Gdy brak zarchiwizowanych sesji — pogrupuj istniejące pliki SCP w demo-sesje. */
    fun ensureSeedFromRecordings() {
        migrateLabelsAndSeedExtras()
        if (sessions.isNotEmpty()) return
        val files = recordingStore.list()
        if (files.isEmpty()) {
            return
        }
        val labels = listOf(
            "EKG kwalifikacyjne (przed sesją)",
            "EKG spoczynkowe (start treningu)",
            "EKG szczyt wysiłku 1/2",
            "EKG szczyt wysiłku 2/2",
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
                bloodPressureSummary = "128/82 · 72/min",
                weightSummary = "78.2 kg",
                surveyPassed = true,
            )
        }
        persist()
    }

    /** Migracja etykiet + uzupełnienie vitals/ankiety, gdy stara sesja ich nie miała. */
    private fun migrateLabelsAndSeedExtras() {
        if (sessions.isEmpty()) return
        var changed = false
        sessions = sessions.map { s ->
            var sessionChanged = false
            val newEcgs = s.ecgs.map { e ->
                val migrated = migrateEcgLabel(e.label)
                if (migrated != e.label) {
                    sessionChanged = true
                    e.copy(label = migrated)
                } else {
                    e
                }
            }
            val needsExtras = s.bloodPressureSummary == null &&
                s.weightSummary == null &&
                s.surveyPassed == null
            if (needsExtras) {
                changed = true
                s.copy(
                    ecgs = newEcgs,
                    bloodPressureSummary = "128/82 · 72/min",
                    weightSummary = "78.2 kg",
                    surveyPassed = true,
                )
            } else if (sessionChanged) {
                changed = true
                s.copy(ecgs = newEcgs)
            } else {
                s
            }
        }
        if (changed) persist()
    }

    fun sessionDateLabel(session: ArchivedRehabSession): String =
        SimpleDateFormat("d.MM.yyyy HH:mm", Locale.forLanguageTag("pl-PL")).format(Date(session.startedAtMs))

    private fun persist() {
        val arr = JSONArray()
        sessions.forEach { s ->
            arr.put(
                JSONObject()
                    .put("id", s.id)
                    .put("sessionNumber", s.sessionNumber)
                    .put("title", s.title)
                    .put("startedAtMs", s.startedAtMs)
                    .put("bloodPressureSummary", s.bloodPressureSummary)
                    .put("weightSummary", s.weightSummary)
                    .put("surveyPassed", s.surveyPassed)
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
                                    label = migrateEcgLabel(e.getString("label")),
                                    fileName = e.getString("fileName"),
                                    capturedAtMs = e.optLong("capturedAtMs"),
                                ),
                            )
                        }
                    }
                    val surveyPassed = when {
                        !o.has("surveyPassed") || o.isNull("surveyPassed") -> null
                        else -> o.getBoolean("surveyPassed")
                    }
                    add(
                        ArchivedRehabSession(
                            id = o.getString("id"),
                            sessionNumber = o.getInt("sessionNumber"),
                            title = o.optString("title", "Sesja rehabilitacji"),
                            startedAtMs = o.getLong("startedAtMs"),
                            ecgs = ecgs,
                            bloodPressureSummary = o.optString("bloodPressureSummary", null)
                                ?.takeIf { it.isNotBlank() && it != "null" },
                            weightSummary = o.optString("weightSummary", null)
                                ?.takeIf { it.isNotBlank() && it != "null" },
                            surveyPassed = surveyPassed,
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    companion object {
        private const val PREFS = "rehab_session_archive"
        private const val KEY = "sessions_v1"

        /** Stare etykiety → kwalifikacja. */
        fun migrateEcgLabel(label: String): String = when {
            label.contains("przed sesją", ignoreCase = true) &&
                !label.contains("kwalifik", ignoreCase = true) ->
                "EKG kwalifikacyjne (przed sesją)"
            label.equals("EKG spoczynkowe", ignoreCase = true) ->
                "EKG kwalifikacyjne (przed sesją)"
            else -> label
        }

        fun isQualificationEcg(label: String): Boolean {
            val l = label.lowercase()
            return "kwalifik" in l || "przed sesją" in l || "przed sesja" in l
        }

        fun isRestStartEcg(label: String): Boolean {
            val l = label.lowercase()
            return "start treningu" in l || ("spoczynk" in l && !isQualificationEcg(label))
        }
    }
}
