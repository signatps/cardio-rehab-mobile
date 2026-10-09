package pl.cardioscp.rehab.ui.screens.clinic

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.outlined.Bloodtype
import androidx.compose.material.icons.outlined.CalendarViewDay
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.DirectionsBike
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Medication
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.MonitorWeight
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pl.cardioscp.rehab.ble.BpWho
import pl.cardioscp.rehab.clinic.ClinicSnapshot
import pl.cardioscp.rehab.clinic.DayPlanItem
import pl.cardioscp.rehab.clinic.DayPlanKind
import pl.cardioscp.rehab.clinic.DayPlanTone
import pl.cardioscp.rehab.clinic.VitalKind
import pl.cardioscp.rehab.clinic.WelcomePhrase
import pl.cardioscp.rehab.session.ArchivedRehabSession
import pl.cardioscp.rehab.session.CycleHrSummary
import pl.cardioscp.rehab.session.CycleHrZoneOutcome
import pl.cardioscp.rehab.ui.theme.ProPlusColors
import java.time.format.DateTimeFormatter

@Composable
fun DashboardScreen(
    clinic: ClinicSnapshot,
    livePulseBpm: Int?,
    todaySession: ArchivedRehabSession?,
    alertCount: Int = 0,
    onOpenRehab: () -> Unit,
    onOpenDayPlan: () -> Unit,
    onOpenMeds: () -> Unit,
    onOpenAlerts: () -> Unit = onOpenMeds,
    onMeasureBp: () -> Unit = {},
    onMeasureWeight: () -> Unit = {},
    onSpeakWelcome: () -> Unit = {},
) {
    LaunchedEffect(Unit) {
        onSpeakWelcome()
    }

    val bp = clinic.measurements.firstOrNull { it.kind == VitalKind.BLOOD_PRESSURE }
    val weight = clinic.measurements.firstOrNull { it.kind == VitalKind.WEIGHT }
    val pulseText = livePulseBpm?.let { "$it bpm" }
        ?: clinic.measurements.firstOrNull { it.kind == VitalKind.PULSE }?.valueText
        ?: "—"
    val pendingDoses = clinic.todayDoses.count { it.needsAction }
    val welcomeHint = WelcomePhrase.buildFromClinic(clinic)
    val timeFmt = DateTimeFormatter.ofPattern("HH:mm")
    val bpColor = whoColorForBpText(bp?.valueText)
    val pulseColor = whoColorForPulse(livePulseBpm)
    val measureItems = clinic.dayPlan.filter { it.kind != DayPlanKind.SESSION }
    val sessionItems = clinic.dayPlan.filter { it.kind == DayPlanKind.SESSION }

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
            Column(Modifier.weight(1f)) {
                Text("Witaj", style = MaterialTheme.typography.titleMedium, color = ProPlusColors.Muted)
                Text(
                    clinic.patientName,
                    style = MaterialTheme.typography.headlineMedium,
                    color = ProPlusColors.Navy,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    welcomeHint.removePrefix("Witaj ${WelcomePhrase.firstName(clinic.patientName)}. ").trim(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = ProPlusColors.Muted,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                IconButton(onClick = onOpenMeds) {
                    BadgedBox(
                        badge = {
                            if (pendingDoses > 0) {
                                Badge(
                                    containerColor = ProPlusColors.Accent,
                                    contentColor = Color.White,
                                ) {
                                    Text(
                                        if (pendingDoses > 99) "99+" else "$pendingDoses",
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                            }
                        },
                    ) {
                        Icon(
                            Icons.Outlined.Medication,
                            contentDescription = if (pendingDoses == 0) {
                                "Leki na dziś — brak dawek"
                            } else {
                                "Leki na dziś · $pendingDoses"
                            },
                            tint = ProPlusColors.Navy,
                            modifier = Modifier.size(26.dp),
                        )
                    }
                }
                IconButton(onClick = onOpenAlerts) {
                    BadgedBox(
                        badge = {
                            if (alertCount > 0) {
                                Badge(
                                    containerColor = ProPlusColors.ResultAlert,
                                    contentColor = Color.White,
                                ) {
                                    Text(
                                        if (alertCount > 99) "99+" else "$alertCount",
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                            }
                        },
                    ) {
                        Icon(
                            Icons.Outlined.Notifications,
                            contentDescription = if (alertCount == 0) {
                                "Alerty"
                            } else {
                                "Alerty · $alertCount"
                            },
                            tint = ProPlusColors.Navy,
                            modifier = Modifier.size(26.dp),
                        )
                    }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CompactVitalTile(
                icon = Icons.Outlined.Bloodtype,
                value = bp?.valueText ?: "—",
                valueColor = bpColor,
                onMeasure = onMeasureBp,
                showMeasure = true,
                modifier = Modifier.weight(1f),
            )
            CompactVitalTile(
                icon = Icons.Outlined.MonitorWeight,
                value = weight?.valueText ?: "—",
                valueColor = ProPlusColors.Navy,
                onMeasure = onMeasureWeight,
                showMeasure = true,
                modifier = Modifier.weight(1f),
            )
            CompactVitalTile(
                icon = Icons.Outlined.FavoriteBorder,
                value = pulseText,
                valueColor = pulseColor,
                onMeasure = null,
                showMeasure = false,
                modifier = Modifier.weight(1f),
            )
        }

        Text(
            "Plan na dziś · ${clinic.planDate.format(java.time.format.DateTimeFormatter.ofPattern("d.MM.yyyy"))}",
            style = MaterialTheme.typography.titleMedium,
            color = ProPlusColors.Navy,
            fontWeight = FontWeight.SemiBold,
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    "Pomiary / leki",
                    style = MaterialTheme.typography.labelLarge,
                    color = ProPlusColors.Muted,
                )
                if (measureItems.isEmpty()) {
                    Text("Brak pozycji", color = ProPlusColors.Muted, style = MaterialTheme.typography.bodySmall)
                } else {
                    measureItems.take(6).forEach { item ->
                        CompactPlanTile(item = item, timeFmt = timeFmt)
                    }
                }
            }
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    "Sesje",
                    style = MaterialTheme.typography.labelLarge,
                    color = ProPlusColors.Muted,
                )
                if (sessionItems.isEmpty()) {
                    Text("Brak sesji", color = ProPlusColors.Muted, style = MaterialTheme.typography.bodySmall)
                } else {
                    sessionItems.take(4).forEach { item ->
                        SessionPlanTile(
                            item = item,
                            archived = todaySession.takeIf { item.done },
                            timeFmt = timeFmt,
                        )
                    }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(
                onClick = onOpenDayPlan,
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp),
                contentPadding = ButtonDefaults.ContentPadding,
            ) {
                Icon(
                    Icons.Outlined.CalendarViewDay,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.size(6.dp))
                Text("Pełny plan", style = MaterialTheme.typography.labelLarge)
            }
            Button(
                onClick = onOpenRehab,
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp),
                contentPadding = ButtonDefaults.ContentPadding,
            ) {
                Icon(
                    Icons.Outlined.DirectionsBike,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.size(6.dp))
                Text("Start sesji", style = MaterialTheme.typography.labelLarge)
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun CompactVitalTile(
    icon: ImageVector,
    value: String,
    valueColor: Color,
    onMeasure: (() -> Unit)?,
    showMeasure: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = ProPlusColors.Surface,
        border = BorderStroke(1.dp, ProPlusColors.Line),
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(icon, contentDescription = null, tint = ProPlusColors.Accent, modifier = Modifier.size(28.dp))
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                color = valueColor,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
                maxLines = 2,
            )
            if (showMeasure && onMeasure != null) {
                IconButton(onClick = onMeasure, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Outlined.PlayArrow,
                        contentDescription = "Pomiar",
                        tint = ProPlusColors.Accent,
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactPlanTile(
    item: DayPlanItem,
    timeFmt: DateTimeFormatter,
) {
    val bg = toneBg(item.tone)
    val statusColor = toneColor(item.tone)
    val kindIcon = when (item.kind) {
        DayPlanKind.MED -> Icons.Outlined.Medication
        DayPlanKind.MEASUREMENT -> when {
            item.title.contains("ciśn", ignoreCase = true) ||
                item.detail.contains("/", ignoreCase = false) -> Icons.Outlined.Bloodtype
            item.title.contains("mas", ignoreCase = true) ||
                item.detail.contains("kg", ignoreCase = true) -> Icons.Outlined.MonitorWeight
            else -> Icons.Outlined.FavoriteBorder
        }
        DayPlanKind.SESSION -> Icons.Outlined.MonitorHeart
    }
    Surface(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, ProPlusColors.Line),
        color = bg,
    ) {
        Row(
            Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(kindIcon, contentDescription = null, tint = ProPlusColors.Accent, modifier = Modifier.size(16.dp))
            Text(
                item.time.format(timeFmt),
                style = MaterialTheme.typography.labelLarge,
                color = ProPlusColors.Accent,
                fontWeight = FontWeight.Bold,
            )
            Column(Modifier.weight(1f)) {
                Text(
                    item.title,
                    style = MaterialTheme.typography.labelLarge,
                    color = ProPlusColors.Navy,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    item.detail,
                    style = MaterialTheme.typography.labelSmall,
                    color = ProPlusColors.Muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                toneShort(item.tone),
                style = MaterialTheme.typography.labelSmall,
                color = statusColor,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun SessionPlanTile(
    item: DayPlanItem,
    archived: ArchivedRehabSession?,
    timeFmt: DateTimeFormatter,
) {
    val bg = toneBg(item.tone)
    val statusColor = toneColor(item.tone)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = bg,
        border = BorderStroke(1.dp, ProPlusColors.Line),
    ) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    Icons.Outlined.MonitorHeart,
                    contentDescription = null,
                    tint = ProPlusColors.Accent,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    item.time.format(timeFmt),
                    style = MaterialTheme.typography.labelLarge,
                    color = ProPlusColors.Accent,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    item.title,
                    style = MaterialTheme.typography.labelLarge,
                    color = ProPlusColors.Navy,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    toneShort(item.tone),
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            if (archived != null) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    archived.bloodPressureSummary?.let { bpSum ->
                        PictogramValue(
                            icon = Icons.Outlined.Bloodtype,
                            value = bpSum,
                            valueColor = whoColorForBpText(bpSum),
                        )
                    }
                    archived.weightSummary?.let { w ->
                        PictogramValue(
                            icon = Icons.Outlined.MonitorWeight,
                            value = w,
                            valueColor = ProPlusColors.Navy,
                        )
                    }
                    SurveyPictogram(archived.surveyPassed)
                }
                if (archived.cycleHrSummaries.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        archived.cycleHrSummaries.forEach { summary ->
                            CycleHrChip(summary)
                        }
                    }
                }
            } else {
                Text(
                    item.detail,
                    style = MaterialTheme.typography.labelSmall,
                    color = ProPlusColors.Muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun toneBg(tone: DayPlanTone): Color = when (tone) {
    DayPlanTone.ON_TIME -> Color(0xFFE8F5E9)
    DayPlanTone.LATE -> Color(0xFFFFF8E1)
    DayPlanTone.MISSED -> Color(0xFFFFEBEE)
    DayPlanTone.UPCOMING -> ProPlusColors.Surface
}

private fun toneColor(tone: DayPlanTone): Color = when (tone) {
    DayPlanTone.ON_TIME -> ProPlusColors.ResultGood
    DayPlanTone.LATE -> ProPlusColors.ResultWatch
    DayPlanTone.MISSED -> ProPlusColors.ResultAlert
    DayPlanTone.UPCOMING -> ProPlusColors.Muted
}

private fun toneShort(tone: DayPlanTone): String = when (tone) {
    DayPlanTone.ON_TIME -> "OK"
    DayPlanTone.LATE -> "Późno"
    DayPlanTone.MISSED -> "Brak"
    DayPlanTone.UPCOMING -> "Plan"
}

@Composable
private fun PictogramValue(
    icon: ImageVector,
    value: String,
    valueColor: Color,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(icon, contentDescription = null, tint = ProPlusColors.Accent, modifier = Modifier.size(14.dp))
        Text(
            value,
            style = MaterialTheme.typography.labelSmall,
            color = valueColor,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SurveyPictogram(passed: Boolean?) {
    val (icon, tint, label) = when (passed) {
        true -> Triple(Icons.Outlined.CheckCircle, ProPlusColors.ResultGood, "Ankieta OK")
        false -> Triple(Icons.Outlined.Cancel, ProPlusColors.ResultAlert, "Ankieta FAIL")
        null -> Triple(Icons.AutoMirrored.Outlined.Assignment, ProPlusColors.Muted, "Ankieta —")
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(14.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = tint, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun CycleHrChip(summary: CycleHrSummary) {
    val (icon, tint) = when (summary.outcome) {
        CycleHrZoneOutcome.OK -> Icons.Outlined.CheckCircle to ProPlusColors.ResultGood
        CycleHrZoneOutcome.TOO_LOW -> Icons.AutoMirrored.Outlined.TrendingDown to ProPlusColors.AccentBright
        CycleHrZoneOutcome.TOO_HIGH -> Icons.AutoMirrored.Outlined.TrendingUp to ProPlusColors.ResultAlert
        CycleHrZoneOutcome.UNKNOWN -> Icons.Outlined.FavoriteBorder to ProPlusColors.Muted
    }
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = tint.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, tint.copy(alpha = 0.35f)),
    ) {
        Row(
            Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                "C${summary.cycle}",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = ProPlusColors.Navy,
            )
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(12.dp))
            summary.avgBpm?.let {
                Text("$it", fontSize = 10.sp, color = tint, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

private fun whoColorForBpText(valueText: String?): Color {
    if (valueText.isNullOrBlank() || valueText == "—") return ProPlusColors.Navy
    val m = Regex("""(\d+)\s*/\s*(\d+)""").find(valueText) ?: return ProPlusColors.Navy
    val sys = m.groupValues[1].toIntOrNull()
    val dia = m.groupValues[2].toIntOrNull()
    val band = BpWho.band(sys, dia)
    return when (band) {
        BpWho.Band.OPTIMAL, BpWho.Band.NORMAL -> ProPlusColors.ResultGood
        BpWho.Band.HIGH_NORMAL, BpWho.Band.LOW -> ProPlusColors.ResultWatch
        BpWho.Band.GRADE1, BpWho.Band.GRADE2, BpWho.Band.GRADE3 -> ProPlusColors.ResultAlert
        BpWho.Band.UNKNOWN -> ProPlusColors.Navy
    }
}

private fun whoColorForPulse(bpm: Int?): Color {
    if (bpm == null || bpm <= 0) return ProPlusColors.Navy
    return when {
        bpm < 50 || bpm > 120 -> ProPlusColors.ResultAlert
        bpm < 60 || bpm > 100 -> ProPlusColors.ResultWatch
        else -> ProPlusColors.ResultGood
    }
}
