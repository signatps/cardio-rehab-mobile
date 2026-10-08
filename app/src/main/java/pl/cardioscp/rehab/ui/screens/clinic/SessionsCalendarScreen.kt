package pl.cardioscp.rehab.ui.screens.clinic

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import pl.cardioscp.rehab.clinic.ClinicSnapshot
import pl.cardioscp.rehab.clinic.PlannedSessionStatus
import pl.cardioscp.rehab.ui.theme.ProPlusColors
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val DayCellHeight = 34.dp

@Composable
fun SessionsCalendarScreen(
    clinic: ClinicSnapshot,
    onStartRehab: () -> Unit,
) {
    var month by remember { mutableStateOf(YearMonth.now()) }
    var selected by remember { mutableStateOf(LocalDate.now()) }
    val locale = Locale("pl")
    val daySessions = clinic.sessions.filter { it.date == selected }
    val daysWithSessions = clinic.sessions.map { it.date }.toSet()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Kalendarz sesji", style = MaterialTheme.typography.headlineMedium, color = ProPlusColors.Navy)
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
                            val has = date in daysWithSessions
                            val isSelected = date == selected
                            Box(
                                Modifier
                                    .weight(1f)
                                    .height(DayCellHeight)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        when {
                                            isSelected -> ProPlusColors.Accent
                                            has -> ProPlusColors.Mist
                                            else -> ProPlusColors.Bg
                                        },
                                    )
                                    .clickable { selected = date },
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        "$dayNum",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (isSelected) ProPlusColors.Surface else ProPlusColors.Navy,
                                        fontWeight = if (has || isSelected) FontWeight.Bold else FontWeight.Normal,
                                    )
                                    if (has) {
                                        Box(
                                            Modifier
                                                .padding(top = 1.dp)
                                                .size(width = 10.dp, height = 3.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    if (isSelected) ProPlusColors.Ice else ProPlusColors.Accent,
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

        Text(
            "Sesje · ${selected.format(DateTimeFormatter.ofPattern("d MMMM yyyy", locale))}",
            style = MaterialTheme.typography.titleLarge,
            color = ProPlusColors.Navy,
        )
        if (daySessions.isEmpty()) {
            Text("Brak zaplanowanych sesji tego dnia.", color = ProPlusColors.Muted)
        } else {
            daySessions.forEach { s ->
                Surface(
                    onClick = {
                        if (s.status == PlannedSessionStatus.SCHEDULED && s.date == LocalDate.now()) {
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
                            when (s.status) {
                                PlannedSessionStatus.SCHEDULED -> "Zaplanowana"
                                PlannedSessionStatus.DONE -> "Wykonana"
                                PlannedSessionStatus.MISSED -> "Pominięta"
                                PlannedSessionStatus.CANCELLED -> "Anulowana"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = when (s.status) {
                                PlannedSessionStatus.DONE -> ProPlusColors.ResultGood
                                PlannedSessionStatus.MISSED -> ProPlusColors.Danger
                                else -> ProPlusColors.Muted
                            },
                        )
                    }
                }
            }
        }
    }
}
