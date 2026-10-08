package pl.cardioscp.rehab.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import pl.cardioscp.rehab.ecg.AveragePqrEngine
import pl.cardioscp.rehab.ecg.EcgAnalysisEngine
import pl.cardioscp.rehab.ecg.Lead
import pl.cardioscp.rehab.ecg.RecordingMode
import pl.cardioscp.rehab.scp.ScpEcgRecording
import pl.cardioscp.rehab.scp.ScpToEcgLeads
import pl.cardioscp.rehab.ui.ecg.AveragePqrPanel
import pl.cardioscp.rehab.ui.ecg.EcgPaper
import pl.cardioscp.rehab.ui.ecg.EcgUiColors
import pl.cardioscp.rehab.ui.theme.DeepTeal
import pl.cardioscp.rehab.ui.theme.Sand

@Composable
fun EcgViewerScreen(
    title: String,
    recording: ScpEcgRecording?,
    error: String?,
    onBack: () -> Unit,
) {
    val wide = LocalConfiguration.current.screenWidthDp >= 840
    var mmPerSec by remember { mutableIntStateOf(25) }
    var mmPerMv by remember { mutableIntStateOf(10) }
    var showR by remember { mutableStateOf(true) }
    var showWaveMarks by remember { mutableStateOf(true) }
    var analysisLead by remember { mutableStateOf(Lead.II) }

    Column(
        Modifier
            .fillMaxSize()
            .background(Sand)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Wróć")
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge, color = DeepTeal)
                if (recording != null) {
                    Text(
                        String.format(
                            "%.1f s · %d Hz · AVM %d nV/LSB (%.3f µV) · SCP",
                            recording.durationSeconds,
                            recording.samplingHz,
                            recording.avm,
                            recording.uvPerLsb,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = EcgUiColors.Muted,
                    )
                }
            }
        }

        when {
            error != null -> {
                Text(
                    error,
                    color = MaterialTheme.colorScheme.tertiary,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(16.dp),
                )
            }
            recording == null -> {
                Text(
                    "Ładowanie przebiegu…",
                    style = MaterialTheme.typography.bodyLarge,
                    color = DeepTeal,
                    modifier = Modifier.padding(16.dp),
                )
            }
            else -> {
                val leadsMv = remember(recording) { ScpToEcgLeads.toMillivolts(recording) }
                val paperLeads = remember(leadsMv) { ScpToEcgLeads.paperLeads(leadsMv) }
                val calibration = remember(recording) { ScpToEcgLeads.calibration(recording) }
                val availableLeads = remember(leadsMv) { leadsMv.keys.toList() }
                val resolvedLead = when {
                    analysisLead in availableLeads -> analysisLead
                    Lead.II in availableLeads -> Lead.II
                    Lead.I in availableLeads -> Lead.I
                    availableLeads.isNotEmpty() -> availableLeads.first()
                    else -> Lead.II
                }
                LaunchedEffect(resolvedLead) {
                    if (resolvedLead != analysisLead) analysisLead = resolvedLead
                }

                val analysis = remember(leadsMv, recording.samplingHz, resolvedLead, calibration) {
                    if (leadsMv.isEmpty()) {
                        null
                    } else {
                        EcgAnalysisEngine.analyze(
                            leadsMv = leadsMv,
                            samplingHz = recording.samplingHz,
                            analysisLead = resolvedLead,
                            calibration = calibration,
                            recordingMode = RecordingMode.Rest,
                        )
                    }
                }
                val average = remember(leadsMv, analysis, resolvedLead) {
                    val a = analysis ?: return@remember null
                    AveragePqrEngine.compute(
                        leadsMv = leadsMv,
                        rPeaks = a.rPeaks,
                        samplingHz = recording.samplingHz,
                        analysisLead = resolvedLead,
                        rrMedianMs = a.rrMs,
                        representativePeaks = a.representativePeaks,
                    )
                }

                ToolbarRow(
                    mmPerSec = mmPerSec,
                    onSpeed = { mmPerSec = it },
                    mmPerMv = mmPerMv,
                    onGain = { mmPerMv = it },
                    showR = showR,
                    onShowR = { showR = it },
                    showWaveMarks = showWaveMarks,
                    onShowWaves = { showWaveMarks = it },
                    analysisLead = resolvedLead,
                    availableLeads = availableLeads,
                    onLead = { analysisLead = it },
                )

                Spacer(Modifier.height(6.dp))

                if (wide) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        EcgPaper(
                            leads = paperLeads,
                            samplingHz = recording.samplingHz,
                            mmPerSec = mmPerSec,
                            mmPerMv = mmPerMv,
                            rPeaks = analysis?.rPeaks ?: IntArray(0),
                            showR = showR,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            showWaveMarks = showWaveMarks,
                            analysisLeadLabel = resolvedLead.label,
                            waveMarks = analysis?.waveMarks.orEmpty(),
                            detectionDiagnostics = analysis?.detectionDiagnostics.orEmpty(),
                        )
                        AveragePqrPanel(
                            average = average,
                            mmPerMv = mmPerMv,
                            analysis = analysis,
                            modifier = Modifier
                                .width(280.dp)
                                .fillMaxHeight(),
                        )
                    }
                } else {
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    ) {
                        EcgPaper(
                            leads = paperLeads,
                            samplingHz = recording.samplingHz,
                            mmPerSec = mmPerSec,
                            mmPerMv = mmPerMv,
                            rPeaks = analysis?.rPeaks ?: IntArray(0),
                            showR = showR,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            showWaveMarks = showWaveMarks,
                            analysisLeadLabel = resolvedLead.label,
                            waveMarks = analysis?.waveMarks.orEmpty(),
                            detectionDiagnostics = analysis?.detectionDiagnostics.orEmpty(),
                        )
                        Spacer(Modifier.height(6.dp))
                        AveragePqrPanel(
                            average = average,
                            mmPerMv = mmPerMv,
                            analysis = analysis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp),
                        )
                    }
                }

                Text(
                    text = EcgAnalysisEngine.DISCLAIMER,
                    style = MaterialTheme.typography.labelSmall,
                    color = EcgUiColors.Muted,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun ToolbarRow(
    mmPerSec: Int,
    onSpeed: (Int) -> Unit,
    mmPerMv: Int,
    onGain: (Int) -> Unit,
    showR: Boolean,
    onShowR: (Boolean) -> Unit,
    showWaveMarks: Boolean,
    onShowWaves: (Boolean) -> Unit,
    analysisLead: Lead,
    availableLeads: List<Lead>,
    onLead: (Lead) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Prędkość", style = MaterialTheme.typography.labelSmall, color = EcgUiColors.Muted)
        listOf(25, 50).forEach { v ->
            FilterChip(
                selected = mmPerSec == v,
                onClick = { onSpeed(v) },
                label = { Text("$v mm/s") },
            )
        }
        Text("Gain", style = MaterialTheme.typography.labelSmall, color = EcgUiColors.Muted)
        listOf(5, 10, 20).forEach { v ->
            FilterChip(
                selected = mmPerMv == v,
                onClick = { onGain(v) },
                label = { Text("$v mm/mV") },
            )
        }
        FilterChip(
            selected = showR,
            onClick = { onShowR(!showR) },
            label = { Text("QRS") },
        )
        FilterChip(
            selected = showWaveMarks,
            onClick = { onShowWaves(!showWaveMarks) },
            label = { Text("PQRST") },
        )
        availableLeads.forEach { lead ->
            FilterChip(
                selected = analysisLead == lead,
                onClick = { onLead(lead) },
                label = { Text(lead.label) },
            )
        }
    }
}
