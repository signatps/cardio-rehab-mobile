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
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.dp
import pl.cardioscp.rehab.clinic.ClinicSnapshot
import pl.cardioscp.rehab.clinic.DoseStatus
import pl.cardioscp.rehab.clinic.MedCatalog
import pl.cardioscp.rehab.host.MedCatalogLoader
import pl.cardioscp.rehab.ui.theme.ProPlusColors
import java.time.format.DateTimeFormatter

@Composable
fun MedsScreen(
    clinic: ClinicSnapshot,
    onConfirmTaken: (String) -> Unit,
    onSkip: (id: String, reason: String) -> Unit,
    onSnooze: (id: String, hhmm: String) -> Unit,
    onAddMedication: (name: String, dose: String, times: List<String>, note: String) -> Unit = { _, _, _, _ -> },
    onRemoveMedication: (String) -> Unit = {},
) {
    val context = LocalContext.current
    var addOpen by remember { mutableStateOf(false) }
    var skipDoseId by remember { mutableStateOf<String?>(null) }
    var laterDoseId by remember { mutableStateOf<String?>(null) }
    var catalogMeta by remember {
        mutableStateOf(
            runCatching { MedCatalogLoader.get(context).meta }.getOrNull(),
        )
    }
    var catalogBusy by remember { mutableStateOf(false) }
    var catalogMessage by remember { mutableStateOf<String?>(null) }
    val timeFmt = DateTimeFormatter.ofPattern("HH:mm")
    val dateFmt = DateTimeFormatter.ofPattern("d.MM.yyyy")
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
            Column {
                Text("Leki", style = MaterialTheme.typography.headlineMedium, color = ProPlusColors.Navy)
                Text(
                    "Dziś · ${clinic.planDate.format(dateFmt)}",
                    style = MaterialTheme.typography.labelLarge,
                    color = ProPlusColors.Muted,
                )
            }
            Button(onClick = { addOpen = true }) { Text("Dodaj lek") }
        }
        CatalogMetaRow(
            meta = catalogMeta,
            busy = catalogBusy,
            message = catalogMessage,
            onRefresh = {
                catalogBusy = true
                catalogMessage = null
                runCatching {
                    val idx = MedCatalogLoader.reloadFromAssets(context)
                    catalogMeta = idx.meta
                    catalogMessage =
                        "Katalog RPL z aplikacji: ${idx.meta.count} pozycji" +
                            idx.meta.asOf.takeIf { it.isNotBlank() }?.let { " · stan $it" }.orEmpty()
                }.onFailure {
                    catalogMessage = "Nie udało się odświeżyć katalogu: ${it.message ?: "błąd"}"
                }
                catalogBusy = false
            },
        )
        Text("Dawki na dziś", style = MaterialTheme.typography.titleLarge, color = ProPlusColors.Navy)
        if (clinic.todayDoses.isEmpty()) {
            Text("Brak dawek na dziś.", color = ProPlusColors.Muted)
        }
        clinic.todayDoses.forEach { dose ->
            val accent = when (dose.status) {
                DoseStatus.TAKEN -> ProPlusColors.ResultGood
                DoseStatus.SKIPPED -> ProPlusColors.ResultAlert
                DoseStatus.SNOOZED -> ProPlusColors.ResultWatch
                DoseStatus.PENDING -> ProPlusColors.Accent
            }
            Surface(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.5.dp, accent.copy(alpha = 0.55f)),
                color = when (dose.status) {
                    DoseStatus.TAKEN -> ProPlusColors.Mist
                    DoseStatus.SKIPPED -> ProPlusColors.Bg
                    DoseStatus.SNOOZED -> ToneLateSoft
                    DoseStatus.PENDING -> ProPlusColors.Surface
                },
            ) {
                Column(
                    Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
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
                        Surface(
                            color = accent.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Text(
                                when (dose.status) {
                                    DoseStatus.TAKEN -> "przyjęte"
                                    DoseStatus.SKIPPED -> "pominięte"
                                    DoseStatus.SNOOZED -> "później"
                                    DoseStatus.PENDING -> "do potwierdzenia"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = accent,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            )
                        }
                    }
                    if (dose.status == DoseStatus.SKIPPED && !dose.skipReason.isNullOrBlank()) {
                        Text(
                            "Powód: ${dose.skipReason}",
                            style = MaterialTheme.typography.labelMedium,
                            color = ProPlusColors.ResultAlert,
                        )
                    }
                    if (dose.needsAction) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { onConfirmTaken(dose.id) }) {
                                Text("Przyjęte")
                            }
                            OutlinedButton(onClick = { skipDoseId = dose.id }) {
                                Text("Pominięte")
                            }
                            OutlinedButton(onClick = { laterDoseId = dose.id }) {
                                Text("Później")
                            }
                        }
                    }
                }
            }
        }
        Text("Lista leków", style = MaterialTheme.typography.titleLarge, color = ProPlusColors.Navy)
        if (clinic.medications.isEmpty()) {
            Text(
                "Brak leków na tym profilu. Dodaj lek z katalogu RPL (MZ).",
                color = ProPlusColors.Muted,
            )
        }
        clinic.medications.forEach { med ->
            Surface(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, ProPlusColors.Line),
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(med.name, style = MaterialTheme.typography.titleMedium, color = ProPlusColors.Navy)
                    Text(
                        "${med.doseLabel} · ${med.scheduleNote}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ProPlusColors.Muted,
                    )
                    TextButton(onClick = { onRemoveMedication(med.id) }) { Text("Usuń") }
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
    skipDoseId?.let { id ->
        MedSkipReasonDialog(
            onDismiss = { skipDoseId = null },
            onConfirm = { reason ->
                onSkip(id, reason)
                skipDoseId = null
            },
        )
    }
    laterDoseId?.let { id ->
        val initial = clinic.todayDoses.find { it.id == id }?.time?.format(timeFmt) ?: "12:00"
        MedLaterTimeDialog(
            initialTime = initial,
            onDismiss = { laterDoseId = null },
            onConfirm = { hhmm ->
                onSnooze(id, hhmm)
                laterDoseId = null
            },
        )
    }
}

private val ToneLateSoft = androidx.compose.ui.graphics.Color(0xFFFFF8E1)

@Composable
private fun CatalogMetaRow(
    meta: MedCatalog.Meta?,
    busy: Boolean,
    message: String?,
    onRefresh: () -> Unit,
) {
    Surface(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, ProPlusColors.Line),
        color = ProPlusColors.Surface,
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                if (meta == null) {
                    "Katalog RPL niedostępny"
                } else {
                    "Katalog RPL (MZ) · ${meta.count} pozycji" +
                        meta.asOf.takeIf { it.isNotBlank() }?.let { " · stan $it" }.orEmpty()
                },
                style = MaterialTheme.typography.bodyMedium,
                color = ProPlusColors.Navy,
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = onRefresh,
                    enabled = !busy,
                ) {
                    Text(if (busy) "Odświeżanie…" else "Odśwież katalog")
                }
                Text(
                    "Zaszyty w aplikacji; opcjonalnie przeładuj z APK.",
                    style = MaterialTheme.typography.bodySmall,
                    color = ProPlusColors.Muted,
                    modifier = Modifier.weight(1f),
                )
            }
            if (message != null) {
                Text(message, style = MaterialTheme.typography.bodySmall, color = ProPlusColors.Muted)
            }
        }
    }
}
