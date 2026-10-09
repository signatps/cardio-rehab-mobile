package pl.cardioscp.rehab.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DirectionsRun
import androidx.compose.material.icons.outlined.Assignment
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.Hotel
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.MonitorWeight
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.cardioscp.rehab.session.ArchivedEcgSlot
import pl.cardioscp.rehab.session.ArchivedRehabSession
import pl.cardioscp.rehab.session.DefaultRehabSurvey
import pl.cardioscp.rehab.session.RehabSessionArchive
import pl.cardioscp.rehab.ui.theme.ProPlusColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private sealed class BrowserSelection {
    data class Ecg(val slotId: String) : BrowserSelection()
    data object Vitals : BrowserSelection()
    data object Survey : BrowserSelection()
}

private sealed class TimelineTile {
    data class Ecg(val slot: ArchivedEcgSlot) : TimelineTile()
    data object Vitals : TimelineTile()
    data object Survey : TimelineTile()
}

/**
 * Zakładka EKG: lewy panel — S1/S2…; po prawej kafelki przebiegu sesji + przeglądarka.
 */
@Composable
fun EcgBrowserScreen(viewModel: HomeViewModel) {
    val sessions by viewModel.archivedSessions.collectAsStateWithLifecycle()
    val viewerRecording by viewModel.viewerRecording.collectAsStateWithLifecycle()
    val viewerError by viewModel.viewerError.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.refreshEcgArchive()
    }

    var selectedSessionId by remember { mutableStateOf<String?>(null) }
    var selection by remember { mutableStateOf<BrowserSelection?>(null) }

    val selectedSession = sessions.firstOrNull { it.id == selectedSessionId }
        ?: sessions.firstOrNull()
    val selectedEcgSlot = (selection as? BrowserSelection.Ecg)?.let { sel ->
        selectedSession?.ecgs?.firstOrNull { it.id == sel.slotId }
    }

    LaunchedEffect(sessions) {
        if (selectedSessionId == null && sessions.isNotEmpty()) {
            selectedSessionId = sessions.first().id
        }
    }

    LaunchedEffect(selectedSession?.id) {
        val session = selectedSession ?: return@LaunchedEffect
        val first = session.ecgs.firstOrNull()
        if (first != null) {
            selection = BrowserSelection.Ecg(first.id)
            viewModel.openArchivedEcg(session, first)
        } else {
            selection = null
            viewModel.clearViewer()
        }
    }

    Row(Modifier.fillMaxSize()) {
        Surface(
            Modifier
                .width(72.dp)
                .fillMaxHeight(),
            color = ProPlusColors.Bg,
            border = BorderStroke(1.dp, ProPlusColors.Line),
            shape = RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp),
        ) {
            Column(
                Modifier
                    .padding(vertical = 10.dp, horizontal = 8.dp)
                    .fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (sessions.isEmpty()) {
                    Text("—", style = MaterialTheme.typography.bodyMedium, color = ProPlusColors.Muted)
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        items(sessions, key = { it.id }) { session ->
                            SessionNumberPictogram(
                                number = session.sessionNumber,
                                selected = session.id == selectedSession?.id,
                                extraViaPin = session.extraViaPin,
                                onClick = { selectedSessionId = session.id },
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.width(8.dp))

        Column(
            Modifier
                .weight(1f)
                .fillMaxHeight(),
        ) {
            if (selectedSession == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Brak zarchiwizowanych sesji.",
                        color = ProPlusColors.Muted,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            } else {
                SessionTimelineKeys(
                    session = selectedSession,
                    selection = selection,
                    onSelectEcg = { slot ->
                        selection = BrowserSelection.Ecg(slot.id)
                        viewModel.openArchivedEcg(selectedSession, slot)
                    },
                    onSelectVitals = {
                        selection = BrowserSelection.Vitals
                        viewModel.clearViewer()
                    },
                    onSelectSurvey = {
                        selection = BrowserSelection.Survey
                        viewModel.clearViewer()
                    },
                )
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(ProPlusColors.Surface),
                ) {
                    when (selection) {
                        BrowserSelection.Vitals -> VitalsDetailPanel(selectedSession)
                        BrowserSelection.Survey -> SurveyDetailPanel(selectedSession)
                        is BrowserSelection.Ecg, null -> {
                            EcgViewerScreen(
                                title = "",
                                recording = viewerRecording,
                                error = viewerError,
                                onBack = {},
                                showChrome = false,
                                browserMeta = selectedEcgSlot?.let { slot ->
                                    EcgViewerBrowserMeta(
                                        sessionNumber = selectedSession.sessionNumber,
                                        cycle = cycleFromEcgLabel(slot.label),
                                        capturedAtMs = slot.capturedAtMs,
                                        phaseLabel = phaseLabelForEcg(slot.label),
                                    )
                                },
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionNumberPictogram(
    number: Int,
    selected: Boolean,
    extraViaPin: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        Modifier
            .size(width = 56.dp, height = 52.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) ProPlusColors.Accent else ProPlusColors.Surface,
        border = BorderStroke(1.dp, if (selected) ProPlusColors.Accent else ProPlusColors.Line),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "S$number",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (selected) ProPlusColors.Surface else ProPlusColors.Navy,
                )
                if (extraViaPin) {
                    Text(
                        "PIN",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (selected) ProPlusColors.Ice else ProPlusColors.ResultWatch,
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionTimelineKeys(
    session: ArchivedRehabSession,
    selection: BrowserSelection?,
    onSelectEcg: (ArchivedEcgSlot) -> Unit,
    onSelectVitals: () -> Unit,
    onSelectSurvey: () -> Unit,
) {
    val tiles = remember(session) { buildTimeline(session) }
    if (tiles.isEmpty()) {
        Text(
            "Brak zapisanych danych w tej sesji.",
            color = ProPlusColors.Muted,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        return
    }
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tiles.forEach { tile ->
            when (tile) {
                is TimelineTile.Ecg -> EcgSlotPictogram(
                    slot = tile.slot,
                    selected = selection is BrowserSelection.Ecg && selection.slotId == tile.slot.id,
                    onClick = { onSelectEcg(tile.slot) },
                )
                TimelineTile.Vitals -> VitalsTile(
                    selected = selection is BrowserSelection.Vitals,
                    onClick = onSelectVitals,
                )
                TimelineTile.Survey -> SurveyTile(
                    passed = session.surveyPassed,
                    selected = selection is BrowserSelection.Survey,
                    onClick = onSelectSurvey,
                )
            }
        }
    }
}

private fun buildTimeline(session: ArchivedRehabSession): List<TimelineTile> {
    val qual = session.ecgs.filter { RehabSessionArchive.isQualificationEcg(it.label) }
    val rest = session.ecgs.filterNot { RehabSessionArchive.isQualificationEcg(it.label) }
    return buildList {
        qual.forEach { add(TimelineTile.Ecg(it)) }
        // Między kwalifikacją a spoczynkiem treningu — ciśnienie/waga + ankieta.
        add(TimelineTile.Vitals)
        add(TimelineTile.Survey)
        rest.forEach { add(TimelineTile.Ecg(it)) }
    }
}

@Composable
private fun EcgSlotPictogram(
    slot: ArchivedEcgSlot,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val icon = iconForEcgLabel(slot.label)
    val badge = badgeForEcgLabel(slot.label)
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) ProPlusColors.Accent else ProPlusColors.Surface,
        border = BorderStroke(1.dp, if (selected) ProPlusColors.Accent else ProPlusColors.Line),
        modifier = Modifier.size(width = 56.dp, height = 52.dp),
    ) {
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                icon,
                contentDescription = slot.label,
                tint = if (selected) ProPlusColors.Surface else ProPlusColors.Navy,
                modifier = Modifier.size(22.dp),
            )
            Text(
                badge,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (selected) ProPlusColors.Ice else ProPlusColors.Muted,
            )
        }
    }
}

@Composable
private fun VitalsTile(
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) ProPlusColors.Accent else ProPlusColors.Surface,
        border = BorderStroke(1.dp, if (selected) ProPlusColors.Accent else ProPlusColors.Line),
        modifier = Modifier.size(width = 56.dp, height = 52.dp),
    ) {
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                Icons.Outlined.MonitorWeight,
                contentDescription = "Ciśnienie i waga",
                tint = if (selected) ProPlusColors.Surface else ProPlusColors.Navy,
                modifier = Modifier.size(20.dp),
            )
            Icon(
                Icons.Outlined.MonitorHeart,
                contentDescription = null,
                tint = if (selected) ProPlusColors.Ice else ProPlusColors.Muted,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

@Composable
private fun SurveyTile(
    passed: Boolean?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val overlayTint = when (passed) {
        true -> ProPlusColors.ResultGood
        false -> ProPlusColors.ResultAlert
        null -> ProPlusColors.Muted
    }
    val bg = when {
        selected && passed == true -> ProPlusColors.ResultGood
        selected && passed == false -> ProPlusColors.ResultAlert
        selected -> ProPlusColors.Accent
        else -> ProPlusColors.Surface
    }
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = bg,
        border = BorderStroke(1.dp, if (selected) bg else ProPlusColors.Line),
        modifier = Modifier.size(width = 56.dp, height = 52.dp),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                Icons.Outlined.Assignment,
                contentDescription = "Ankieta",
                tint = if (selected) Color.White.copy(alpha = 0.35f) else ProPlusColors.Navy.copy(alpha = 0.35f),
                modifier = Modifier.size(34.dp),
            )
            Icon(
                when (passed) {
                    true -> Icons.Outlined.CheckCircle
                    false -> Icons.Outlined.Cancel
                    null -> Icons.Outlined.Assignment
                },
                contentDescription = when (passed) {
                    true -> "Pass"
                    false -> "Fail"
                    null -> "Ankieta"
                },
                tint = if (selected) Color.White else overlayTint,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun VitalsDetailPanel(session: ArchivedRehabSession) {
    val whenLabel = SimpleDateFormat("d.MM.yyyy HH:mm", Locale.forLanguageTag("pl-PL"))
        .format(Date(session.startedAtMs))
    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "Sesja ${session.sessionNumber} · ciśnienie i waga · $whenLabel",
            style = MaterialTheme.typography.labelLarge,
            color = ProPlusColors.Navy,
            fontWeight = FontWeight.SemiBold,
        )
        Surface(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, ProPlusColors.Line),
            color = ProPlusColors.Bg,
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Ciśnienie", style = MaterialTheme.typography.titleMedium, color = ProPlusColors.Navy)
                Text(
                    session.bloodPressureSummary ?: "Brak pomiaru",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (session.bloodPressureSummary != null) ProPlusColors.Accent else ProPlusColors.Muted,
                )
                Text("Waga", style = MaterialTheme.typography.titleMedium, color = ProPlusColors.Navy)
                Text(
                    session.weightSummary ?: "Brak pomiaru / pominięto",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (session.weightSummary != null) ProPlusColors.Accent else ProPlusColors.Muted,
                )
            }
        }
    }
}

@Composable
private fun SurveyDetailPanel(session: ArchivedRehabSession) {
    val whenLabel = SimpleDateFormat("d.MM.yyyy HH:mm", Locale.forLanguageTag("pl-PL"))
        .format(Date(session.startedAtMs))
    val (title, color) = when (session.surveyPassed) {
        true -> "PASS" to ProPlusColors.ResultGood
        false -> "FAIL" to ProPlusColors.ResultAlert
        null -> "Brak wyniku" to ProPlusColors.Muted
    }
    val answers = session.surveyAnswers
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Sesja ${session.sessionNumber} · ankieta · $whenLabel",
                style = MaterialTheme.typography.labelMedium,
                color = ProPlusColors.Navy,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = color.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, color.copy(alpha = 0.45f)),
            ) {
                Row(
                    Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        when (session.surveyPassed) {
                            true -> Icons.Outlined.CheckCircle
                            false -> Icons.Outlined.Cancel
                            null -> Icons.Outlined.Assignment
                        },
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        title,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = color,
                    )
                }
            }
        }
        if (answers.isEmpty()) {
            Text("Brak zapisanych odpowiedzi.", color = ProPlusColors.Muted)
        } else {
            DefaultRehabSurvey.questions.forEach { q ->
                val ans = answers[q.id]
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        q.text,
                        style = MaterialTheme.typography.bodySmall,
                        color = ProPlusColors.Navy,
                        modifier = Modifier.weight(1f),
                        maxLines = 2,
                        fontSize = 12.sp,
                        lineHeight = 14.sp,
                    )
                    val ansLabel = when (ans) {
                        true -> "TAK"
                        false -> "NIE"
                        null -> "—"
                    }
                    val ansColor = when {
                        ans == null -> ProPlusColors.Muted
                        ans == q.safeAnswerYes -> ProPlusColors.ResultGood
                        else -> ProPlusColors.ResultAlert
                    }
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = ansColor.copy(alpha = 0.14f),
                    ) {
                        Text(
                            ansLabel,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = ansColor,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

private fun iconForEcgLabel(label: String): ImageVector {
    val l = label.lowercase()
    return when {
        RehabSessionArchive.isQualificationEcg(label) -> Icons.Outlined.Hotel
        "szczyt" in l || "wysił" in l || "wysilk" in l -> Icons.AutoMirrored.Outlined.DirectionsRun
        "po wysił" in l || "po wysilk" in l -> Icons.Outlined.SelfImprovement
        "spoczynk" in l || "start" in l -> Icons.Outlined.Hotel
        else -> Icons.Outlined.MonitorHeart
    }
}

private fun badgeForEcgLabel(label: String): String {
    if (RehabSessionArchive.isQualificationEcg(label)) return "K"
    Regex("""(\d+)\s*/\s*(\d+)""").find(label)?.let { return it.groupValues[1] }
    val l = label.lowercase()
    return when {
        "start" in l -> "S"
        "po " in l -> "P"
        else -> "•"
    }
}

internal fun cycleFromEcgLabel(label: String): Int? {
    if (RehabSessionArchive.isQualificationEcg(label)) return 0
    Regex("""(\d+)\s*/\s*(\d+)""").find(label)?.groupValues?.get(1)?.toIntOrNull()?.let { return it }
    val l = label.lowercase()
    return when {
        "spoczynk" in l || "start" in l -> 0
        else -> null
    }
}

internal fun phaseLabelForEcg(label: String): String? =
    if (RehabSessionArchive.isQualificationEcg(label)) "kwalifikacja do treningu" else null
