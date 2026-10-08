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
import androidx.compose.material3.Button
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pl.cardioscp.rehab.clinic.ClinicSnapshot
import pl.cardioscp.rehab.clinic.DoseStatus
import pl.cardioscp.rehab.ui.theme.ProPlusColors
import java.time.format.DateTimeFormatter

@Composable
fun MedsScreen(
    clinic: ClinicSnapshot,
    onMarkTaken: (String) -> Unit,
    onAddMedication: (name: String, dose: String, times: List<String>, note: String) -> Unit = { _, _, _, _ -> },
) {
    var addOpen by remember { mutableStateOf(false) }
    val timeFmt = DateTimeFormatter.ofPattern("HH:mm")
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
            Text("Leki", style = MaterialTheme.typography.headlineMedium, color = ProPlusColors.Navy)
            Button(onClick = { addOpen = true }) { Text("Dodaj lek") }
        }
        Text("Dawki na dziś", style = MaterialTheme.typography.titleLarge, color = ProPlusColors.Navy)
        clinic.todayDoses.forEach { dose ->
            Surface(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, ProPlusColors.Line),
                color = when (dose.status) {
                    DoseStatus.TAKEN -> ProPlusColors.Mist
                    DoseStatus.SKIPPED -> ProPlusColors.Bg
                    DoseStatus.PENDING -> ProPlusColors.Surface
                },
            ) {
                Row(
                    Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        dose.time.format(timeFmt),
                        style = MaterialTheme.typography.titleMedium,
                        color = ProPlusColors.Accent,
                        fontWeight = FontWeight.Bold,
                    )
                    Column(Modifier.weight(1f)) {
                        Text(dose.drugName, style = MaterialTheme.typography.titleMedium, color = ProPlusColors.Navy)
                        Text(dose.doseLabel, style = MaterialTheme.typography.bodyMedium, color = ProPlusColors.Muted)
                    }
                    when (dose.status) {
                        DoseStatus.PENDING -> Button(onClick = { onMarkTaken(dose.id) }) { Text("Przyjęte") }
                        DoseStatus.TAKEN -> Text("OK", color = ProPlusColors.ResultGood, fontWeight = FontWeight.Bold)
                        DoseStatus.SKIPPED -> Text("Pominięte", color = ProPlusColors.Danger)
                    }
                }
            }
        }
        Text("Lista leków", style = MaterialTheme.typography.titleLarge, color = ProPlusColors.Navy)
        clinic.medications.forEach { med ->
            Surface(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, ProPlusColors.Line),
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(med.name, style = MaterialTheme.typography.titleMedium, color = ProPlusColors.Navy)
                    Text(
                        "${med.doseLabel} · ${med.scheduleNote}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ProPlusColors.Muted,
                    )
                }
            }
        }
    }
    if (addOpen) {
        MedAddDialog(
            onDismiss = { addOpen = false },
            onSave = { name, dose, times, note ->
                onAddMedication(name, dose, times, note)
                addOpen = false
            },
        )
    }
}
