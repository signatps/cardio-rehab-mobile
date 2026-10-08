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
import pl.cardioscp.rehab.bluetooth.BluetoothPermissionHelper
import pl.cardioscp.rehab.bluetooth.BondedEcgDevice
import pl.cardioscp.rehab.bluetooth.EhoMiniConnectionState
import pl.cardioscp.rehab.bluetooth.ProtocolSessionController
import pl.cardioscp.rehab.bluetooth.SppEhoMiniDeviceClient
import pl.cardioscp.rehab.scp.ScpEcgParser
import pl.cardioscp.rehab.scp.ScpEcgRecording
import pl.cardioscp.rehab.scp.ScpRecording
import pl.cardioscp.rehab.scp.ScpRecordingStore

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
        viewModelScope.launch {
            sessionController.status.collect { status ->
                sessionLabel.value = when (status) {
                    ProtocolSessionController.Status.Idle -> null
                    is ProtocolSessionController.Status.Running -> when (status.scenario) {
                        ProtocolSessionController.Scenario.PULSE ->
                            "Scenariusz 2: odbiór pulsu…"
                        ProtocolSessionController.Scenario.ECG_OFFLINE_CREATE ->
                            "Scenariusz 3: zapis ECG Offline na urządzeniu…"
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
                val serial = (deviceClient.connectionState.value as? EhoMiniConnectionState.Connected)
                    ?.deviceName
                    ?.takeLast(6)
                runCatching {
                    recordingStore.saveComplete(bytes, serial)
                }.onSuccess { saved ->
                    lastSavedScpName.value = saved.displayName
                    recordings.value = recordingStore.list()
                    sessionLabel.value = "Zapisano cały SCP: ${saved.displayName}"
                }.onFailure {
                    sessionLabel.value = "Błąd zapisu SCP: ${it.message}"
                }
            }
        }
        refreshPermissions()
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
        sessionController.stop()
        viewModelScope.launch {
            runCatching { deviceClient.disconnect() }
        }
    }

    fun onStartPulseScenario() {
        if (deviceClient.connectionState.value !is EhoMiniConnectionState.Connected) return
        sessionController.startPulseScenario()
    }

    fun onStartEcgOfflineScenario() {
        val connected = deviceClient.connectionState.value as? EhoMiniConnectionState.Connected
            ?: return
        val userId = bondedDevices.value
            .firstOrNull { it.address == connected.address }
            ?.serialSuffix
            ?: connected.deviceName.takeLast(6)
        sessionController.startEcgOfflineCreateScenario(userId = userId)
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
}
