package pl.cardioscp.rehab.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Height
import androidx.compose.material.icons.outlined.HorizontalRule
import androidx.compose.material.icons.outlined.MedicalServices
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pl.cardioscp.rehab.ecg.AveragePqrEngine
import pl.cardioscp.rehab.ecg.EcgAnalysisEngine
import pl.cardioscp.rehab.ecg.EcgFilters
import pl.cardioscp.rehab.ecg.EcgPreset
import pl.cardioscp.rehab.ecg.Lead
import pl.cardioscp.rehab.ecg.RecordingMode
import pl.cardioscp.rehab.scp.ScpEcgRecording
import pl.cardioscp.rehab.scp.ScpToEcgLeads
import pl.cardioscp.rehab.ui.ecg.AveragePqrPanel
import pl.cardioscp.rehab.ui.ecg.EcgPaper
import pl.cardioscp.rehab.ui.ecg.EcgUiColors
import pl.cardioscp.rehab.ui.theme.DeepTeal
import pl.cardioscp.rehab.ui.theme.ProPlusColors
import pl.cardioscp.rehab.ui.theme.Sand

/** Metadane z archiwum sesji — zamiast nazwy pliku SCP w nagłówku przeglądarki. */
data class EcgViewerBrowserMeta(
    val sessionNumber: Int,
    val cycle: Int?,
    val capturedAtMs: Long,
    /** Gdy ustawione — zamiast etykiety cyklu (np. „kwalifikacja do treningu”). */
    val phaseLabel: String? = null,
)

