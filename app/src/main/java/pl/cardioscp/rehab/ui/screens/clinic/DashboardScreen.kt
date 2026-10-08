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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bloodtype
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.MonitorWeight
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pl.cardioscp.rehab.clinic.ClinicSnapshot
import pl.cardioscp.rehab.clinic.VitalKind
import pl.cardioscp.rehab.clinic.WelcomePhrase
import pl.cardioscp.rehab.ui.theme.ProPlusColors
import java.time.format.DateTimeFormatter

@Composable
fun DashboardScreen(
    clinic: ClinicSnapshot,
    livePulseBpm: Int?,
    onOpenRehab: () -> Unit,
    onOpenDayPlan: () -> Unit,
    onOpenMeds: () -> Unit,
    onOpenMeasurements: () -> Unit,
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
    val pendingDoses = clinic.todayDoses.count { it.status.name == "PENDING" }
    val welcomeHint = WelcomePhrase.buildFromClinic(clinic)
    val timeFmt = DateTimeFormatter.ofPattern("HH:mm")

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
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
            OutlinedButton(onClick = onOpenMeds) {
                Text(if (pendingDoses > 0) "Leki · $pendingDoses" else "Leki na dziś")
            }
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            VitalCard(
                title = "Ciśnienie",
                value = bp?.valueText ?: "—",
                icon = Icons.Outlined.Bloodtype,
                modifier = Modifier.weight(1f),
            )
            VitalCard(
                title = "Masa",
                value = weight?.valueText ?: "—",
                icon = Icons.Outlined.MonitorWeight,
                modifier = Modifier.weight(1f),
            )
            VitalCard(
                title = if (livePulseBpm != null) "Tętno na żywo" else "Tętno",
                value = pulseText,
                icon = Icons.Outlined.FavoriteBorder,
                modifier = Modifier.weight(1f),
            )
        }

        Text("Plan na dziś", style = MaterialTheme.typography.titleLarge, color = ProPlusColors.Navy)
        clinic.dayPlan.take(4).forEach { item ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = if (item.done) ProPlusColors.Mist else ProPlusColors.Surface,
                border = BorderStroke(1.dp, ProPlusColors.Line),
            ) {
                Row(
                    Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        item.time.format(timeFmt),
                        style = MaterialTheme.typography.titleMedium,
                        color = ProPlusColors.Accent,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Column(Modifier.weight(1f)) {
                        Text(item.title, style = MaterialTheme.typography.titleMedium, color = ProPlusColors.Navy)
                        Text(item.detail, style = MaterialTheme.typography.bodyMedium, color = ProPlusColors.Muted)
                    }
                    if (item.done) {
                        Text("OK", color = ProPlusColors.ResultGood, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onOpenRehab, modifier = Modifier.weight(1f)) {
                Text("Start sesji rehab")
            }
            OutlinedButton(onClick = onOpenDayPlan, modifier = Modifier.weight(1f)) {
                Text("Pełny plan")
            }
        }
        OutlinedButton(onClick = onOpenMeasurements, modifier = Modifier.fillMaxWidth()) {
            Text("Wszystkie pomiary")
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun VitalCard(
    title: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = ProPlusColors.Surface,
        border = BorderStroke(1.dp, ProPlusColors.Line),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(icon, contentDescription = null, tint = ProPlusColors.Accent)
                Text(title, style = MaterialTheme.typography.labelLarge, color = ProPlusColors.Muted)
            }
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                color = ProPlusColors.Navy,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
