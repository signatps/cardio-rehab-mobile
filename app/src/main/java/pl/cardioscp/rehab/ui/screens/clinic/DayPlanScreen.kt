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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pl.cardioscp.rehab.clinic.ClinicSnapshot
import pl.cardioscp.rehab.ui.theme.ProPlusColors
import java.time.format.DateTimeFormatter

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
            "Harmonogram leków, pomiarów i sesji rehabilitacji.",
            style = MaterialTheme.typography.bodyMedium,
            color = ProPlusColors.Muted,
        )
        clinic.dayPlan.forEach { item ->
            Surface(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, ProPlusColors.Line),
                color = if (item.done) ProPlusColors.Mist else ProPlusColors.Surface,
            ) {
                Row(
                    Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Checkbox(checked = item.done, onCheckedChange = null)
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
                }
            }
        }
    }
}
