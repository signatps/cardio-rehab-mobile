package pl.cardioscp.rehab.ui.screens.clinic

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import pl.cardioscp.rehab.clinic.DiseaseCatalog
import pl.cardioscp.rehab.clinic.DiseaseStatus
import pl.cardioscp.rehab.host.DiseaseCatalogLoader
import pl.cardioscp.rehab.ui.theme.ProPlusColors
import java.time.LocalDate

@Composable
fun DiseaseAddDialog(
    onDismiss: () -> Unit,
    onSave: (
        name: String,
        icd: String,
        status: DiseaseStatus,
        diagnosed: String,
        note: String,
        codingSystem: String,
    ) -> Unit,
) {
    val context = LocalContext.current
    val catalog = remember {
        runCatching { DiseaseCatalogLoader.get(context) }.getOrNull()
    }
    var query by remember { mutableStateOf("") }
    var systemFilter by remember { mutableStateOf<DiseaseCatalog.System?>(null) }
    var selected by remember { mutableStateOf<DiseaseCatalog.Hit?>(null) }
    var status by remember { mutableStateOf(DiseaseStatus.AKTUALNA) }
    var diagnosed by remember { mutableStateOf(LocalDate.now().toString().take(7)) }
    var note by remember { mutableStateOf("") }
    val hits = remember(query, systemFilter, catalog) {
        val q = query.trim()
        if (catalog == null || q.length < 2) emptyList()
        else catalog.search(q, limit = 40, system = systemFilter)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .widthIn(max = 980.dp)
                .heightIn(max = 680.dp)
                .imePadding(),
            shape = RoundedCornerShape(18.dp),
            color = ProPlusColors.Bg,
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = systemFilter == null,
                        onClick = { systemFilter = null },
                        label = { Text("Wszystkie") },
                    )
                    FilterChip(
                        selected = systemFilter == DiseaseCatalog.System.ICD10,
                        onClick = { systemFilter = DiseaseCatalog.System.ICD10 },
                        label = { Text("ICD-10") },
                    )
                    FilterChip(
                        selected = systemFilter == DiseaseCatalog.System.ICD9,
                        onClick = { systemFilter = DiseaseCatalog.System.ICD9 },
                        label = { Text("ICD-9") },
                    )
                }
                if (catalog == null) {
                    Text(
                        "Nie wczytano katalogu ICD z aplikacji (assets/icd_pl.json.gz).",
                        color = ProPlusColors.ResultAlert,
                    )
                }
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    val sideBySide = maxWidth >= 640.dp
                    if (sideBySide) {
                        Row(
                            Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            DiseaseSearchPane(
                                query = query,
                                onQueryChange = { query = it.take(80) },
                                hits = hits,
                                selected = selected,
                                onSelect = { selected = it },
                                modifier = Modifier.weight(0.58f).fillMaxHeight(),
                            )
                            DiseaseDetailsPane(
                                selected = selected,
                                status = status,
                                onStatus = { status = it },
                                diagnosed = diagnosed,
                                onDiagnosed = { diagnosed = it.take(10) },
                                note = note,
                                onNote = { note = it.take(80) },
                                onSave = {
                                    val hit = selected ?: return@DiseaseDetailsPane
                                    onSave(
                                        hit.name,
                                        hit.code,
                                        status,
                                        diagnosed,
                                        note,
                                        hit.systemTag,
                                    )
                                },
                                onClear = { selected = null },
                                modifier = Modifier.weight(0.42f).fillMaxHeight(),
                            )
                        }
                    } else {
                        Column(
                            Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            DiseaseSearchPane(
                                query = query,
                                onQueryChange = { query = it.take(80) },
                                hits = hits,
                                selected = selected,
                                onSelect = { selected = it },
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                            )
                            DiseaseDetailsPane(
                                selected = selected,
                                status = status,
                                onStatus = { status = it },
                                diagnosed = diagnosed,
                                onDiagnosed = { diagnosed = it.take(10) },
                                note = note,
                                onNote = { note = it.take(80) },
                                onSave = {
                                    val hit = selected ?: return@DiseaseDetailsPane
                                    onSave(
                                        hit.name,
                                        hit.code,
                                        status,
                                        diagnosed,
                                        note,
                                        hit.systemTag,
                                    )
                                },
                                onClear = { selected = null },
                                modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp),
                            )
                        }
                    }
                }
                val meta = catalog?.meta
                Text(
                    if (meta != null) {
                        "Katalog ICD (NFZ ICD-9 + ICD-10 PL) · ${meta.count} pozycji · stan ${meta.asOf}"
                    } else {
                        "Katalog ICD niedostępny"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = ProPlusColors.Muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun DiseaseSearchPane(
    query: String,
    onQueryChange: (String) -> Unit,
    hits: List<DiseaseCatalog.Hit>,
    selected: DiseaseCatalog.Hit?,
    onSelect: (DiseaseCatalog.Hit) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            label = { Text("Kod lub nazwa (PL)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Text(
            "Katalog NFZ (ICD-9) + ICD-10 PL. Wpisz ≥2 znaki.",
            style = MaterialTheme.typography.bodyMedium,
            color = ProPlusColors.Muted,
        )
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (hits.isEmpty() && query.trim().length >= 2) {
                item { Text("Brak wyników.", color = ProPlusColors.Muted) }
            }
            items(hits, key = { "${it.system.name}:${it.code}" }) { hit ->
                val isSelected = selected?.code == hit.code && selected.system == hit.system
                Surface(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(hit) },
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) ProPlusColors.Mist else ProPlusColors.Surface,
                ) {
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                        Text(
                            "${hit.systemTag} ${hit.code}",
                            fontWeight = FontWeight.Bold,
                            color = ProPlusColors.Navy,
                        )
                        Text(
                            hit.name,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DiseaseDetailsPane(
    selected: DiseaseCatalog.Hit?,
    status: DiseaseStatus,
    onStatus: (DiseaseStatus) -> Unit,
    diagnosed: String,
    onDiagnosed: (String) -> Unit,
    note: String,
    onNote: (String) -> Unit,
    onSave: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = ProPlusColors.Surface,
    ) {
        Column(
            Modifier
                .padding(12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (selected == null) {
                Text(
                    "Wybierz kod ICD z listy po lewej.",
                    color = ProPlusColors.Muted,
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                Text(
                    selected.label,
                    style = MaterialTheme.typography.titleMedium,
                    color = ProPlusColors.Navy,
                    fontWeight = FontWeight.SemiBold,
                )
                TextButton(onClick = onClear) { Text("Zmień kod") }
                Text("Rodzaj", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DiseaseStatus.entries.forEach { st ->
                        FilterChip(
                            selected = status == st,
                            onClick = { onStatus(st) },
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
                    onValueChange = onDiagnosed,
                    label = { Text("Data diagnozy (RRRR-MM)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = onNote,
                    label = { Text("Notatka (opcjonalnie)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) {
                    Text("Zapisz")
                }
            }
        }
    }
}
