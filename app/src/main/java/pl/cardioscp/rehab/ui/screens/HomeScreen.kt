package pl.cardioscp.rehab.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.BluetoothSearching
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import pl.cardioscp.rehab.R
import pl.cardioscp.rehab.bluetooth.BluetoothPermissionHelper
import pl.cardioscp.rehab.bluetooth.EhoMiniConnectionState
import pl.cardioscp.rehab.ui.theme.ProPlusColors

@Composable
fun HomeScreen(
    viewModel: HomeViewModel = viewModel(),
    onOpenRecordings: () -> Unit = {},
    onOpenRehabSession: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        viewModel.refreshPermissions()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Urządzenie EHO-Mini",
            style = MaterialTheme.typography.headlineMedium,
            color = ProPlusColors.Navy,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.home_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = ProPlusColors.Muted,
            textAlign = TextAlign.Center,
        )

        Icon(
            imageVector = Icons.AutoMirrored.Outlined.BluetoothSearching,
            contentDescription = null,
            tint = ProPlusColors.Accent,
            modifier = Modifier.height(36.dp),
        )
        Text(
            text = statusLabel(state.connection),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            color = ProPlusColors.Navy,
        )
        Text(
            text = detailLabel(state),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = ProPlusColors.Muted,
        )

        when (val connection = state.connection) {
            is EhoMiniConnectionState.PermissionsRequired -> {
                Text(
                    text = stringResource(R.string.permissions_rationale),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
                Button(
                    onClick = {
                        permissionLauncher.launch(BluetoothPermissionHelper.requiredPermissions())
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.grant_permissions))
                }
            }

            is EhoMiniConnectionState.Connecting -> {
                Text(
                    text = stringResource(R.string.connecting_to, connection.deviceName),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
            }

            is EhoMiniConnectionState.Connected -> {
                Text(
                    text = connection.deviceName,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = ProPlusColors.Navy,
                )
                Text(
                    text = when (val bpm = state.lastPulseBpm) {
                        null -> stringResource(R.string.pulse_waiting)
                        else -> stringResource(R.string.pulse_bpm, bpm)
                    },
                    style = MaterialTheme.typography.headlineMedium,
                    color = ProPlusColors.Accent,
                    textAlign = TextAlign.Center,
                )
                if (state.pulseSampleCount > 0) {
                    Text(
                        text = stringResource(
                            R.string.pulse_samples,
                            state.pulseSampleCount,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = ProPlusColors.Muted,
                    )
                }
                state.electrodeWarning?.let { warn ->
                    Text(
                        text = warn,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                state.sessionLabel?.let { label ->
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                }
                state.lastSavedScpName?.let { name ->
                    Text(
                        text = stringResource(R.string.scp_saved, name),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = ProPlusColors.Navy,
                    )
                }
                Button(
                    onClick = onOpenRehabSession,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.start_rehab_session))
                }
                Button(
                    onClick = viewModel::onStartPulseScenario,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.scenario_pulse))
                }
                Button(
                    onClick = viewModel::onStartEcgOfflineScenario,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.scenario_ecg_offline))
                }
                Button(
                    onClick = viewModel::onDownloadScp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.download_full_scp))
                }
                OutlinedButton(
                    onClick = onOpenRecordings,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.open_recordings))
                }
                OutlinedButton(
                    onClick = viewModel::onDisconnectClicked,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.disconnect_device))
                }
            }

            is EhoMiniConnectionState.Error -> {
                Text(
                    text = connection.message,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.error,
                )
                TextButton(onClick = viewModel::clearError) {
                    Text("OK")
                }
                Button(
                    onClick = viewModel::onConnectClicked,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.connect_device))
                }
                TextButton(onClick = viewModel::refreshBondedDevices) {
                    Text(stringResource(R.string.refresh_bonded))
                }
            }

            else -> {
                if (state.bondedDevices.isNotEmpty()) {
                    Text(
                        text = stringResource(
                            R.string.bonded_found,
                            state.bondedDevices.joinToString { it.name },
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                }
                Button(
                    onClick = viewModel::onConnectClicked,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.connect_device))
                }
                TextButton(onClick = viewModel::refreshBondedDevices) {
                    Text(stringResource(R.string.refresh_bonded))
                }
                TextButton(onClick = onOpenRecordings) {
                    Text(stringResource(R.string.open_recordings))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun statusLabel(state: EhoMiniConnectionState): String {
    return when (state) {
        EhoMiniConnectionState.Idle -> stringResource(R.string.device_status_idle)
        EhoMiniConnectionState.PermissionsRequired -> stringResource(R.string.permissions_needed)
        EhoMiniConnectionState.LookingForBonded -> stringResource(R.string.device_status_scanning)
        is EhoMiniConnectionState.Connecting -> stringResource(R.string.device_status_connecting)
        is EhoMiniConnectionState.Connected -> stringResource(R.string.device_status_connected)
        is EhoMiniConnectionState.Error -> stringResource(R.string.device_status_error)
    }
}

@Composable
private fun detailLabel(state: HomeUiState): String {
    return when (val connection = state.connection) {
        is EhoMiniConnectionState.Connected ->
            stringResource(R.string.connected_detail, connection.address)
        is EhoMiniConnectionState.Connecting ->
            stringResource(R.string.protocol_ready_hint)
        else -> stringResource(R.string.protocol_ready_hint)
    }
}
