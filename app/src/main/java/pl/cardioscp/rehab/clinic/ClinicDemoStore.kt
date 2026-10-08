package pl.cardioscp.rehab.clinic

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

/** Lokalny magazyn demo (jak mobile-DSD) — leki/choroby/pomiary z persystencją SharedPreferences. */
class ClinicDemoStore(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val zone = ZoneId.systemDefault()
    private val today = LocalDate.now()

    private var measurements = defaultMeasurements()
    private var medications = defaultMedications()
    private var doses = defaultDoses()
    private var diseases = defaultDiseases()
    private var sessions = defaultSessions()

    init {
        loadPersisted()
        ensureTodayDoses()
    }

    fun snapshot(): ClinicSnapshot = ClinicSnapshot(
        patientName = "Jan Kowalski",
        measurements = measurements.sortedByDescending { it.measuredAtMs },
        medications = medications,
        todayDoses = doses.sortedBy { it.time },
        diseases = diseases,
        sessions = sessions.sortedWith(compareBy({ it.date }, { it.time })),
        dayPlan = buildDayPlan(),
    )

    fun addMeasurement(
        kind: VitalKind,
        label: String,
        valueText: String,
        note: String = "",
        sessionGroupId: String? = null,
        sessionGroupTitle: String? = null,
    ) {
        measurements = listOf(
            ClinicMeasurement(
                id = UUID.randomUUID().toString(),
                kind = kind,
                label = label,
                valueText = valueText,
                measuredAtMs = System.currentTimeMillis(),
                note = note,
                sessionGroupId = sessionGroupId,
                sessionGroupTitle = sessionGroupTitle,
            ),
        ) + measurements
    }

    fun markDoseTaken(id: String) {
        val now = System.currentTimeMillis()
        doses = doses.map {
            if (it.id == id) it.copy(status = DoseStatus.TAKEN, takenAtMs = now) else it
        }
        persist()
    }

    fun addMedication(
        name: String,
        doseLabel: String,
        times: List<String>,
        note: String = "",
    ): Medication {
        val sorted = times.map { it.trim() }.filter { it.matches(Regex("\\d{1,2}:\\d{2}")) }
            .map { normalizeTime(it) }.distinct().sorted()
        require(sorted.isNotEmpty()) { "Wymagana co najmniej jedna godzina" }
        val med = Medication(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            doseLabel = doseLabel.trim().ifBlank { "1" },
            scheduleNote = buildString {
                append(sorted.joinToString(", "))
                if (note.isNotBlank()) append(" · ").append(note.trim())
            },
            times = sorted,
        )
        medications = medications + med
        ensureTodayDoses()
        persist()
        return med
    }

    fun removeMedication(id: String): Boolean {
        val removed = medications.firstOrNull { it.id == id } ?: return false
        medications = medications.filterNot { it.id == id }
        doses = doses.filterNot { it.drugName == removed.name }
        persist()
        return true
    }

    fun addDisease(
        name: String,
        icd: String,
        status: DiseaseStatus,
        diagnosedLabel: String,
        note: String = "",
        codingSystem: String = "ICD-10",
    ): Disease {
        val disease = Disease(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            icd = icd.trim(),
            codingSystem = codingSystem.trim().ifBlank { "ICD-10" },
            status = status,
            diagnosedLabel = diagnosedLabel.trim().ifBlank {
                LocalDate.now().toString().take(7)
            },
            note = note.trim(),
        )
        diseases = listOf(disease) + diseases
        persist()
        return disease
    }

    fun updateDiseaseStatus(id: String, status: DiseaseStatus): Boolean {
        val before = diseases
        diseases = diseases.map { if (it.id == id) it.copy(status = status) else it }
        if (diseases == before) return false
        persist()
        return true
    }

    fun removeDisease(id: String): Boolean {
        val next = diseases.filterNot { it.id == id }
        if (next.size == diseases.size) return false
        diseases = next
        persist()
        return true
    }

    fun markTodayRehabSessionDone() {
        val now = System.currentTimeMillis()
        sessions = sessions.map {
            if (it.date == today &&
                it.kind == PlannedSessionKind.REHAB_INTERVAL &&
                it.status == PlannedSessionStatus.SCHEDULED
            ) {
                it.copy(status = PlannedSessionStatus.DONE, completedAtMs = now)
            } else {
                it
            }
        }
        persist()
    }

    /**
     * Najbliższa nieprzyjęta dawka (dziś lub jutro) do AlarmManager.
     * @return Triple(triggerAtMs, title, body) lub null
     */
    fun nextPendingDoseAlarm(): Triple<Long, String, String>? {
        val now = System.currentTimeMillis()
        val candidates = mutableListOf<Triple<Long, String, String>>()
        for (dayOffset in 0..1) {
            val date = today.plusDays(dayOffset.toLong())
            for (med in medications) {
                for (t in med.times.ifEmpty { parseTimes(med.scheduleNote) }) {
                    val time = runCatching { LocalTime.parse(normalizeTime(t)) }.getOrNull()
                        ?: continue
                    val at = date.atTime(time).atZone(zone).toInstant().toEpochMilli()
                    if (at <= now + 15_000L) continue
                    if (dayOffset == 0) {
                        val alreadyTaken = doses.any {
                            it.drugName == med.name &&
                                it.time == time &&
                                it.status == DoseStatus.TAKEN
                        }
                        if (alreadyTaken) continue
                    }
                    candidates += Triple(
                        at,
                        "Przypomnienie o leku",
                        "${med.name} · ${med.doseLabel} · ${normalizeTime(t)}",
                    )
                }
            }
        }
        return candidates.minByOrNull { it.first }
    }

    /** Payload powiadomienia „teraz” — pierwsza zaległa / bieżąca dawka PENDING. */
    fun dueMedicationReminderPayload(nowMs: Long = System.currentTimeMillis()): Pair<String, String>? {
        val nowTime = java.time.Instant.ofEpochMilli(nowMs).atZone(zone).toLocalTime()
        val due = doses
            .filter { it.status == DoseStatus.PENDING && !it.time.isAfter(nowTime) }
            .minByOrNull { it.time }
            ?: return null
        return "Przypomnienie o leku" to
            "${due.drugName} · ${due.doseLabel} · ${due.time}"
    }

    fun ensureTodayDoses() {
        val existingKeys = doses.map { "${it.drugName}|${it.time}" }.toSet()
        val generated = mutableListOf<MedDose>()
        for (med in medications) {
            val times = med.times.ifEmpty { parseTimes(med.scheduleNote) }
            for (t in times) {
                val time = runCatching { LocalTime.parse(normalizeTime(t)) }.getOrNull()
                    ?: continue
                val key = "${med.name}|$time"
                if (key in existingKeys) continue
                generated += MedDose(
                    id = "dose-${med.id}-$t",
                    drugName = med.name,
                    doseLabel = med.doseLabel,
                    time = time,
                    status = DoseStatus.PENDING,
                )
            }
        }
        if (generated.isNotEmpty()) {
            doses = (doses + generated).distinctBy { "${it.drugName}|${it.time}" }
        }
    }

    /**
     * Plan dnia = leki + sesja rehab.
     * Dodatkowe pomiary pacjenta (bez grupy sesji) dopisywane jako wykonane.
     */
    private fun buildDayPlan(): List<DayPlanItem> {
        val nowTime = LocalTime.now()
        val medItems = doses.map { dose ->
            val done = dose.status == DoseStatus.TAKEN
            DayPlanItem(
                id = "dose-${dose.id}",
                time = dose.time,
                title = dose.drugName,
                detail = dose.doseLabel,
                done = done,
                kind = DayPlanKind.MED,
                completedAtMs = dose.takenAtMs,
                tone = planTone(
                    done = done,
                    scheduled = dose.time,
                    completedAtMs = dose.takenAtMs,
                    nowTime = nowTime,
                ),
            )
        }
        val todaySession = sessions.firstOrNull {
            it.date == today && it.kind == PlannedSessionKind.REHAB_INTERVAL
        }
        val sessionItem = todaySession?.let { s ->
            val done = s.status == PlannedSessionStatus.DONE
            DayPlanItem(
                id = "session",
                time = s.time,
                title = "Sesja rehabilitacji",
                detail = s.title,
                done = done || s.status == PlannedSessionStatus.DISQUALIFIED,
                kind = DayPlanKind.SESSION,
                completedAtMs = s.completedAtMs,
                tone = when (s.status) {
                    PlannedSessionStatus.DISQUALIFIED -> DayPlanTone.MISSED
                    PlannedSessionStatus.CANCELLED, PlannedSessionStatus.MISSED -> DayPlanTone.MISSED
                    else -> planTone(
                        done = done,
                        scheduled = s.time,
                        completedAtMs = s.completedAtMs,
                        nowTime = nowTime,
                    )
                },
            )
        }
        val dayStart = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val dayEnd = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val measureItems = measurements
            .filter { it.sessionGroupId == null && it.measuredAtMs in dayStart until dayEnd }
            .filter { it.kind == VitalKind.BLOOD_PRESSURE || it.kind == VitalKind.WEIGHT || it.kind == VitalKind.PULSE }
            .map { m ->
                val t = java.time.Instant.ofEpochMilli(m.measuredAtMs).atZone(zone).toLocalTime()
                DayPlanItem(
                    id = "meas-${m.id}",
                    time = t,
                    title = m.label,
                    detail = m.valueText,
                    done = true,
                    kind = DayPlanKind.MEASUREMENT,
                    completedAtMs = m.measuredAtMs,
                    tone = DayPlanTone.ON_TIME,
                )
            }
        return (medItems + listOfNotNull(sessionItem) + measureItems).sortedBy { it.time }
    }

    private fun planTone(
        done: Boolean,
        scheduled: LocalTime,
        completedAtMs: Long?,
        nowTime: LocalTime,
    ): DayPlanTone {
        val deadline = today.atTime(scheduled).atZone(zone).toInstant().toEpochMilli()
        return when {
            done -> {
                val at = completedAtMs ?: deadline
                if (at <= deadline) DayPlanTone.ON_TIME else DayPlanTone.LATE
            }
            nowTime.isAfter(scheduled) -> DayPlanTone.MISSED
            else -> DayPlanTone.UPCOMING
        }
    }

    private fun persist() {
        val root = JSONObject()
        root.put("medications", JSONArray().also { arr ->
            medications.forEach { m ->
                arr.put(
                    JSONObject()
                        .put("id", m.id)
                        .put("name", m.name)
                        .put("doseLabel", m.doseLabel)
                        .put("scheduleNote", m.scheduleNote)
                        .put("times", JSONArray(m.times)),
                )
            }
        })
        root.put("doses", JSONArray().also { arr ->
            doses.forEach { d ->
                arr.put(
                    JSONObject()
                        .put("id", d.id)
                        .put("drugName", d.drugName)
                        .put("doseLabel", d.doseLabel)
                        .put("time", d.time.toString())
                        .put("status", d.status.name)
                        .put("takenAtMs", d.takenAtMs ?: JSONObject.NULL),
                )
            }
        })
        root.put("diseases", JSONArray().also { arr ->
            diseases.forEach { d ->
                arr.put(
                    JSONObject()
                        .put("id", d.id)
                        .put("name", d.name)
                        .put("icd", d.icd)
                        .put("codingSystem", d.codingSystem)
                        .put("status", d.status.name)
                        .put("diagnosedLabel", d.diagnosedLabel)
                        .put("note", d.note),
                )
            }
        })
        prefs.edit().putString(KEY_STATE, root.toString()).apply()
    }

    private fun loadPersisted() {
        val raw = prefs.getString(KEY_STATE, null) ?: return
        runCatching {
            val root = JSONObject(raw)
            if (root.has("medications")) {
                val arr = root.getJSONArray("medications")
                medications = buildList {
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        val timesArr = o.optJSONArray("times")
                        val times = buildList {
                            if (timesArr != null) {
                                for (j in 0 until timesArr.length()) add(timesArr.getString(j))
                            }
                        }
                        add(
                            Medication(
                                id = o.getString("id"),
                                name = o.getString("name"),
                                doseLabel = o.getString("doseLabel"),
                                scheduleNote = o.optString("scheduleNote"),
                                times = times.ifEmpty { parseTimes(o.optString("scheduleNote")) },
                            ),
                        )
                    }
                }
            }
            if (root.has("doses")) {
                val arr = root.getJSONArray("doses")
                doses = buildList {
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        add(
                            MedDose(
                                id = o.getString("id"),
                                drugName = o.getString("drugName"),
                                doseLabel = o.getString("doseLabel"),
                                time = LocalTime.parse(o.getString("time")),
                                status = DoseStatus.valueOf(o.getString("status")),
                                takenAtMs = o.optLong("takenAtMs").takeIf { o.has("takenAtMs") && !o.isNull("takenAtMs") && it > 0 },
                            ),
                        )
                    }
                }
            }
            if (root.has("diseases")) {
                val arr = root.getJSONArray("diseases")
                diseases = buildList {
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        add(
                            Disease(
                                id = o.getString("id"),
                                name = o.getString("name"),
                                icd = o.optString("icd"),
                                codingSystem = o.optString("codingSystem", "ICD-10")
                                    .ifBlank { "ICD-10" },
                                status = DiseaseStatus.valueOf(o.getString("status")),
                                diagnosedLabel = o.optString("diagnosedLabel"),
                                note = o.optString("note"),
                            ),
                        )
                    }
                }
            }
        }
    }

    private fun normalizeTime(raw: String): String {
        val parts = raw.trim().split(":")
        val h = parts.getOrNull(0)?.toIntOrNull() ?: return raw
        val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
        return "%02d:%02d".format(h.coerceIn(0, 23), m.coerceIn(0, 59))
    }

    private fun parseTimes(scheduleNote: String): List<String> =
        Regex("\\b(\\d{1,2}:\\d{2})\\b").findAll(scheduleNote).map { it.groupValues[1] }.toList()

    private fun at(daysAgo: Long, hour: Int, minute: Int = 0): Long =
        today.minusDays(daysAgo).atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

    private fun defaultMeasurements(): List<ClinicMeasurement> {
        val sessionDone = "sess-done"
        val sessionTitle = "Sesja rehab · trening sekwencyjny"
        val sessionOlder = "sess-older"
        val sessionOlderTitle = "Sesja rehab · trening sekwencyjny"
        return listOf(
            ClinicMeasurement("m1", VitalKind.BLOOD_PRESSURE, "Ciśnienie", "128/82 · 72/min", at(0, 8, 10)),
            ClinicMeasurement("m2", VitalKind.WEIGHT, "Masa", "78.2 kg", at(0, 8, 5)),
            ClinicMeasurement("m6", VitalKind.SPO2, "SpO₂", "97%", at(3, 10, 0)),
            ClinicMeasurement(
                "s3-bp", VitalKind.BLOOD_PRESSURE, "Ciśnienie przed", "126/80 · 70/min",
                at(2, 10, 5), sessionGroupId = sessionDone, sessionGroupTitle = sessionTitle,
            ),
            ClinicMeasurement(
                "s3-w", VitalKind.WEIGHT, "Masa", "78.4 kg",
                at(2, 10, 8), sessionGroupId = sessionDone, sessionGroupTitle = sessionTitle,
            ),
            ClinicMeasurement(
                "s3-ecg0", VitalKind.ECG, "EKG spoczynkowe", "SCP · 5 s",
                at(2, 10, 12), sessionGroupId = sessionDone, sessionGroupTitle = sessionTitle,
            ),
            ClinicMeasurement(
                "s3-p1", VitalKind.PULSE, "Tętno wysiłek 1", "98 bpm",
                at(2, 10, 14), sessionGroupId = sessionDone, sessionGroupTitle = sessionTitle,
            ),
            ClinicMeasurement(
                "s3-ecg1", VitalKind.ECG, "EKG szczyt 1", "SCP · 5 s",
                at(2, 10, 16), sessionGroupId = sessionDone, sessionGroupTitle = sessionTitle,
            ),
            ClinicMeasurement(
                "s3-p2", VitalKind.PULSE, "Tętno wysiłek 2", "104 bpm",
                at(2, 10, 18), sessionGroupId = sessionDone, sessionGroupTitle = sessionTitle,
            ),
            ClinicMeasurement(
                "s3-ecg2", VitalKind.ECG, "EKG szczyt 2", "SCP · 5 s",
                at(2, 10, 20), sessionGroupId = sessionDone, sessionGroupTitle = sessionTitle,
            ),
            ClinicMeasurement(
                "s4-bp", VitalKind.BLOOD_PRESSURE, "Ciśnienie przed", "132/84 · 74/min",
                at(5, 10, 5), sessionGroupId = sessionOlder, sessionGroupTitle = sessionOlderTitle,
            ),
            ClinicMeasurement(
                "s4-ecg0", VitalKind.ECG, "EKG spoczynkowe", "SCP · 5 s",
                at(5, 10, 10), sessionGroupId = sessionOlder, sessionGroupTitle = sessionOlderTitle,
            ),
            ClinicMeasurement(
                "s4-p1", VitalKind.PULSE, "Tętno wysiłek", "92 bpm",
                at(5, 10, 12), sessionGroupId = sessionOlder, sessionGroupTitle = sessionOlderTitle,
            ),
            ClinicMeasurement(
                "s4-ecg1", VitalKind.ECG, "EKG szczyt", "SCP · 5 s",
                at(5, 10, 14), sessionGroupId = sessionOlder, sessionGroupTitle = sessionOlderTitle,
            ),
            ClinicMeasurement("m7", VitalKind.BLOOD_PRESSURE, "Ciśnienie", "126/80 · 70/min", at(4, 8, 20)),
            ClinicMeasurement("m3", VitalKind.PULSE, "Tętno", "68 bpm", at(1, 9, 0)),
        )
    }

    private fun defaultMedications(): List<Medication> = emptyList()

    private fun defaultDoses(): List<MedDose> = emptyList()

    private fun defaultDiseases(): List<Disease> = emptyList()

    private fun defaultSessions(): List<PlannedSession> {
        val base = mutableListOf(
            PlannedSession(
                "s0", today, LocalTime.of(10, 0),
                PlannedSessionKind.REHAB_INTERVAL, "Trening sekwencyjny",
                PlannedSessionStatus.SCHEDULED,
            ),
            PlannedSession(
                "s1", today.plusDays(2), LocalTime.of(10, 0),
                PlannedSessionKind.REHAB_INTERVAL, "Trening sekwencyjny",
                PlannedSessionStatus.SCHEDULED,
            ),
            PlannedSession(
                "s2", today.plusDays(4), LocalTime.of(11, 30),
                PlannedSessionKind.ECG_CHECK, "Kontrola EKG",
                PlannedSessionStatus.SCHEDULED,
            ),
            PlannedSession(
                "s3", today.minusDays(2), LocalTime.of(10, 0),
                PlannedSessionKind.REHAB_INTERVAL, "Trening sekwencyjny",
                PlannedSessionStatus.DONE,
            ),
            PlannedSession(
                "s-dq", today.minusDays(5), LocalTime.of(10, 0),
                PlannedSessionKind.REHAB_INTERVAL, "Trening sekwencyjny",
                PlannedSessionStatus.DISQUALIFIED,
            ),
            PlannedSession(
                "s-cancel", today.minusDays(8), LocalTime.of(10, 0),
                PlannedSessionKind.REHAB_INTERVAL, "Trening sekwencyjny",
                PlannedSessionStatus.CANCELLED,
            ),
            PlannedSession(
                "s4", today.plusDays(7), LocalTime.of(9, 0),
                PlannedSessionKind.CONSULT, "Konsultacja kardiologiczna",
                PlannedSessionStatus.SCHEDULED,
            ),
        )
        for (d in 1..28 step 3) {
            val date = today.withDayOfMonth(d.coerceAtMost(today.lengthOfMonth()))
            if (base.none { it.date == date }) {
                val status = when {
                    date.isBefore(today) && d % 6 == 0 -> PlannedSessionStatus.DISQUALIFIED
                    date.isBefore(today) -> PlannedSessionStatus.DONE
                    else -> PlannedSessionStatus.SCHEDULED
                }
                base += PlannedSession(
                    "sx$d", date, LocalTime.of(10, 0),
                    PlannedSessionKind.REHAB_INTERVAL, "Trening sekwencyjny",
                    status,
                )
            }
        }
        return base
    }

    companion object {
        private const val PREFS = "clinic_demo"
        /** v2 — puste leki/choroby (bez seedów testowych). */
        private const val KEY_STATE = "state_v2"
    }
}