@Composable
fun EcgViewerScreen(
    title: String,
    recording: ScpEcgRecording?,
    error: String?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    showChrome: Boolean = true,
    browserMeta: EcgViewerBrowserMeta? = null,
) {
    val wide = LocalConfiguration.current.screenWidthDp >= 840
    var mmPerSec by remember { mutableIntStateOf(25) }
    var mmPerMv by remember { mutableIntStateOf(10) }
    var showR by remember { mutableStateOf(true) }
    var showWaveMarks by remember { mutableStateOf(true) }
    var showDiag by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf(EcgPreset.CLINICAL) }
    var analysisLead by remember { mutableStateOf(Lead.II) }

    Column(
        modifier
            .fillMaxSize()
            .background(if (showChrome) Sand else ProPlusColors.Surface)
            .padding(horizontal = if (showChrome) 8.dp else 4.dp, vertical = 4.dp),
    ) {
        if (showChrome) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Wróć")
                }
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, color = DeepTeal)
                    if (recording != null) {
                        Text(
                            String.format(
                                "%.1f s · %d Hz · AVM %d nV/LSB · SCP",
                                recording.durationSeconds,
                                recording.samplingHz,
                                recording.avm,
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = EcgUiColors.Muted,
                        )
                    }
                }
            }
        } else if (recording != null && browserMeta != null) {
            Text(
                browserMetaLine(browserMeta, recording, mmPerMv),
                style = MaterialTheme.typography.labelLarge,
                color = DeepTeal,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        } else if (recording != null && title.isNotBlank()) {
            Text(
                String.format(
                    "%s · %.1f s · %d Hz · %d mm/mV",
                    title,
                    recording.durationSeconds,
                    recording.samplingHz,
                    mmPerMv,
                ),
                style = MaterialTheme.typography.labelLarge,
                color = DeepTeal,
                modifier = Modifier.padding(bottom = 4.dp),
            )
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
                val filteredLeads = remember(leadsMv, filter) {
                    leadsMv.mapValues { (_, samples) -> EcgFilters.apply(filter, samples) }
                }
                val paperLeads = remember(filteredLeads) { ScpToEcgLeads.paperLeads(filteredLeads) }
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

                CompactToolbar(
                    filter = filter,
                    onFilter = { filter = it },
                    mmPerSec = mmPerSec,
                    onSpeed = { mmPerSec = it },
                    mmPerMv = mmPerMv,
                    onGain = { mmPerMv = it },
                    showR = showR,
                    onShowR = { showR = it },
                    showWaveMarks = showWaveMarks,
                    onShowWaves = { showWaveMarks = it },
                    showDiag = showDiag,
                    onShowDiag = { showDiag = it },
                    analysisLead = resolvedLead,
                    availableLeads = availableLeads,
                    onLead = { analysisLead = it },
                )

                Spacer(Modifier.height(4.dp))

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
                            showDiag = showDiag,
                            detectionDiagnostics = analysis?.detectionDiagnostics.orEmpty(),
                        )
                        AveragePqrPanel(
                            average = average,
                            mmPerMv = mmPerMv,
                            analysis = analysis,
                            modifier = Modifier
                                .width(240.dp)
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
                            showDiag = showDiag,
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
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

private fun browserMetaLine(
    meta: EcgViewerBrowserMeta,
    recording: ScpEcgRecording,
    mmPerMv: Int,
): String {
    val pl = java.util.Locale.forLanguageTag("pl-PL")
    val whenLabel = java.text.SimpleDateFormat("d.MM.yyyy HH:mm", pl)
        .format(java.util.Date(meta.capturedAtMs))
    val phase = meta.phaseLabel ?: when (meta.cycle) {
        null -> "cykl —"
        0 -> "spoczynek"
        else -> "cykl ${meta.cycle}"
    }
    return String.format(
        pl,
        "Sesja %d · %s · %s · %.1f s · %d Hz · %d mm/mV",
        meta.sessionNumber,
        phase,
        whenLabel,
        recording.durationSeconds,
        recording.samplingHz,
        mmPerMv,
    )
}

@Composable
private fun CompactToolbar(
    filter: EcgPreset,
    onFilter: (EcgPreset) -> Unit,
    mmPerSec: Int,
    onSpeed: (Int) -> Unit,
    mmPerMv: Int,
    onGain: (Int) -> Unit,
    showR: Boolean,
    onShowR: (Boolean) -> Unit,
    showWaveMarks: Boolean,
    onShowWaves: (Boolean) -> Unit,
    showDiag: Boolean,
    onShowDiag: (Boolean) -> Unit,
    analysisLead: Lead,
    availableLeads: List<Lead>,
    onLead: (Lead) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ToolGlyph(Icons.AutoMirrored.Outlined.ShowChart, "Surowy", filter == EcgPreset.NONE) {
            onFilter(EcgPreset.NONE)
        }
        ToolGlyph(Icons.Outlined.GraphicEq, "32Hz", filter == EcgPreset.HZ32) {
            onFilter(EcgPreset.HZ32)
        }
        ToolGlyph(Icons.Outlined.Bolt, "50Hz", filter == EcgPreset.HZ50) {
            onFilter(EcgPreset.HZ50)
        }
        ToolGlyph(Icons.Outlined.HorizontalRule, "Izol.", filter == EcgPreset.BASELINE) {
            onFilter(EcgPreset.BASELINE)
        }
        ToolGlyph(Icons.Outlined.MedicalServices, "Klin.", filter == EcgPreset.CLINICAL) {
            onFilter(EcgPreset.CLINICAL)
        }
        ToolDivider()
        listOf(25, 50).forEach { speed ->
            ToolGlyph(Icons.Outlined.Speed, "$speed", mmPerSec == speed) {
                onSpeed(speed)
            }
        }
        ToolDivider()
        listOf(5, 10, 20).forEach { gain ->
            ToolGlyph(Icons.Outlined.Height, "$gain", mmPerMv == gain) {
                onGain(gain)
            }
        }
        ToolDivider()
        ToolGlyph(Icons.Outlined.Timeline, "R", showR) {
            onShowR(!showR)
        }
        ToolGlyph(Icons.Outlined.MedicalServices, "PQR", selected = showWaveMarks) {
            onShowWaves(!showWaveMarks)
        }
        ToolGlyph(Icons.Outlined.BugReport, "Diag", selected = showDiag) {
            onShowDiag(!showDiag)
        }
        LeadDropdown(
            selected = analysisLead,
            available = availableLeads,
            onSelect = onLead,
        )
    }
}

@Composable
private fun ToolGlyph(
    icon: ImageVector,
    caption: String,
    selected: Boolean = false,
    primary: Boolean = false,
    onClick: () -> Unit,
) {
    val active = selected || primary
    val container = when {
        selected -> MaterialTheme.colorScheme.primary
        primary -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.surface
    }
    val content = when {
        selected -> MaterialTheme.colorScheme.onPrimary
        primary -> MaterialTheme.colorScheme.onSecondary
        else -> MaterialTheme.colorScheme.onSurface
    }
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = container,
        border = if (active) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.height(40.dp),
    ) {
        Row(
            Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(icon, contentDescription = caption, tint = content, modifier = Modifier.size(16.dp))
            Text(
                caption,
                color = content,
                fontSize = 11.sp,
                lineHeight = 12.sp,
                maxLines = 1,
                softWrap = false,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun LeadDropdown(
    selected: Lead,
    available: List<Lead>,
    onSelect: (Lead) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val order = Lead.displayOrder.filter { it in available }.ifEmpty { available }
    Box {
        Surface(
            onClick = { open = true },
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier
                .height(40.dp)
                .widthIn(min = 64.dp),
        ) {
            Row(
                Modifier.padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    selected.label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = DeepTeal,
                )
                Text("▾", color = EcgUiColors.Muted, style = MaterialTheme.typography.labelSmall)
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            order.forEach { lead ->
                DropdownMenuItem(
                    text = { Text(lead.label) },
                    onClick = {
                        onSelect(lead)
                        open = false
                    },
                )
            }
        }
    }
}

@Composable
private fun ToolDivider() {
    Box(
        Modifier
            .padding(horizontal = 4.dp)
            .width(1.dp)
            .height(28.dp)
            .background(MaterialTheme.colorScheme.outlineVariant),
    )
}
