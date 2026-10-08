package pl.cardioscp.rehab.ui.screens.clinic

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import pl.cardioscp.rehab.clinic.MedCatalog
import pl.cardioscp.rehab.host.MedCatalogLoader
import pl.cardioscp.rehab.ui.theme.ProPlusColors

@Composable
fun MedAddDialog(
    onDismiss: () -> Unit,
    onSave: (name: String, dose: String, times: List<String>, note: String) -> Unit,
) {
    val context = LocalContext.current
    val catalog = remember {
        runCatching { MedCatalogLoader.get(context) }.getOrNull()
    }
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<MedCatalog.Hit?>(null) }
    var dose by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var times by remember { mutableStateOf(setOf("08:00")) }
    var customTime by remember { mutableStateOf("") }
    val hits = remember(query, catalog) {
        val q = query.trim()
        if (catalog == null || q.length < 2) emptyList()
        else catalog.search(q, limit = 25)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .widthIn(max = 880.dp)
                .heightIn(max = 620.dp)
                .imePadding(),
            shape = RoundedCornerShape(18.dp),
            color = ProPlusColors.Bg,
        ) {
            if (selected == null) {
                MedSearchStep(
                    catalog = catalog,
                    query = query,
                    onQueryChange = { query = it.take(80) },
                    hits = hits,
                    onSelect = { hit ->
                        selected = hit
                        dose = hit.strength.ifBlank { "1" }
                        query = ""
                    },
                    onDismiss = onDismiss,
                )
            } else {
                MedScheduleStep(
                    selected = selected!!,
                    dose = dose,
                    onDoseChange = { dose = it.take(40) },
                    note = note,
                    onNoteChange = { note = it.take(80) },
                    times = times,
                    onToggleTime = { slot ->
                        times = if (slot in times) times - slot else times + slot
                    },
                    customTime = customTime,
                    onCustomTimeChange = { customTime = it.take(5) },
                    onAddCustomTime = {
                        val t = customTime.trim()
                        if (t.matches(Regex("\\d{1,2}:\\d{2}"))) {
                            times = times + t
                            customTime = ""
                        }
                    },
                    onBack = { selected = null },
                    onDismiss = onDismiss,
                    onSave = {
                        if (times.isNotEmpty()) {
                            onSave(selected!!.name, dose, times.sorted(), note)
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun MedSearchStep(
    catalog: MedCatalog.Index?,
    query: String,
    onQueryChange: (String) -> Unit,
    hits: List<MedCatalog.Hit>,
    onSelect: (MedCatalog.Hit) -> Unit,
    onDismiss: () -> Unit,
) {
    BoxWithConstraints(Modifier.padding(16.dp)) {
        val sideBySide = maxWidth >= 640.dp
        Column(
            Modifier.fillMaxWidth(),
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
            if (catalog == null) {
                Text(
                    "Nie wczytano katalogu RPL (MZ) z aplikacji.",
                    color = ProPlusColors.Danger,
                )
                return@Column
            }
            if (sideBySide) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 280.dp, max = 420.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("Szukaj", style = MaterialTheme.typography.labelLarge)
                        OutlinedTextField(
                            value = query,
                            onValueChange = onQueryChange,
                            label = { Text("Nazwa lub substancja") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                        Text(
                            "Wpisz ≥2 znaki. Po wyborze ustawisz harmonogram.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = ProPlusColors.Muted,
                        )
                        Text(
                            "RPL · ${catalog.meta.count} pozycji" +
                                catalog.meta.asOf.takeIf { it.isNotBlank() }?.let { " · stan $it" }.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = ProPlusColors.Muted,
                        )
                    }
                    Column(
                        Modifier
                            .weight(1.2f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text("Lista z katalogu", style = MaterialTheme.typography.labelLarge)
                        MedSearchHitList(query, hits, onSelect)
                    }
                }
            } else {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    label = { Text("Nazwa lub substancja") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Text(
                    "RPL · ${catalog.meta.count} pozycji" +
                        catalog.meta.asOf.takeIf { it.isNotBlank() }?.let { " · stan $it" }.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = ProPlusColors.Muted,
                )
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    MedSearchHitList(query, hits, onSelect)
                }
            }
        }
    }
}

@Composable
private fun MedSearchHitList(
    query: String,
    hits: List<MedCatalog.Hit>,
    onSelect: (MedCatalog.Hit) -> Unit,
) {
    if (hits.isEmpty()) {
        Text(
            if (query.trim().length < 2) {
                "Wpisz co najmniej 2 znaki, żeby szukać."
            } else {
                "Brak wyników."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = ProPlusColors.Muted,
        )
        return
    }
    hits.take(20).forEach { hit ->
        Surface(
            Modifier
                .fillMaxWidth()
                .clickable { onSelect(hit) },
            shape = RoundedCornerShape(10.dp),
            color = ProPlusColors.Surface,
        ) {
            Column(Modifier.padding(12.dp)) {
                Text(
                    hit.label,
                    style = MaterialTheme.typography.titleMedium,
                    color = ProPlusColors.Navy,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (hit.inn.isNotBlank()) {
                    Text(hit.inn, style = MaterialTheme.typography.bodyMedium, color = ProPlusColors.Muted)
                }
            }
        }
    }
}

@Composable
private fun MedScheduleStep(
    selected: MedCatalog.Hit,
    dose: String,
    onDoseChange: (String) -> Unit,
    note: String,
    onNoteChange: (String) -> Unit,
    times: Set<String>,
    onToggleTime: (String) -> Unit,
    customTime: String,
    onCustomTimeChange: (String) -> Unit,
    onAddCustomTime: () -> Unit,
    onBack: () -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
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
                "Harmonogram",
                style = MaterialTheme.typography.headlineMedium,
                color = ProPlusColors.Navy,
            )
            TextButton(onClick = onDismiss) { Text("Zamknij") }
        }
        Text(
            selected.label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = ProPlusColors.Navy,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        TextButton(onClick = onBack) { Text("← Inny lek z katalogu") }
        OutlinedTextField(
            value = dose,
            onValueChange = onDoseChange,
            label = { Text("Dawka (np. 1 tabletka)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Text("Godziny przyjęcia", style = MaterialTheme.typography.labelLarge)
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MedCatalog.PRESET_TIMES.forEach { slot ->
                FilterChip(
                    selected = slot in times,
                    onClick = { onToggleTime(slot) },
                    label = { Text(slot) },
                )
            }
            times.filter { it !in MedCatalog.PRESET_TIMES }.sorted().forEach { slot ->
                FilterChip(
                    selected = true,
                    onClick = { onToggleTime(slot) },
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
                onValueChange = onCustomTimeChange,
                label = { Text("Własna (HH:MM)") },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
            OutlinedButton(onClick = onAddCustomTime) { Text("Dodaj") }
        }
        OutlinedTextField(
            value = note,
            onValueChange = onNoteChange,
            label = { Text("Notatka (opcjonalnie)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Button(
            onClick = onSave,
            enabled = times.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Zapisz harmonogram") }
    }
}
