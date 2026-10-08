package pl.cardioscp.rehab.ui.screens.clinic

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.OutlinedButton
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
import pl.cardioscp.rehab.clinic.DrugCatalog
import pl.cardioscp.rehab.ui.theme.ProPlusColors

@Composable
fun MedAddDialog(
    onDismiss: () -> Unit,
    onSave: (name: String, dose: String, times: List<String>, note: String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<DrugCatalog.Hit?>(null) }
    var dose by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var times by remember { mutableStateOf(setOf("08:00")) }
    var customTime by remember { mutableStateOf("") }
    val hits = remember(query) { DrugCatalog.search(query) }

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
                        "Dodaj lek",
                        style = MaterialTheme.typography.headlineMedium,
                        color = ProPlusColors.Navy,
                    )
                    TextButton(onClick = onDismiss) { Text("Zamknij") }
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Nazwa lub substancja") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                if (selected == null) {
                    Text("Wpisz ≥2 znaki i wybierz z listy", color = ProPlusColors.Muted)
                    hits.forEach { hit ->
                        Surface(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selected = hit
                                    dose = hit.defaultDose
                                    query = hit.name
                                },
                            shape = RoundedCornerShape(10.dp),
                            color = ProPlusColors.Surface,
                        ) {
                            Column(Modifier.padding(10.dp)) {
                                Text(hit.name, fontWeight = FontWeight.SemiBold, color = ProPlusColors.Navy)
                                Text(hit.substance, color = ProPlusColors.Muted)
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
                    OutlinedTextField(
                        value = dose,
                        onValueChange = { dose = it.take(40) },
                        label = { Text("Dawka (np. 5 mg)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    Text("Godziny przyjęcia", style = MaterialTheme.typography.labelLarge)
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        DrugCatalog.PRESET_TIMES.forEach { slot ->
                            FilterChip(
                                selected = slot in times,
                                onClick = {
                                    times = if (slot in times) times - slot else times + slot
                                },
                                label = { Text(slot) },
                            )
                        }
                        times.filter { it !in DrugCatalog.PRESET_TIMES }.forEach { slot ->
                            FilterChip(
                                selected = true,
                                onClick = { times = times - slot },
                                label = { Text(slot) },
                            )
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = customTime,
                            onValueChange = { customTime = it.take(5) },
                            label = { Text("Własna (HH:MM)") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                        )
                        OutlinedButton(
                            onClick = {
                                val t = customTime.trim()
                                if (t.matches(Regex("\\d{1,2}:\\d{2}"))) {
                                    times = times + t
                                    customTime = ""
                                }
                            },
                        ) { Text("Dodaj") }
                    }
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it.take(80) },
                        label = { Text("Notatka (opcjonalnie)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    Button(
                        onClick = {
                            if (times.isNotEmpty()) {
                                onSave(selected!!.name, dose, times.sorted(), note)
                            }
                        },
                        enabled = times.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Zapisz harmonogram") }
                }
            }
        }
    }
}
