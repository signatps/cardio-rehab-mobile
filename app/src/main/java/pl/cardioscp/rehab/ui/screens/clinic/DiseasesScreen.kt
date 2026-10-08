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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pl.cardioscp.rehab.clinic.ClinicSnapshot
import pl.cardioscp.rehab.clinic.Disease
import pl.cardioscp.rehab.clinic.DiseaseStatus
import pl.cardioscp.rehab.host.DiseaseCatalogLoader
import pl.cardioscp.rehab.host.DiseaseCatalogUpdater
import pl.cardioscp.rehab.ui.theme.ProPlusColors

@Composable
fun DiseasesScreen(
    clinic: ClinicSnapshot,
    onAddDisease: (
        name: String,
        icd: String,
        status: DiseaseStatus,
        diagnosed: String,
        note: String,
        codingSystem: String,
    ) -> Unit = { _, _, _, _, _, _ -> },
    onSetStatus: (id: String, status: DiseaseStatus) -> Unit = { _, _ -> },
    onRemove: (id: String) -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var addOpen by remember { mutableStateOf(false) }
    var catalogMeta by remember {
        mutableStateOf(
            runCatching { DiseaseCatalogLoader.get(context).meta }.getOrNull(),
        )
    }
    var catalogBusy by remember { mutableStateOf(false) }
    var catalogMessage by remember { mutableStateOf<String?>(null) }

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
        Text(
            "Rozpoznania ICD-10 (PL) i ICD-9 (NFZ).",
            style = MaterialTheme.typography.bodyMedium,
            color = ProPlusColors.Muted,
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(
                onClick = {
                    catalogBusy = true
                    catalogMessage = null
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            runCatching { DiseaseCatalogUpdater.refresh(context) }
                        }
                        result.onSuccess {
                            catalogMeta = it.index.meta
                            catalogMessage = it.message
                        }.onFailure {
                            catalogMessage = "Aktualizacja ICD nieudana: ${it.message ?: "sieć / NFZ"}"
                        }
                        catalogBusy = false
                    }
                },
                enabled = !catalogBusy,
            ) {
                Text(if (catalogBusy) "Aktualizacja…" else "Aktualizuj katalog")
            }
            TextButton(
                onClick = {
                    catalogBusy = true
                    catalogMessage = null
                    runCatching {
                        val idx = DiseaseCatalogLoader.reloadFromAssets(context)
                        catalogMeta = idx.meta
                        catalogMessage =
                            "Katalog ICD z aplikacji: ${idx.meta.count} pozycji" +
                                idx.meta.asOf.takeIf { it.isNotBlank() }?.let { " · stan $it" }.orEmpty()
                    }.onFailure {
                        catalogMessage = "Nie udało się odświeżyć: ${it.message ?: "błąd"}"
                    }
                    catalogBusy = false
                },
                enabled = !catalogBusy,
            ) { Text("Z APK") }
        }
        val meta = catalogMeta
        Text(
            if (meta != null) {
                "Katalog ICD · ${meta.count} pozycji · stan ${meta.asOf.ifBlank { "—" }}"
            } else {
                "Katalog ICD · nie wczytano (sprawdź assets/icd_pl.json.gz)"
            },
            style = MaterialTheme.typography.labelMedium,
            color = if (meta != null) ProPlusColors.Muted else ProPlusColors.ResultAlert,
        )
        catalogMessage?.let {
            Text(it, style = MaterialTheme.typography.labelMedium, color = ProPlusColors.Accent)
        }
        Text("Aktualne / przewlekłe", style = MaterialTheme.typography.titleLarge, color = ProPlusColors.Navy)
        val active = clinic.diseases.filter {
            it.status == DiseaseStatus.AKTUALNA || it.status == DiseaseStatus.PRZEWLEKLA
        }
        if (active.isEmpty()) {
            Text("Brak rozpoznań. Dodaj kod ICD z katalogu.", color = ProPlusColors.Muted)
        }
        active.forEach { d -> DiseaseCard(d, onSetStatus, onRemove) }
        Text("Historyczne", style = MaterialTheme.typography.titleLarge, color = ProPlusColors.Navy)
        val hist = clinic.diseases.filter { it.status == DiseaseStatus.HISTORYCZNA }
        if (hist.isEmpty()) {
            Text("Brak historycznych.", color = ProPlusColors.Muted)
        }
        hist.forEach { d -> DiseaseCard(d, onSetStatus, onRemove) }
    }
    if (addOpen) {
        DiseaseAddDialog(
            onDismiss = { addOpen = false },
            onSave = { name, icd, status, diagnosed, note, codingSystem ->
                onAddDisease(name, icd, status, diagnosed, note, codingSystem)
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
                Text(
                    "${d.codingSystem} ${d.icd}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ProPlusColors.Accent,
                    fontWeight = FontWeight.Bold,
                )
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
