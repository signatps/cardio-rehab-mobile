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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.cardioscp.rehab.session.ArchivedEcgSlot
import pl.cardioscp.rehab.session.ArchivedRehabSession
import pl.cardioscp.rehab.ui.theme.ProPlusColors

/**
 * Zakładka EKG: lewy panel — numery sesji; po wyborze po prawej
 * u góry klawisze EKG z sesji, na dole przeglądarka przebiegu.
 */
@Composable
fun EcgBrowserScreen(viewModel: HomeViewModel) {
    val sessions by viewModel.archivedSessions.collectAsStateWithLifecycle()
    val viewerRecording by viewModel.viewerRecording.collectAsStateWithLifecycle()
    val viewerError by viewModel.viewerError.collectAsStateWithLifecycle()
    val viewerTitle by viewModel.viewerTitle.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.refreshEcgArchive()
    }

    var selectedSessionId by remember { mutableStateOf<String?>(null) }
    var selectedSlotId by remember { mutableStateOf<String?>(null) }

    val selectedSession = sessions.firstOrNull { it.id == selectedSessionId }
        ?: sessions.firstOrNull()

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
        // Lewy panel — numery sesji
        Surface(
            Modifier
                .width(148.dp)
                .fillMaxHeight(),
            color = ProPlusColors.Bg,
            border = BorderStroke(1.dp, ProPlusColors.Line),
            shape = RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp),
        ) {
            Column(Modifier.padding(8.dp)) {
                Text(
                    "Sesje",
                    style = MaterialTheme.typography.titleMedium,
                    color = ProPlusColors.Navy,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "Wybierz numer",
                    style = MaterialTheme.typography.labelMedium,
                    color = ProPlusColors.Muted,
                )
                Spacer(Modifier.height(8.dp))
                if (sessions.isEmpty()) {
                    Text(
                        "Brak zarchiwizowanych sesji. Ukończ trening rehab, aby zapisać EKG.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ProPlusColors.Muted,
                    )
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(sessions, key = { it.id }) { session ->
                            val selected = session.id == selectedSession?.id
                            Surface(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedSessionId = session.id
                                    },
                                shape = RoundedCornerShape(10.dp),
                                color = if (selected) ProPlusColors.Accent else ProPlusColors.Surface,
                                border = BorderStroke(1.dp, ProPlusColors.Line),
                            ) {
                                Column(Modifier.padding(10.dp)) {
                                    Text(
                                        "Sesja ${session.sessionNumber}",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = if (selected) ProPlusColors.Surface else ProPlusColors.Navy,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Text(
                                        viewModel.sessionArchiveDateLabel(session),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (selected) ProPlusColors.Ice else ProPlusColors.Muted,
                                    )
                                    Text(
                                        "${session.ecgs.size} EKG",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (selected) ProPlusColors.Mist else ProPlusColors.Muted,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.width(8.dp))

        // Prawe okno
        Column(
            Modifier
                .weight(1f)
                .fillMaxHeight(),
        ) {
            if (selectedSession == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Wybierz sesję z lewej listy.",
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
                HorizontalDivider(color = ProPlusColors.Line)
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(ProPlusColors.Surface),
                ) {
                    EcgViewerScreen(
                        title = viewerTitle,
                        recording = viewerRecording,
                        error = viewerError,
                        onBack = {},
                        showChrome = false,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionEcgKeys(
    session: ArchivedRehabSession,
    selectedSlotId: String?,
    onSelect: (ArchivedEcgSlot) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            "Sesja ${session.sessionNumber} · EKG z treningu",
            style = MaterialTheme.typography.titleLarge,
            color = ProPlusColors.Navy,
            fontWeight = FontWeight.SemiBold,
        )
        if (session.ecgs.isEmpty()) {
            Text("Brak zapisanych EKG w tej sesji.", color = ProPlusColors.Muted)
        } else {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                session.ecgs.forEach { slot ->
                    FilterChip(
                        selected = slot.id == selectedSlotId,
                        onClick = { onSelect(slot) },
                        label = {
                            Text(slot.label, maxLines = 2)
                        },
                    )
                }
            }
        }
    }
}
