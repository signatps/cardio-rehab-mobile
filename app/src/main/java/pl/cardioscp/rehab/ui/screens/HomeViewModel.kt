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

data class HomeUiState(
    val connection: EhoMiniConnectionState = EhoMiniConnectionState.Idle,
    val bondedDevices: List<BondedEcgDevice> = emptyList(),
    val lastPulseBpm: Int? = null,
    val sessionLabel: String? = null,
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val deviceClient = SppEhoMiniDeviceClient(application)
    private val sessionController = ProtocolSessionController(deviceClient, viewModelScope)

    private val permissionsOk = MutableStateFlow(
        BluetoothPermissionHelper.hasAllPermissions(application),
    )
    private val bondedDevices = MutableStateFlow<List<BondedEcgDevice>>(emptyList())
    private val lastPulseBpm = MutableStateFlow<Int?>(null)
    private val localError = MutableStateFlow<String?>(null)
    private val sessionLabel = MutableStateFlow<String?>(null)

    val uiState: StateFlow<HomeUiState> = combine(
        combine(
            deviceClient.connectionState,
            permissionsOk,
            bondedDevices,
        ) { connection, granted, bonded -> Triple(connection, granted, bonded) },
        combine(lastPulseBpm, localError, sessionLabel) { pulse, error, session ->
            Triple(pulse, error, session)
        },
    ) { connPermBonded, pulseErrorSession ->
        val (connection, granted, bonded) = connPermBonded
        val (pulse, error, session) = pulseErrorSession
        val effective = when {
            !granted -> EhoMiniConnectionState.PermissionsRequired
            error != null &&
                (connection is EhoMiniConnectionState.Idle ||
                    connection is EhoMiniConnectionState.Error) ->
                EhoMiniConnectionState.Error(error)
            else -> connection
        }
        HomeUiState(
            connection = effective,
            bondedDevices = bonded,
            lastPulseBpm = pulse,
            sessionLabel = session,
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
        ),
    )

    init {
        viewModelScope.launch {
            deviceClient.incomingFrames.collect { frame ->
                if (frame.type == pl.cardioscp.rehab.bluetooth.protocol.FrameType.PULSE_VALUE &&
                    frame.payload.isNotEmpty()
                ) {
                    lastPulseBpm.value = frame.payload[0].toInt() and 0xFF
                }
            }
        }
        viewModelScope.launch {
            sessionController.status.collect { status ->
                sessionLabel.value = when (status) {
                    ProtocolSessionController.Status.Idle -> null
                    is ProtocolSessionController.Status.Running -> when (status.scenario) {
                        ProtocolSessionController.Scenario.PULSE -> "Scenariusz 2: puls…"
                        ProtocolSessionController.Scenario.ECG_OFFLINE_CREATE ->
                            "Scenariusz 3: ECG Offline…"
                    }
                    is ProtocolSessionController.Status.Pulse -> "Puls: ${status.bpm} bpm"
                    is ProtocolSessionController.Status.Info -> status.message
                    is ProtocolSessionController.Status.Failed -> "Sesja: ${status.reason}"
                    ProtocolSessionController.Status.Finished -> "Scenariusz zakończony"
                }
                if (status is ProtocolSessionController.Status.Pulse) {
                    lastPulseBpm.value = status.bpm
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

    fun clearError() {
        localError.value = null
        if (deviceClient.connectionState.value is EhoMiniConnectionState.Error) {
            viewModelScope.launch { deviceClient.disconnect() }
        }
    }
}
