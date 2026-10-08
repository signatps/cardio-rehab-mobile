package pl.cardioscp.rehab.ui.screens.clinic

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Medication
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pl.cardioscp.rehab.clinic.ClinicSnapshot
import pl.cardioscp.rehab.clinic.DayPlanItem
import pl.cardioscp.rehab.clinic.DayPlanKind
import pl.cardioscp.rehab.clinic.DayPlanTone
import pl.cardioscp.rehab.ui.theme.ProPlusColors
import java.time.format.DateTimeFormatter

private val ToneOnTime = Color(0xFFE8F5E9)
private val ToneLate = Color(0xFFFFF8E1)
private val ToneMissed = Color(0xFFFFEBEE)
private val ToneUpcoming = ProPlusColors.Surface

@Composable
fun DayPlanScreen(clinic: ClinicSnapshot) {
    val timeFmt = DateTimeFormatter.ofPattern("HH:mm")
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Plan na dziś", style = MaterialTheme.typography.headlineMedium, color = ProPlusColors.Navy)
        Text(
            "Leki i sesja rehabilitacji. Dodatkowe pomiary pacjenta pojawiają się jako wykonane.",
            style = MaterialTheme.typography.bodyMedium,
            color = ProPlusColors.Muted,
        )
        if (clinic.dayPlan.isEmpty()) {
            Text("Brak pozycji w planie na dziś.", color = ProPlusColors.Muted)
        }
        clinic.dayPlan.forEach { item ->
            DayPlanRow(item = item, timeFmt = timeFmt)
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
