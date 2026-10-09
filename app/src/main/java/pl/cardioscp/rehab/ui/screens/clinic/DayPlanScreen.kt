package pl.cardioscp.rehab.ui.screens.clinic

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarViewDay
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Medication
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import pl.cardioscp.rehab.clinic.ClinicSnapshot
import pl.cardioscp.rehab.clinic.DayPlanItem
import pl.cardioscp.rehab.clinic.DayPlanKind
import pl.cardioscp.rehab.clinic.DayPlanTone
import pl.cardioscp.rehab.ui.theme.ProPlusColors
import java.time.format.DateTimeFormatter
import java.util.Locale

private val ToneOnTime = Color(0xFFE8F5E9)
private val ToneLate = Color(0xFFFFF8E1)
private val ToneMissed = Color(0xFFFFEBEE)
private val ToneUpcoming = ProPlusColors.Surface

@Composable
fun DayPlanScreen(clinic: ClinicSnapshot) {
    val timeFmt = DateTimeFormatter.ofPattern("HH:mm")
    val dateFmt = DateTimeFormatter.ofPattern("EEEE, d.MM.yyyy", Locale.forLanguageTag("pl-PL"))
    var calendarView by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("Plan na dziś", style = MaterialTheme.typography.headlineMedium, color = ProPlusColors.Navy)
                Text(
                    clinic.planDate.format(dateFmt).replaceFirstChar {
                        if (it.isLowerCase()) it.titlecase(Locale.forLanguageTag("pl-PL")) else it.toString()
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = ProPlusColors.Accent,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            IconButton(onClick = { calendarView = !calendarView }) {
                Icon(
                    imageVector = if (calendarView) Icons.Outlined.ViewAgenda else Icons.Outlined.CalendarViewDay,
                    contentDescription = if (calendarView) {
                        "Widok listy"
                    } else {
                        "Widok dnia z godzinami"
                    },
                    tint = ProPlusColors.Navy,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
        Text(
            if (calendarView) {
                "Kalendarz dnia · leki i sesja w siatce godzin"
            } else {
                "Leki i sesja rehabilitacji. Po północy plan i dawki odświeżają się na nowy dzień."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = ProPlusColors.Muted,
        )
        if (clinic.dayPlan.isEmpty()) {
            Text("Brak pozycji w planie na dziś.", color = ProPlusColors.Muted)
        } else if (calendarView) {
            DayPlanCalendarView(items = clinic.dayPlan, timeFmt = timeFmt)
        } else {
            clinic.dayPlan.forEach { item ->
                DayPlanRow(item = item, timeFmt = timeFmt)
            }
        }
    }
}

@Composable
private fun DayPlanCalendarView(
    items: List<DayPlanItem>,
    timeFmt: DateTimeFormatter,
) {
    val byHour = remember(items) {
        items.groupBy { it.time.hour }
    }
    val hours = remember(byHour) {
        val occupied = byHour.keys
        val lo = (occupied.minOrNull() ?: 6).coerceAtMost(6)
        val hi = (occupied.maxOrNull() ?: 22).coerceAtLeast(22)
        (lo..hi).toList()
    }
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            "Grafik dnia · kolory: w terminie / po terminie / niewykonane / zaplanowane",
            style = MaterialTheme.typography.labelMedium,
            color = ProPlusColors.Muted,
        )
        hours.forEach { hour ->
            val atHour = byHour[hour].orEmpty().sortedBy { it.time }
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(ProPlusColors.Surface, RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    "%02d:00".format(hour),
                    style = MaterialTheme.typography.titleMedium,
                    color = ProPlusColors.Navy,
                    modifier = Modifier.width(56.dp),
                )
                if (atHour.isEmpty()) {
                    Box(
                        Modifier
                            .weight(1f)
                            .height(2.dp)
                            .align(Alignment.CenterVertically)
                            .background(ProPlusColors.Line),
                    )
                } else {
                    Column(
                        Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        atHour.forEach { item ->
                            DayPlanCalendarChip(item = item, timeFmt = timeFmt)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayPlanCalendarChip(
    item: DayPlanItem,
    timeFmt: DateTimeFormatter,
) {
    val accent = when (item.tone) {
        DayPlanTone.ON_TIME -> ProPlusColors.ResultGood
        DayPlanTone.LATE -> ProPlusColors.ResultWatch
        DayPlanTone.MISSED -> ProPlusColors.ResultAlert
        DayPlanTone.UPCOMING -> ProPlusColors.Accent
    }
    val statusLabel = when (item.tone) {
        DayPlanTone.ON_TIME -> "w terminie"
        DayPlanTone.LATE -> "po terminie"
        DayPlanTone.MISSED -> "niewykonane"
        DayPlanTone.UPCOMING -> "zaplanowane"
    }
    Surface(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.5.dp, accent),
        color = accent.copy(alpha = 0.12f),
    ) {
        Row(
            Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                Modifier
                    .size(10.dp)
                    .background(accent, CircleShape),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    "${item.time.format(timeFmt)} · ${item.title}",
                    style = MaterialTheme.typography.titleMedium,
                    color = ProPlusColors.Navy,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "$statusLabel · ${item.detail}",
                    style = MaterialTheme.typography.labelMedium,
                    color = accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun DayPlanRow(
    item: DayPlanItem,
    timeFmt: DateTimeFormatter,
) {
    val bg = when (item.tone) {
        DayPlanTone.ON_TIME -> ToneOnTime
        DayPlanTone.LATE -> ToneLate
        DayPlanTone.MISSED -> ToneMissed
        DayPlanTone.UPCOMING -> ToneUpcoming
    }
    val statusLabel = when (item.tone) {
        DayPlanTone.ON_TIME -> "W terminie"
        DayPlanTone.LATE -> "Po terminie"
        DayPlanTone.MISSED -> "Niewykonane"
        DayPlanTone.UPCOMING -> "Zaplanowane"
    }
    val statusColor = when (item.tone) {
        DayPlanTone.ON_TIME -> ProPlusColors.ResultGood
        DayPlanTone.LATE -> ProPlusColors.ResultWatch
        DayPlanTone.MISSED -> ProPlusColors.ResultAlert
        DayPlanTone.UPCOMING -> ProPlusColors.Muted
    }
    val kindIcon = when (item.kind) {
        DayPlanKind.MED -> Icons.Outlined.Medication
        DayPlanKind.SESSION -> Icons.Outlined.MonitorHeart
        DayPlanKind.MEASUREMENT -> Icons.Outlined.CheckCircle
    }
    Surface(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, ProPlusColors.Line),
        color = bg,
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(kindIcon, contentDescription = null, tint = ProPlusColors.Accent, modifier = Modifier.size(22.dp))
            Text(
                item.time.format(timeFmt),
                style = MaterialTheme.typography.titleMedium,
                color = ProPlusColors.Accent,
                fontWeight = FontWeight.Bold,
            )
            Column(Modifier.weight(1f)) {
                Text(item.title, style = MaterialTheme.typography.titleMedium, color = ProPlusColors.Navy)
                Text(item.detail, style = MaterialTheme.typography.bodyMedium, color = ProPlusColors.Muted)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (item.tone == DayPlanTone.UPCOMING) {
                    Icon(Icons.Outlined.Schedule, contentDescription = null, tint = statusColor, modifier = Modifier.size(16.dp))
                }
                Text(
                    statusLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = statusColor,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}
