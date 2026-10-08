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
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pl.cardioscp.rehab.clinic.ClinicSnapshot
import pl.cardioscp.rehab.clinic.Disease
import pl.cardioscp.rehab.clinic.DiseaseStatus
import pl.cardioscp.rehab.ui.theme.ProPlusColors

@Composable
fun DiseasesScreen(
    clinic: ClinicSnapshot,
    onAddDisease: (name: String, icd: String, status: DiseaseStatus, diagnosed: String, note: String) -> Unit =
        { _, _, _, _, _ -> },
    onSetStatus: (id: String, status: DiseaseStatus) -> Unit = { _, _ -> },
    onRemove: (id: String) -> Unit = {},
) {
    var addOpen by remember { mutableStateOf(false) }
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
            Text("Choroby", style = MaterialTheme.typography.headlineMedium, color = ProPlusColors.Navy)
            Button(onClick = { addOpen = true }) { Text("Dodaj chorobę") }
        }
        Text("Aktualne / przewlekłe", style = MaterialTheme.typography.titleLarge, color = ProPlusColors.Navy)
        clinic.diseases
            .filter { it.status == DiseaseStatus.AKTUALNA || it.status == DiseaseStatus.PRZEWLEKLA }
            .forEach { d ->
                DiseaseCard(d, onSetStatus, onRemove)
            }
        Text("Historyczne", style = MaterialTheme.typography.titleLarge, color = ProPlusColors.Navy)
        clinic.diseases.filter { it.status == DiseaseStatus.HISTORYCZNA }.forEach { d ->
            DiseaseCard(d, onSetStatus, onRemove)
        }
    }
    if (addOpen) {
        DiseaseAddDialog(
            onDismiss = { addOpen = false },
            onSave = { name, icd, status, diagnosed, note ->
                onAddDisease(name, icd, status, diagnosed, note)
                addOpen = false
            },
        )
    }
}

@Composable
private fun DiseaseCard(
    d: Disease,
    onSetStatus: (id: String, status: DiseaseStatus) -> Unit,
    onRemove: (id: String) -> Unit,
) {
    Surface(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, ProPlusColors.Line),
        color = if (d.status == DiseaseStatus.HISTORYCZNA) ProPlusColors.Bg else ProPlusColors.Surface,
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(d.name, style = MaterialTheme.typography.titleMedium, color = ProPlusColors.Navy, fontWeight = FontWeight.SemiBold)
            if (d.icd.isNotBlank()) {
                Text("ICD: ${d.icd}", style = MaterialTheme.typography.bodyMedium, color = ProPlusColors.Accent)
            }
            Text("Diagnoza: ${d.diagnosedLabel}", style = MaterialTheme.typography.bodyMedium, color = ProPlusColors.Muted)
            if (d.note.isNotBlank()) {
                Text(d.note, style = MaterialTheme.typography.bodyMedium, color = ProPlusColors.Muted)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DiseaseStatus.entries.forEach { st ->
                    FilterChip(
                        selected = d.status == st,
                        onClick = { onSetStatus(d.id, st) },
                        label = {
                            Text(
                                when (st) {
                                    DiseaseStatus.AKTUALNA -> "Aktualna"
                                    DiseaseStatus.PRZEWLEKLA -> "Przewlekła"
                                    DiseaseStatus.HISTORYCZNA -> "Historyczna"
                                },
                            )
                        },
                    )
                }
            }
            TextButton(onClick = { onRemove(d.id) }) { Text("Usuń") }
        }
    }
}
