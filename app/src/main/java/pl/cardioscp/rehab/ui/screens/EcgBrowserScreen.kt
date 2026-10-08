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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DirectionsRun
import androidx.compose.material.icons.outlined.Hotel
import androidx.compose.material.icons.outlined.MonitorHeart
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.cardioscp.rehab.session.ArchivedEcgSlot
import pl.cardioscp.rehab.session.ArchivedRehabSession
import pl.cardioscp.rehab.ui.theme.ProPlusColors

/**
 * Zakładka EKG: lewy panel — piktogramy numerów sesji; po prawej
 * piktogramy EKG z sesji + przeglądarka przebiegu.
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
    var selectedSlotId by remember { mutableStateOf<String?>(null) }

    val selectedSession = sessions.firstOrNull { it.id == selectedSessionId }
        ?: sessions.firstOrNull()
    val selectedSlot = selectedSession?.ecgs?.firstOrNull { it.id == selectedSlotId }
        ?: selectedSession?.ecgs?.firstOrNull()

    LaunchedEffect(sessions) {
        if (selectedSessionId == null && sessions.isNotEmpty()) {
            selectedSessionId = sessions.first().id
        }
    }

    LaunchedEffect(selectedSession?.id) {
        val session = selectedSession ?: return@LaunchedEffect
        val first = session.ecgs.firstOrNull()
        selectedSlotId = first?.id
        if (first != null) {
            viewModel.openArchivedEcg(session, first)
        } else {
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
                    Text(
                        "—",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ProPlusColors.Muted,
                    )
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        items(sessions, key = { it.id }) { session ->
                            val selected = session.id == selectedSession?.id
                            SessionNumberPictogram(
                                number = session.sessionNumber,
                                selected = selected,
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
                SessionEcgKeys(
                    session = selectedSession,
                    selectedSlotId = selectedSlotId,
                    onSelect = { slot ->
                        selectedSlotId = slot.id
                        viewModel.openArchivedEcg(selectedSession, slot)
                    },
                )
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(ProPlusColors.Surface),
                ) {
                    EcgViewerScreen(
                        title = "",
                        recording = viewerRecording,
                        error = viewerError,
                        onBack = {},
                        showChrome = false,
                        browserMeta = selectedSlot?.let { slot ->
                            EcgViewerBrowserMeta(
                                sessionNumber = selectedSession.sessionNumber,
                                cycle = cycleFromEcgLabel(slot.label),
                                capturedAtMs = slot.capturedAtMs,
                            )
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionNumberPictogram(
    number: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        Modifier
            .size(52.dp)
            .clickable(onClick = onClick),
        shape = CircleShape,
        color = if (selected) ProPlusColors.Accent else ProPlusColors.Surface,
        border = BorderStroke(1.dp, if (selected) ProPlusColors.Accent else ProPlusColors.Line),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                "$number",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = if (selected) ProPlusColors.Surface else ProPlusColors.Navy,
            )
        }
    }
}

@Composable
private fun SessionEcgKeys(
    session: ArchivedRehabSession,
    selectedSlotId: String?,
    onSelect: (ArchivedEcgSlot) -> Unit,
) {
    if (session.ecgs.isEmpty()) {
        Text(
            "Brak zapisanych EKG w tej sesji.",
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
        session.ecgs.forEach { slot ->
            val selected = slot.id == selectedSlotId
            EcgSlotPictogram(
                slot = slot,
                selected = selected,
                onClick = { onSelect(slot) },
            )
        }
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

private fun iconForEcgLabel(label: String): ImageVector {
    val l = label.lowercase()
    return when {
        "szczyt" in l || "wysił" in l || "wysilk" in l -> Icons.AutoMirrored.Outlined.DirectionsRun
        "po wysił" in l || "po wysilk" in l -> Icons.Outlined.SelfImprovement
        "spoczynk" in l || "przed" in l || "start" in l -> Icons.Outlined.Hotel
        else -> Icons.Outlined.MonitorHeart
    }
}

private fun badgeForEcgLabel(label: String): String {
    Regex("""(\d+)\s*/\s*(\d+)""").find(label)?.let { return it.groupValues[1] }
    val l = label.lowercase()
    return when {
        "przed" in l -> "0"
        "start" in l -> "S"
        "po " in l -> "P"
        else -> "•"
    }
}

internal fun cycleFromEcgLabel(label: String): Int? {
    Regex("""(\d+)\s*/\s*(\d+)""").find(label)?.groupValues?.get(1)?.toIntOrNull()?.let { return it }
    val l = label.lowercase()
    return when {
        "spoczynk" in l || "przed" in l || "start" in l -> 0
        else -> null
    }
}
