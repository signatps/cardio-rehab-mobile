package pl.cardioscp.rehab.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pl.cardioscp.rehab.ble.BleMeasureController
import pl.cardioscp.rehab.bluetooth.BluetoothPermissionHelper
import pl.cardioscp.rehab.bluetooth.BondedEcgDevice
import pl.cardioscp.rehab.bluetooth.EhoMiniConnectionState
import pl.cardioscp.rehab.bluetooth.ProtocolSessionController
import pl.cardioscp.rehab.bluetooth.SppEhoMiniDeviceClient
import pl.cardioscp.rehab.bluetooth.protocol.ElectrodeStatus
import pl.cardioscp.rehab.CardioRehabApp
import pl.cardioscp.rehab.clinic.ClinicDemoStore
import pl.cardioscp.rehab.clinic.ClinicSnapshot
import pl.cardioscp.rehab.clinic.DiseaseStatus
import pl.cardioscp.rehab.clinic.DoseStatus
import pl.cardioscp.rehab.clinic.VitalKind
import pl.cardioscp.rehab.clinic.WelcomePhrase
import pl.cardioscp.rehab.host.MedReminderNotifier
import pl.cardioscp.rehab.host.MedReminderScheduler
import pl.cardioscp.rehab.scp.ScpEcgParser
import pl.cardioscp.rehab.scp.ScpEcgRecording
import pl.cardioscp.rehab.scp.ScpRecording
import pl.cardioscp.rehab.scp.ScpRecordingStore
import pl.cardioscp.rehab.session.ArchivedEcgSlot
import pl.cardioscp.rehab.session.ArchivedRehabSession
import pl.cardioscp.rehab.session.RehabSessionArchive
import pl.cardioscp.rehab.session.RehabSessionEngine
import pl.cardioscp.rehab.session.RehabSessionState
import pl.cardioscp.rehab.session.SessionDayGate
import pl.cardioscp.rehab.session.SessionEcgEntry
import pl.cardioscp.rehab.session.TrainingPlan
import java.time.LocalDate

