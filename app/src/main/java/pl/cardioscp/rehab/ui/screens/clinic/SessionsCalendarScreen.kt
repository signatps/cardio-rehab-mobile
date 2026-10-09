package pl.cardioscp.rehab.ui.screens.clinic

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import pl.cardioscp.rehab.clinic.ClinicSnapshot
import pl.cardioscp.rehab.clinic.PlannedSession
import pl.cardioscp.rehab.clinic.PlannedSessionKind
import pl.cardioscp.rehab.clinic.PlannedSessionStatus
import pl.cardioscp.rehab.ui.theme.ProPlusColors
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val DayCellHeight = 34.dp
private val DayGray = Color(0xFFE5E7EA)
private val DayGreen = Color(0xFFD1E7DD)
private val DayRed = Color(0xFFF8D7DA)
private val DayScheduled = Color(0xFFDAF1FF)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SessionsCalendarScreen(
    clinic: ClinicSnapshot,
    onStartRehab: () -> Unit,
    canStartRehab: Boolean = true,
    subtitle: String? = null,
) {
    var month by remember { mutableStateOf(YearMonth.now()) }
    var selected by remember { mutableStateOf(LocalDate.now()) }
    val locale = Locale("pl")
    val daySessions = clinic.sessions.filter { it.date == selected }
    val sessionsByDay = remember(clinic.sessions) {
        clinic.sessions.groupBy { it.date }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Kalendarz sesji", style = MaterialTheme.typography.headlineMedium, color = ProPlusColors.Navy)
        subtitle?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = ProPlusColors.Muted,
            )
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { month = month.minusMonths(1) }) { Text("‹") }
            Text(
                month.month.getDisplayName(TextStyle.FULL_STANDALONE, locale)
                    .replaceFirstChar { it.titlecase(locale) } + " ${month.year}",
                style = MaterialTheme.typography.titleLarge,
                color = ProPlusColors.Navy,
                fontWeight = FontWeight.SemiBold,
            )
            TextButton(onClick = { month = month.plusMonths(1) }) { Text("›") }
        }

        Row(Modifier.fillMaxWidth()) {
            listOf("Pn", "Wt", "Śr", "Cz", "Pt", "So", "Nd").forEach {
                Text(
                    it,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = ProPlusColors.Muted,
                )
            }
        }

        val first = month.atDay(1)
        val shift = (first.dayOfWeek.value + 6) % 7 // Monday=0
        val days = month.lengthOfMonth()
        val cells = shift + days
        val rows = (cells + 6) / 7
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            repeat(rows) { row ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    repeat(7) { col ->
                        val index = row * 7 + col
                        val dayNum = index - shift + 1
                        if (dayNum in 1..days) {
                            val date = month.atDay(dayNum)
                            val dayList = sessionsByDay[date].orEmpty()
                            val tone = dayTone(dayList)
                            val isSelected = date == selected
                            Box(
                                Modifier
                                    .weight(1f)
                                    .height(DayCellHeight)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSelected) ProPlusColors.Accent else tone.background)
                                    .clickable { selected = date },
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        "$dayNum",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (isSelected) ProPlusColors.Surface else tone.foreground,
                                        fontWeight = if (tone != DayTone.EMPTY || isSelected) {
                                            FontWeight.Bold
                                        } else {
                                            FontWeight.Normal
                                        },
                                    )
                                    if (tone != DayTone.EMPTY) {
                                        Box(
                                            Modifier
                                                .padding(top = 1.dp)
                                                .size(width = 10.dp, height = 3.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    if (isSelected) ProPlusColors.Ice else tone.mark,
                                                ),
                                        )
                                    }
                                }
                            }
                        } else {
                            Spacer(Modifier.weight(1f).height(DayCellHeight))
                        }
                    }
                }
            }
        }

        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            LegendDot(DayGreen, "Wykonana")
            LegendDot(DayRed, "Brak dopuszczenia / anulowana")
            LegendDot(DayGray, "Bez ćwiczeń")
            LegendDot(DayScheduled, "Zaplanowana")
        }

        Text(
            "Sesje · ${selected.format(DateTimeFormatter.ofPattern("d MMMM yyyy", locale))}",
            style = MaterialTheme.typography.titleLarge,
            color = ProPlusColors.Navy,
        )
        if (daySessions.isEmpty()) {
            Text("Brak zaplanowanych ćwiczeń tego dnia.", color = ProPlusColors.Muted)
        } else {
            daySessions.forEach { s ->
                Surface(
                    onClick = {
                        if (
                            canStartRehab &&
                            s.status == PlannedSessionStatus.SCHEDULED &&
                            s.date == LocalDate.now()
                        ) {
                            onStartRehab()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, ProPlusColors.Line),
                    color = ProPlusColors.Surface,
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            "${s.time} · ${s.title}",
                            style = MaterialTheme.typography.titleMedium,
                            color = ProPlusColors.Navy,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            buildString {
                                append(statusLabel(s.status))
                                if (s.title.contains("PIN", ignoreCase = true)) {
                                    append(" · PIN")
                                }
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = when (s.status) {
                                PlannedSessionStatus.DONE -> ProPlusColors.ResultGood
                                PlannedSessionStatus.DISQUALIFIED,
                                PlannedSessionStatus.MISSED,
                                PlannedSessionStatus.CANCELLED,
                                -> ProPlusColors.Danger
                                else -> ProPlusColors.Muted
                            },
                        )
                    }
                }
            }
        }
    }
}

private enum class DayTone(
    val background: Color,
    val foreground: Color,
    val mark: Color,
) {
    EMPTY(DayGray, ProPlusColors.Muted, ProPlusColors.Muted),
    DONE(DayGreen, ProPlusColors.ResultGood, ProPlusColors.ResultGood),
    BAD(DayRed, ProPlusColors.Danger, ProPlusColors.Danger),
    SCHEDULED(DayScheduled, ProPlusColors.Navy, ProPlusColors.Accent),
}

/** Priorytet: problem (czerwony) > wykonana (zielony) > zaplanowana > szary. */
private fun dayTone(daySessions: List<PlannedSession>): DayTone {
    val rehab = daySessions.filter { it.kind == PlannedSessionKind.REHAB_INTERVAL }
    val relevant = rehab.ifEmpty { daySessions }
    if (relevant.isEmpty()) return DayTone.EMPTY
    if (relevant.any {
            it.status == PlannedSessionStatus.DISQUALIFIED ||
                it.status == PlannedSessionStatus.CANCELLED ||
                it.status == PlannedSessionStatus.MISSED
        }
    ) {
        return DayTone.BAD
    }
    if (relevant.any { it.status == PlannedSessionStatus.DONE }) return DayTone.DONE
    if (relevant.any { it.status == PlannedSessionStatus.SCHEDULED }) return DayTone.SCHEDULED
    return DayTone.EMPTY
}

private fun statusLabel(status: PlannedSessionStatus): String = when (status) {
    PlannedSessionStatus.SCHEDULED -> "Zaplanowana"
    PlannedSessionStatus.DONE -> "Wykonana"
    PlannedSessionStatus.DISQUALIFIED -> "Brak dopuszczenia"
    PlannedSessionStatus.MISSED -> "Pominięta"
    PlannedSessionStatus.CANCELLED -> "Anulowana"
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color),
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = ProPlusColors.Muted,
            maxLines = 2,
            softWrap = true,
        )
    }
}
