package pl.cardioscp.rehab.ui.screens.clinic

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import pl.cardioscp.rehab.clinic.DiseaseCatalog
import pl.cardioscp.rehab.clinic.DiseaseStatus
import pl.cardioscp.rehab.ui.theme.ProPlusColors
import java.time.LocalDate

@Composable
fun DiseaseAddDialog(
    onDismiss: () -> Unit,
    onSave: (name: String, icd: String, status: DiseaseStatus, diagnosed: String, note: String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<DiseaseCatalog.Hit?>(null) }
    var status by remember { mutableStateOf(DiseaseStatus.AKTUALNA) }
    var diagnosed by remember { mutableStateOf(LocalDate.now().toString().take(7)) }
    var note by remember { mutableStateOf("") }
    val hits = remember(query) { DiseaseCatalog.search(query) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .heightIn(max = 640.dp),
            shape = RoundedCornerShape(18.dp),
            color = ProPlusColors.Bg,
        ) {
            Column(
                Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Dodaj chorobę",
                        style = MaterialTheme.typography.headlineMedium,
                        color = ProPlusColors.Navy,
                    )
                    TextButton(onClick = onDismiss) { Text("Zamknij") }
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Kod ICD lub nazwa") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                if (selected == null) {
                    Text("Wpisz ≥2 znaki i wybierz z katalogu ICD", color = ProPlusColors.Muted)
                    hits.forEach { hit ->
                        Surface(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selected = hit
                                    query = hit.label
                                },
                            shape = RoundedCornerShape(10.dp),
                            color = ProPlusColors.Surface,
                        ) {
                            Column(Modifier.padding(10.dp)) {
                                Text(hit.code, color = ProPlusColors.Accent, fontWeight = FontWeight.Bold)
                                Text(hit.name, color = ProPlusColors.Navy)
                            }
                        }
                    }
                } else {
                    Text(
                        selected!!.label,
                        style = MaterialTheme.typography.titleMedium,
                        color = ProPlusColors.Navy,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text("Rodzaj", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DiseaseStatus.entries.forEach { st ->
                            FilterChip(
                                selected = status == st,
                                onClick = { status = st },
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
                    OutlinedTextField(
                        value = diagnosed,
                        onValueChange = { diagnosed = it.take(10) },
                        label = { Text("Data diagnozy (RRRR-MM)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it.take(80) },
                        label = { Text("Notatka (opcjonalnie)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    Button(
                        onClick = {
                            onSave(selected!!.name, selected!!.code, status, diagnosed, note)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Zapisz") }
                }
            }
        }
    }
}
