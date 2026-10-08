package pl.cardioscp.rehab.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import pl.cardioscp.rehab.bluetooth.BluetoothPermissionHelper
import pl.cardioscp.rehab.bluetooth.EhoMiniConnectionState
import pl.cardioscp.rehab.bluetooth.StubEhoMiniDeviceClient

data class HomeUiState(
    val connection: EhoMiniConnectionState = EhoMiniConnectionState.Idle,
    val protocolReady: Boolean = false,
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val deviceClient = StubEhoMiniDeviceClient()

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        refreshPermissions()
    }

    fun refreshPermissions() {
        val granted = BluetoothPermissionHelper.hasAllPermissions(getApplication())
        _uiState.update { state ->
            state.copy(
                connection = if (granted) {
                    if (state.connection is EhoMiniConnectionState.PermissionsRequired) {
                        EhoMiniConnectionState.Idle
                    } else {
                        state.connection
                    }
                } else {
                    EhoMiniConnectionState.PermissionsRequired
                },
            )
        }
    }

    fun onConnectClicked() {
        if (!BluetoothPermissionHelper.hasAllPermissions(getApplication())) {
            _uiState.update { it.copy(connection = EhoMiniConnectionState.PermissionsRequired) }
            return
        }
        // Discovery filter: PRO_PLUS_ECG_ + 6-digit serial (SPP transport pending).
        _uiState.update {
            it.copy(
                connection = EhoMiniConnectionState.Error(
                    "Transport SPP jeszcze niepodłączony — filtr BT: PRO_PLUS_ECG_******.",
                ),
            )
        }
    }

    fun clearError() {
        _uiState.update { it.copy(connection = EhoMiniConnectionState.Idle) }
    }

    @Suppress("unused")
    internal fun client() = deviceClient
}
