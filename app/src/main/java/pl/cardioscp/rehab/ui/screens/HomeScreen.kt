package pl.cardioscp.rehab.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.BluetoothSearching
import androidx.compose.material.icons.outlined.FavoriteBorder
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import pl.cardioscp.rehab.R
import pl.cardioscp.rehab.bluetooth.BluetoothPermissionHelper
import pl.cardioscp.rehab.bluetooth.EhoMiniConnectionState
import pl.cardioscp.rehab.ui.theme.DeepTeal
import pl.cardioscp.rehab.ui.theme.Sand
import pl.cardioscp.rehab.ui.theme.Seafoam
import pl.cardioscp.rehab.ui.theme.SoftMint

@Composable
fun HomeScreen(
    viewModel: HomeViewModel = viewModel(),
    onOpenRecordings: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        viewModel.refreshPermissions()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Sand, SoftMint, Sand),
                ),
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 40.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AnimatedVisibility(
                visible = true,
                enter = fadeIn() + slideInVertically { it / 4 },
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(DeepTeal),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.FavoriteBorder,
                            contentDescription = null,
                            tint = SoftMint,
                            modifier = Modifier.size(36.dp),
                        )
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = stringResource(R.string.home_title),
                        style = MaterialTheme.typography.displayLarge,
                        color = DeepTeal,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.home_subtitle),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center,
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.BluetoothSearching,
                    contentDescription = null,
                    tint = Seafoam,
                    modifier = Modifier.size(40.dp),
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = statusLabel(state.connection),
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                    color = DeepTeal,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = detailLabel(state),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                )
                Spacer(modifier = Modifier.height(24.dp))

                when (val connection = state.connection) {
                    is EhoMiniConnectionState.PermissionsRequired -> {
                        Text(
                            text = stringResource(R.string.permissions_rationale),
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
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
                            color = DeepTeal,
                        )

                        // Always reserve pulse panel once scenario may run — show 0 too.
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = when (val bpm = state.lastPulseBpm) {
                                null -> stringResource(R.string.pulse_waiting)
                                else -> stringResource(R.string.pulse_bpm, bpm)
                            },
                            style = MaterialTheme.typography.displayLarge,
                            color = Seafoam,
                            textAlign = TextAlign.Center,
                        )
                        if (state.pulseSampleCount > 0) {
                            Text(
                                text = stringResource(
                                    R.string.pulse_samples,
                                    state.pulseSampleCount,
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                            )
                        }
                        state.electrodeWarning?.let { warn ->
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = warn,
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.tertiary,
                            )
                        }
                        state.sessionLabel?.let { label ->
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                            )
                        }
                        state.lastSavedScpName?.let { name ->
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.scp_saved, name),
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                color = DeepTeal,
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = viewModel::onStartPulseScenario,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.scenario_pulse))
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = viewModel::onStartEcgOfflineScenario,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.scenario_ecg_offline))
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = viewModel::onDownloadScp,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.download_full_scp))
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = onOpenRecordings,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.open_recordings))
                        }
                        Spacer(modifier = Modifier.height(8.dp))
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
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
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
                            Spacer(modifier = Modifier.height(12.dp))
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
            }

            Text(
                text = stringResource(R.string.session_placeholder),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
            )
        }
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
