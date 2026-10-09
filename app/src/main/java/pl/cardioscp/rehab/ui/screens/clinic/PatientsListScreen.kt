package pl.cardioscp.rehab.ui.screens.clinic

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pl.cardioscp.rehab.auth.RosterPatient
import pl.cardioscp.rehab.clinic.PlannedSession
import pl.cardioscp.rehab.clinic.PlannedSessionKind
import pl.cardioscp.rehab.clinic.PlannedSessionStatus
import pl.cardioscp.rehab.session.SessionSchemeProgress
import pl.cardioscp.rehab.ui.components.SessionSchemeStatusRow
import pl.cardioscp.rehab.ui.theme.ProPlusColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class DoctorPatientRow(
    val patient: RosterPatient,
    val todayRehab: PlannedSession?,
    val schemeProgress: SessionSchemeProgress = SessionSchemeProgress.empty(),
)

@Composable
fun PatientsListScreen(
    rows: List<DoctorPatientRow>,
    selectedPatientId: String?,
    onSelect: (String) -> Unit,
    onOpenCalendar: (String) -> Unit,
) {
    val timeFmt = DateTimeFormatter.ofPattern("HH:mm")
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            "Pacjenci",
            style = MaterialTheme.typography.headlineMedium,
            color = ProPlusColors.Navy,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "Kliknij pacjenta, aby otworzyć szczegóły sesji (kalendarz jak w menu Sesje).",
            style = MaterialTheme.typography.bodyMedium,
            color = ProPlusColors.Muted,
        )
        rows.forEach { row ->
            val selected = row.patient.id == selectedPatientId
            Surface(
                onClick = {
                    onSelect(row.patient.id)
                    onOpenCalendar(row.patient.id)
                },
                shape = RoundedCornerShape(12.dp),
                color = if (selected) {
                    ProPlusColors.Accent.copy(alpha = 0.12f)
                } else {
                    ProPlusColors.Surface
                },
                border = BorderStroke(
                    1.dp,
                    if (selected) ProPlusColors.Accent else ProPlusColors.Line,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Outlined.Person,
                            contentDescription = null,
                            tint = ProPlusColors.Accent,
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                row.patient.displayName,
                                style = MaterialTheme.typography.titleMedium,
                                color = ProPlusColors.Navy,
                                fontWeight = FontWeight.SemiBold,
                            )
                            val rehab = row.todayRehab
                            if (rehab == null) {
                                Text(
                                    "Dziś brak sesji rehab",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ProPlusColors.Muted,
                                )
                            } else {
                                Text(
                                    "Dziś ${rehab.time.format(timeFmt)} · ${statusLabel(rehab.status)}" +
                                        " · ${row.schemeProgress.doneCount}/${row.schemeProgress.plannedCount}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = statusColor(rehab.status),
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                    if (row.todayRehab != null) {
                        SessionSchemeStatusRow(progress = row.schemeProgress)
                    }
                }
            }
        }
    }
}

private fun statusLabel(status: PlannedSessionStatus): String = when (status) {
    PlannedSessionStatus.SCHEDULED -> "zaplanowana"
    PlannedSessionStatus.DONE -> "wykonana"
    PlannedSessionStatus.DISQUALIFIED -> "dyskwalifikacja"
    PlannedSessionStatus.MISSED -> "nieobecność"
    PlannedSessionStatus.CANCELLED -> "anulowana"
}

private fun statusColor(status: PlannedSessionStatus?): androidx.compose.ui.graphics.Color =
    when (status) {
        PlannedSessionStatus.DONE -> ProPlusColors.ResultGood
        PlannedSessionStatus.DISQUALIFIED, PlannedSessionStatus.MISSED -> ProPlusColors.ResultAlert
        PlannedSessionStatus.SCHEDULED -> ProPlusColors.Accent
        PlannedSessionStatus.CANCELLED -> ProPlusColors.Muted
        null -> ProPlusColors.Muted
    }

fun todayRehabSession(sessions: List<PlannedSession>, today: LocalDate = LocalDate.now()): PlannedSession? =
    sessions.firstOrNull {
        it.date == today && it.kind == PlannedSessionKind.REHAB_INTERVAL
    }