data class HomeUiState(
    val connection: EhoMiniConnectionState = EhoMiniConnectionState.Idle,
    val bondedDevices: List<BondedEcgDevice> = emptyList(),
    val lastPulseBpm: Int? = null,
    val pulseSampleCount: Int = 0,
    val electrodeWarning: String? = null,
    val sessionLabel: String? = null,
    val recordings: List<ScpRecording> = emptyList(),
    val lastSavedScpName: String? = null,
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val deviceClient = SppEhoMiniDeviceClient(application)
    private val sessionController = ProtocolSessionController(deviceClient, viewModelScope)
    private val recordingStore = ScpRecordingStore(application)
    val bleMeasure = BleMeasureController(application)

    private val clinicStore = ClinicDemoStore(application)
    private val _clinic = MutableStateFlow(clinicStore.snapshot())
    val clinic: StateFlow<ClinicSnapshot> = _clinic

    private val sessionArchive = RehabSessionArchive(application, recordingStore)
    private val sessionDayGate = SessionDayGate(application)
    private val _archivedSessions = MutableStateFlow<List<ArchivedRehabSession>>(emptyList())
    val archivedSessions: StateFlow<List<ArchivedRehabSession>> = _archivedSessions

    private val _showRehabPinDialog = MutableStateFlow(false)
    val showRehabPinDialog: StateFlow<Boolean> = _showRehabPinDialog
    private var pendingRehabOpen: (() -> Unit)? = null
    private var pendingExtraViaPin = false

    private var dayPlanWelcomeSpoken = false
    private val voiceGreeting
        get() = (getApplication<Application>() as? CardioRehabApp)?.voiceGreeting

    private val rehabEngine = RehabSessionEngine(
        scope = viewModelScope,
        deviceClient = deviceClient,
        sessionController = sessionController,
        recordingStore = recordingStore,
        bleMeasure = bleMeasure,
        userIdProvider = {
            val connected = deviceClient.connectionState.value as? EhoMiniConnectionState.Connected
            bondedDevices.value
                .firstOrNull { it.address == connected?.address }
                ?.serialSuffix
                ?: connected?.deviceName?.takeLast(6)
                ?: "000000"
        },
        onOpenViewer = { openRecording(it) },
    )
    val rehabSession: StateFlow<RehabSessionState?> = rehabEngine.state
    val electrodeStatus: StateFlow<ElectrodeStatus> = sessionController.electrodeStatus

    private val permissionsOk = MutableStateFlow(
        BluetoothPermissionHelper.hasAllPermissions(application),
    )
    private val bondedDevices = MutableStateFlow<List<BondedEcgDevice>>(emptyList())
    private val localError = MutableStateFlow<String?>(null)
    private val sessionLabel = MutableStateFlow<String?>(null)
    private val recordings = MutableStateFlow(recordingStore.list())
    private val lastSavedScpName = MutableStateFlow<String?>(null)

    private val _viewerRecording = MutableStateFlow<ScpEcgRecording?>(null)
    val viewerRecording: StateFlow<ScpEcgRecording?> = _viewerRecording
    private val _viewerError = MutableStateFlow<String?>(null)
    val viewerError: StateFlow<String?> = _viewerError
    private val _viewerTitle = MutableStateFlow("EKG")
    val viewerTitle: StateFlow<String> = _viewerTitle

    /** Consumed by UI to auto-open waveform after a successful download. */
    private val _openViewerRequest = MutableStateFlow(0)
    val openViewerRequest: StateFlow<Int> = _openViewerRequest

    private val connectionSlice = combine(
        deviceClient.connectionState,
        permissionsOk,
        bondedDevices,
    ) { connection, granted, bonded ->
        Triple(connection, granted, bonded)
    }

    private val pulseSlice = combine(
        sessionController.lastPulseBpm,
        sessionController.pulseSampleCount,
        sessionController.electrodeWarning,
    ) { pulse, count, electrode ->
        Triple(pulse, count, electrode)
    }

    private val sessionSlice = combine(
        localError,
        sessionLabel,
        recordings,
        lastSavedScpName,
    ) { error, session, recs, saved ->
        SessionBits(error, session, recs, saved)
    }

    val uiState: StateFlow<HomeUiState> = combine(
        connectionSlice,
        pulseSlice,
        sessionSlice,
    ) { conn, pulse, session ->
        val (connection, granted, bonded) = conn
        val (bpm, pulseCount, electrode) = pulse
        val effective = when {
            !granted -> EhoMiniConnectionState.PermissionsRequired
            session.error != null &&
                (connection is EhoMiniConnectionState.Idle ||
                    connection is EhoMiniConnectionState.Error) ->
                EhoMiniConnectionState.Error(session.error)
            else -> connection
        }
        HomeUiState(
            connection = effective,
            bondedDevices = bonded,
            lastPulseBpm = bpm,
            pulseSampleCount = pulseCount,
            electrodeWarning = electrode,
            sessionLabel = session.session,
            recordings = session.recordings,
            lastSavedScpName = session.savedName,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        HomeUiState(
            connection = if (permissionsOk.value) {
                EhoMiniConnectionState.Idle
            } else {
                EhoMiniConnectionState.PermissionsRequired
            },
            recordings = recordings.value,
        ),
    )

    private data class SessionBits(
        val error: String?,
        val session: String?,
        val recordings: List<ScpRecording>,
        val savedName: String?,
    )

    init {
        // Po północy odśwież plan dnia / dawki / sesję bez restartu aplikacji.
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(30_000)
                publishClinic()
            }
        }
        viewModelScope.launch {
            sessionController.status.collect { status ->
                sessionLabel.value = when (status) {
                    ProtocolSessionController.Status.Idle -> null
                    is ProtocolSessionController.Status.Running -> when (status.scenario) {
                        ProtocolSessionController.Scenario.PULSE ->
                            "Scenariusz 2: odbiór pulsu…"
                        ProtocolSessionController.Scenario.ECG_OFFLINE_CREATE ->
                            "Scenariusz 3: zapis ECG Offline na urządzeniu…"
                        ProtocolSessionController.Scenario.ECG_ONLINE_SESSION ->
                            "EKG Online — ciągły strumień, fragmenty z taśmy…"
                        ProtocolSessionController.Scenario.SCP_DOWNLOAD ->
                            "Pobieranie całego pliku SCP…"
                    }
                    is ProtocolSessionController.Status.Info -> status.message
                    is ProtocolSessionController.Status.Failed -> "Sesja: ${status.reason}"
                    ProtocolSessionController.Status.Finished -> "Scenariusz zakończony"
                }
            }
        }
        viewModelScope.launch {
            sessionController.lastScpBytes.collect { bytes ->
                if (bytes == null) return@collect
                // Sesja rehabilitacji sama zapisuje SCP slotów treningowych.
                if (rehabEngine.suppressAutoViewer) {
                    recordings.value = recordingStore.list()
                    return@collect
                }
                val serial = (deviceClient.connectionState.value as? EhoMiniConnectionState.Connected)
                    ?.deviceName
                    ?.takeLast(6)
                runCatching {
                    recordingStore.saveComplete(bytes, serial)
                }.onSuccess { saved ->
                    lastSavedScpName.value = saved.displayName
                    recordings.value = recordingStore.list()
                    sessionLabel.value = "Zapisano cały SCP: ${saved.displayName}"
                    openRecording(saved)
                    _openViewerRequest.value = _openViewerRequest.value + 1
                }.onFailure {
                    sessionLabel.value = "Błąd zapisu SCP: ${it.message}"
                }
            }
        }
        refreshPermissions()
        refreshEcgArchive()
        MedReminderScheduler.reschedule(application, clinicStore)
    }

    /** Wejście w sesję rehab — przy zużytym slocie dnia pokazuje PIN. */
    fun requestOpenRehab(open: () -> Unit) {
        publishClinic()
        val day = LocalDate.now()
        val slotUsed = clinicStore.isTodayRehabSlotUsed(day) || hasArchivedSessionToday(day)
        if (sessionDayGate.requiresPinForNewSession(day, slotUsed)) {
            pendingRehabOpen = open
            _showRehabPinDialog.value = true
            return
        }
        pendingExtraViaPin = sessionDayGate.isExtraGranted(day)
        open()
    }

    fun dismissRehabPinDialog() {
        _showRehabPinDialog.value = false
        pendingRehabOpen = null
    }

    fun submitRehabPin(pin: String): Boolean {
        if (pin != sessionDayGate.defaultPin()) return false
        val day = LocalDate.now()
        sessionDayGate.grantExtraSession(day)
        clinicStore.scheduleExtraRehabSessionAfterPin(day)
        publishClinic()
        pendingExtraViaPin = true
        _showRehabPinDialog.value = false
        val open = pendingRehabOpen
        pendingRehabOpen = null
        open?.invoke()
        return true
    }

    private fun hasArchivedSessionToday(day: LocalDate): Boolean {
        val zone = java.time.ZoneId.systemDefault()
        return _archivedSessions.value.any {
            java.time.Instant.ofEpochMilli(it.startedAtMs).atZone(zone).toLocalDate() == day
        }
    }

    fun startRehabSession(
        includeWeight: Boolean = true,
        heartRateLimits: List<pl.cardioscp.rehab.session.CycleHeartRateLimit> =
            pl.cardioscp.rehab.session.HeartRateCoach.defaultLimits(2),
        ecgMode: pl.cardioscp.rehab.session.EcgAcquisitionMode =
            pl.cardioscp.rehab.session.EcgAcquisitionMode.OFFLINE,
    ) {
        viewModelScope.launch {
            val day = LocalDate.now()
            val slotUsed = clinicStore.isTodayRehabSlotUsed(day) || hasArchivedSessionToday(day)
            if (sessionDayGate.requiresPinForNewSession(day, slotUsed)) {
                rehabEngine.start(includeWeight = includeWeight, plan = TrainingPlan(), ecgMode = ecgMode)
                rehabEngine.setGateError(
                    "Dziś sesja już się odbyła. Odblokuj PIN-em opiekuna (pulpit → Start sesji).",
                )
                return@launch
            }
            if (pendingExtraViaPin || sessionDayGate.isExtraGranted(day)) {
                sessionDayGate.markExtraSessionStarted(day)
                pendingExtraViaPin = true
            }
            val plan = TrainingPlan(
                cycles = 2,
                exerciseSec = 30,
                restSec = 30,
                acquireSec = 15,
                admissionWaitSec = 10,
                heartRateLimits = heartRateLimits,
            )
            if (deviceClient.connectionState.value !is EhoMiniConnectionState.Connected) {
                rehabEngine.start(includeWeight = includeWeight, plan = plan, ecgMode = ecgMode)
                rehabEngine.setGateError("Połącz najpierw EHO-Mini (SPP).")
                return@launch
            }
            val electrodes = runCatching { sessionController.refreshElectrodes() }
                .getOrDefault(sessionController.electrodeStatus.value)
            if (!electrodes.allAttached) {
                rehabEngine.start(includeWeight = includeWeight, plan = plan, ecgMode = ecgMode)
                rehabEngine.setGateError(
                    "Nie można rozpocząć sesji — ${electrodes.summaryPl}",
                )
                return@launch
            }
            rehabEngine.start(includeWeight = includeWeight, plan = plan, ecgMode = ecgMode)
            rehabEngine.beginBaselineEcg()
        }
    }

    fun confirmStartTraining() = rehabEngine.confirmStartTraining()

    fun refreshElectrodes() {
        viewModelScope.launch {
            runCatching { sessionController.refreshElectrodes() }
        }
    }

    /** TTS coachingu tętna (PRZYSPIESZ / ZWOLNIJ) — bez spamu przy tym samym cue. */
    fun speakHeartRateCue(phrase: String) {
        voiceGreeting?.speak(phrase)
    }

    /** Głosowe komunikaty faz sesji (ćwiczenie, EKG, pomiary, ankieta…). */
    fun speakCoachMessage(phrase: String) {
        val trimmed = phrase.trim()
        if (trimmed.isEmpty()) return
        voiceGreeting?.speak(trimmed)
    }

    fun cancelRehabSession() = rehabEngine.cancel()
    fun beginBaselineEcg() = rehabEngine.beginBaselineEcg()
    fun retryBpMeasure() = rehabEngine.startBpMeasure()
    fun retryWeightMeasure() = rehabEngine.startWeightMeasure()

    /** Pomiar ciśnienia niezależny od sesji rehab (pulpit). */
    fun measureBpStandalone() {
        bleMeasure.startMeasure(
            type = pl.cardioscp.rehab.ble.VitalMeasureType.BLOOD_PRESSURE,
            onSaved = { reading, note ->
                recordClinicMeasurement(
                    VitalKind.BLOOD_PRESSURE,
                    "Ciśnienie",
                    reading.summary,
                    note = note,
                )
            },
        )
    }

    /** Pomiar wagi niezależny od sesji rehab (pulpit). */
    fun measureWeightStandalone() {
        bleMeasure.startMeasure(
            type = pl.cardioscp.rehab.ble.VitalMeasureType.WEIGHT,
            onSaved = { reading, note ->
                recordClinicMeasurement(
                    VitalKind.WEIGHT,
                    "Masa",
                    reading.summary,
                    note = note,
                )
            },
        )
    }

    fun simulateBpMeasure() {
        if (!bleMeasure.measurePopupOpen) measureBpStandalone()
        bleMeasure.simulateMeasure()
        recordClinicMeasurement(
            VitalKind.BLOOD_PRESSURE,
            "Ciśnienie",
            "128/82 · 72/min",
            note = "symulacja",
        )
    }
    fun simulateWeightMeasure() {
        if (!bleMeasure.measurePopupOpen) measureWeightStandalone()
        bleMeasure.simulateMeasure()
        recordClinicMeasurement(
            VitalKind.WEIGHT,
            "Masa",
            "78.2 kg",
            note = "symulacja",
        )
    }

    /** Liczba alertów na pulpit (dawki zaległe + ciśnienie poza WHO). */
    fun dashboardAlertCount(): Int {
        val snap = clinicStore.snapshot()
        val now = java.time.LocalTime.now()
        var n = snap.todayDoses.count {
            it.needsAction && !it.time.isAfter(now)
        }
        val bp = snap.measurements.firstOrNull { it.kind == VitalKind.BLOOD_PRESSURE }
        val m = bp?.valueText?.let { Regex("""(\d+)\s*/\s*(\d+)""").find(it) }
        if (m != null) {
            val band = pl.cardioscp.rehab.ble.BpWho.band(
                m.groupValues[1].toIntOrNull(),
                m.groupValues[2].toIntOrNull(),
            )
            if (band == pl.cardioscp.rehab.ble.BpWho.Band.HIGH_NORMAL ||
                band == pl.cardioscp.rehab.ble.BpWho.Band.GRADE1 ||
                band == pl.cardioscp.rehab.ble.BpWho.Band.GRADE2 ||
                band == pl.cardioscp.rehab.ble.BpWho.Band.GRADE3 ||
                band == pl.cardioscp.rehab.ble.BpWho.Band.LOW
            ) {
                n++
            }
        }
        return n
    }

    fun markDoseTaken(id: String) {
        confirmDoseTaken(id)
    }

    fun confirmDoseTaken(id: String) {
        clinicStore.confirmDose(id, DoseStatus.TAKEN)
        publishClinic()
        MedReminderScheduler.reschedule(getApplication(), clinicStore)
    }

    fun skipDose(id: String, reason: String) {
        clinicStore.confirmDose(id, DoseStatus.SKIPPED, skipReason = reason)
        publishClinic()
        MedReminderScheduler.reschedule(getApplication(), clinicStore)
    }

    fun snoozeDose(id: String, hhmm: String) {
        val normalized = hhmm.trim()
        val time = runCatching {
            val parts = normalized.split(':')
            java.time.LocalTime.of(
                parts[0].toInt().coerceIn(0, 23),
                parts.getOrNull(1)?.toInt()?.coerceIn(0, 59) ?: 0,
            )
        }.getOrNull() ?: return
        clinicStore.confirmDose(id, DoseStatus.SNOOZED, newTime = time)
        publishClinic()
        MedReminderScheduler.reschedule(getApplication(), clinicStore)
    }

    private fun publishClinic() {
        val before = _clinic.value.planDate
        _clinic.value = clinicStore.snapshot()
        if (_clinic.value.planDate != before) dayPlanWelcomeSpoken = false
    }

    fun addMedication(name: String, dose: String, times: List<String>, note: String) {
        clinicStore.addMedication(name, dose, times, note)
        publishClinic()
        MedReminderScheduler.reschedule(getApplication(), clinicStore)
        maybeNotifyDueMed()
    }

    fun removeMedication(id: String) {
        if (!clinicStore.removeMedication(id)) return
        publishClinic()
        MedReminderScheduler.reschedule(getApplication(), clinicStore)
    }

    fun addDisease(
        name: String,
        icd: String,
        status: DiseaseStatus,
        diagnosed: String,
        note: String,
        codingSystem: String = "ICD-10",
    ) {
        clinicStore.addDisease(name, icd, status, diagnosed, note, codingSystem)
        _clinic.value = clinicStore.snapshot()
    }

    fun setDiseaseStatus(id: String, status: DiseaseStatus) {
        clinicStore.updateDiseaseStatus(id, status)
        _clinic.value = clinicStore.snapshot()
    }

    fun removeDisease(id: String) {
        clinicStore.removeDisease(id)
        _clinic.value = clinicStore.snapshot()
    }

    fun recordClinicMeasurement(
        kind: VitalKind,
        label: String,
        valueText: String,
        note: String = "",
    ) {
        clinicStore.addMeasurement(kind, label, valueText, note)
        _clinic.value = clinicStore.snapshot()
    }

    fun refreshClinic() {
        _clinic.value = clinicStore.snapshot()
    }

    fun refreshEcgArchive() {
        sessionArchive.ensureSeedFromRecordings()
        _archivedSessions.value = sessionArchive.list()
    }

    fun sessionArchiveDateLabel(session: ArchivedRehabSession): String =
        sessionArchive.sessionDateLabel(session)

    fun openArchivedEcg(session: ArchivedRehabSession, slot: ArchivedEcgSlot) {
        _viewerTitle.value = "Sesja ${session.sessionNumber} · ${slot.label}"
        val rec = sessionArchive.resolveRecording(slot)
        if (rec == null) {
            _viewerRecording.value = null
            _viewerError.value = "Brak pliku SCP: ${slot.fileName}"
            return
        }
        openRecording(rec)
    }

    /** Podgrzewa TTS (np. na splashu). */
    fun warmVoiceGreeting() {
        voiceGreeting?.warmUp()
    }

    /**
     * Powitanie głosowe z pulpitu — raz na sesję aplikacji.
     * Gdy dzisiejsza sesja rehab jest już DONE, mówi tylko o lekach.
     */
    fun speakDayPlanWelcome() {
        if (dayPlanWelcomeSpoken) return
        dayPlanWelcomeSpoken = true
        val phrase = WelcomePhrase.buildFromClinic(_clinic.value)
        voiceGreeting?.warmUp()
        voiceGreeting?.speak(phrase)
    }

    /** Po zakończonej sesji rehab — archiwum EKG + plan dnia bez sesji. */
    fun markTodayRehabSessionDone() {
        val live = rehabEngine.state.value
        val disqualified = live?.step == pl.cardioscp.rehab.session.RehabStep.SUMMARY &&
            live.statusMessage?.contains("dyskwalifik", ignoreCase = true) == true
        if (live != null && live.ecgEntries.isNotEmpty()) {
            val surveyOutcome = pl.cardioscp.rehab.session.DefaultRehabSurvey.evaluate(live.surveyAnswers)
            sessionArchive.archiveFromLiveSession(
                entries = live.ecgEntries,
                startedAtMs = live.startedAtMs,
                bloodPressureSummary = live.vitals.bloodPressure?.summary,
                weightSummary = live.vitals.weight?.summary,
                surveyPassed = when (surveyOutcome) {
                    pl.cardioscp.rehab.session.SurveyOutcome.PASS -> true
                    pl.cardioscp.rehab.session.SurveyOutcome.DISQUALIFIED -> false
                    pl.cardioscp.rehab.session.SurveyOutcome.INCOMPLETE -> null
                },
                surveyAnswers = live.surveyAnswers,
                cycleHrSummaries = live.cycleHrSummaries,
                extraViaPin = pendingExtraViaPin || sessionDayGate.isCurrentStartExtra(),
            )
            refreshEcgArchive()
        }
        if (disqualified ||
            live?.let {
                pl.cardioscp.rehab.session.DefaultRehabSurvey.evaluate(it.surveyAnswers) ==
                    pl.cardioscp.rehab.session.SurveyOutcome.DISQUALIFIED
            } == true
        ) {
            clinicStore.markTodayRehabSessionDisqualified()
        } else {
            clinicStore.markTodayRehabSessionDone()
        }
        pendingExtraViaPin = false
        publishClinic()
    }

    /** Najnowsza zarchiwizowana sesja z dziś — do kafelka na pulpicie. */
    fun todayArchivedSession(): ArchivedRehabSession? {
        val zone = java.time.ZoneId.systemDefault()
        val start = java.time.LocalDate.now().atStartOfDay(zone).toInstant().toEpochMilli()
        val end = java.time.LocalDate.now().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return sessionArchive.latestForDay(start, end)
            ?: _archivedSessions.value.firstOrNull {
                val d = java.time.Instant.ofEpochMilli(it.startedAtMs).atZone(zone).toLocalDate()
                d == java.time.LocalDate.now()
            }
            ?: _archivedSessions.value.firstOrNull()
    }

    private fun maybeNotifyDueMed() {
        val app = getApplication<Application>()
        if (!MedReminderNotifier.canPost(app)) return
        clinicStore.dueMedicationReminderPayload()?.let { (title, body) ->
            MedReminderNotifier.notifyMedicationReminder(app, title, body)
        }
    }
    fun skipWeight() = rehabEngine.skipWeight()
    fun answerSurvey(questionId: String, yes: Boolean) = rehabEngine.answerSurvey(questionId, yes)
    fun submitSurvey() = rehabEngine.submitSurvey()
    fun finishDisqualified() {
        rehabEngine.finishDisqualified()
    }
    fun reportEcgEvent() = rehabEngine.reportEcgEvent()
    fun confirmEcgEvent(endTraining: Boolean) = rehabEngine.confirmEventAction(endTraining)
    fun dismissEcgEvent() = rehabEngine.dismissEventPause()
    fun openSessionEcg(entry: SessionEcgEntry) {
        rehabEngine.openSessionEcg(entry)
        _openViewerRequest.value = _openViewerRequest.value + 1
    }

    fun refreshPermissions() {
        permissionsOk.value = BluetoothPermissionHelper.hasAllPermissions(getApplication())
        if (permissionsOk.value) {
            refreshBondedDevices()
        }
    }

    fun refreshBondedDevices() {
        if (!BluetoothPermissionHelper.hasAllPermissions(getApplication())) return
        viewModelScope.launch {
            runCatching { deviceClient.listBondedCandidates() }
                .onSuccess { bondedDevices.value = it }
                .onFailure {
                    localError.value = it.message ?: "Nie udało się odczytać sparowanych urządzeń"
                }
        }
    }

    fun refreshRecordings() {
        recordings.value = recordingStore.list()
    }

    fun onConnectClicked() {
        localError.value = null
        if (!BluetoothPermissionHelper.hasAllPermissions(getApplication())) {
            permissionsOk.value = false
            return
        }
        viewModelScope.launch {
            val candidates = runCatching { deviceClient.listBondedCandidates() }
                .onFailure {
                    localError.value = it.message ?: "Błąd Bluetooth"
                }
                .getOrNull() ?: return@launch

            bondedDevices.value = candidates
            when {
                candidates.isEmpty() -> {
                    localError.value =
                        "Brak sparowanego PRO_PLUS_ECG_******. Sparuj urządzenie w ustawieniach Bluetooth Androida, potem połącz ponownie."
                }
                else -> {
                    val target = candidates.first()
                    runCatching { deviceClient.connect(target.address) }
                        .onSuccess {
                            sessionController.startElectrodePolling()
                            runCatching { sessionController.refreshElectrodes() }
                        }
                        .onFailure {
                            localError.value = it.message
                                ?: "Nie udało się połączyć z ${target.name}"
                        }
                }
            }
        }
    }

    fun onDisconnectClicked() {
        localError.value = null
        sessionController.stopElectrodePolling()
        sessionController.resetElectrodes()
        sessionController.stop()
        viewModelScope.launch {
            runCatching { deviceClient.disconnect() }
        }
    }

    fun onStartPulseScenario() {
        if (deviceClient.connectionState.value !is EhoMiniConnectionState.Connected) return
        viewModelScope.launch {
            val electrodes = runCatching { sessionController.refreshElectrodes() }
                .getOrDefault(sessionController.electrodeStatus.value)
            if (!electrodes.allAttached) {
                localError.value = "Nie można startować pulsu — ${electrodes.summaryPl}"
                return@launch
            }
            sessionController.startPulseScenario()
        }
    }

    fun onStartEcgOfflineScenario() {
        val connected = deviceClient.connectionState.value as? EhoMiniConnectionState.Connected
            ?: return
        viewModelScope.launch {
            val electrodes = runCatching { sessionController.refreshElectrodes() }
                .getOrDefault(sessionController.electrodeStatus.value)
            if (!electrodes.allAttached) {
                localError.value = "Nie można startować EKG — ${electrodes.summaryPl}"
                return@launch
            }
            val userId = bondedDevices.value
                .firstOrNull { it.address == connected.address }
                ?.serialSuffix
                ?: connected.deviceName.takeLast(6)
            sessionController.startEcgOfflineCreateScenario(userId = userId)
        }
    }

    fun onDownloadScp() {
        if (deviceClient.connectionState.value !is EhoMiniConnectionState.Connected) return
        sessionController.startScpDownload()
    }

    fun openRecording(recording: ScpRecording) {
        _viewerTitle.value = recording.displayName
        _viewerError.value = null
        _viewerRecording.value = null
        viewModelScope.launch {
            runCatching {
                val bytes = recordingStore.read(recording.file)
                ScpEcgParser.parse(bytes)
            }.onSuccess {
                _viewerRecording.value = it
            }.onFailure {
                _viewerError.value = it.message ?: "Nie udało się odczytać SCP"
            }
        }
    }

    fun clearViewer() {
        _viewerRecording.value = null
        _viewerError.value = null
    }

    fun clearError() {
        localError.value = null
        if (deviceClient.connectionState.value is EhoMiniConnectionState.Error) {
            viewModelScope.launch { deviceClient.disconnect() }
        }
    }

    override fun onCleared() {
        bleMeasure.release()
        rehabEngine.cancel()
        super.onCleared()
    }
}
