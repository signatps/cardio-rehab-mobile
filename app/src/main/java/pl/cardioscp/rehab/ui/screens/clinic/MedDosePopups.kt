package pl.cardioscp.rehab.ui.screens.clinic

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import pl.cardioscp.rehab.clinic.DoseSkipReasons
import pl.cardioscp.rehab.clinic.MedCatalog
import pl.cardioscp.rehab.ui.theme.ProPlusColors

@Composable
fun MedSkipReasonDialog(
    onDismiss: () -> Unit,
    onConfirm: (reason: String) -> Unit,
) {
    var reason by remember { mutableStateOf("") }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .widthIn(max = 480.dp),
            shape = RoundedCornerShape(18.dp),
            color = ProPlusColors.Bg,
        ) {
            Column(
                Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    "Powód pominięcia",
                    style = MaterialTheme.typography.headlineMedium,
                    color = ProPlusColors.Navy,
                )
                Text(
                    "Wybierz ze słownika — bez powodu nie zapiszę pominięcia.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ProPlusColors.Muted,
                )
                DoseSkipReasons.ALL.forEach { item ->
                    FilterChip(
                        selected = reason == item,
                        onClick = { reason = item },
                        label = { Text(item, maxLines = 2) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text("Anuluj")
                    }
                    Button(
                        onClick = { onConfirm(reason) },
                        enabled = reason.isNotBlank(),
                        modifier = Modifier.weight(1f),
                    ) { Text("Zapisz") }
                }
            }
        }
    }
}

@Composable
fun MedLaterTimeDialog(
    initialTime: String,
    onDismiss: () -> Unit,
    onConfirm: (hhmm: String) -> Unit,
) {
    var draft by remember { mutableStateOf(initialTime) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .widthIn(max = 420.dp),
            shape = RoundedCornerShape(18.dp),
            color = ProPlusColors.Bg,
        ) {
            Column(
                Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    "Później — nowa godzina",
                    style = MaterialTheme.typography.headlineMedium,
                    color = ProPlusColors.Navy,
                )
                Text(
                    "Ustal o której przypomnieć ponownie.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ProPlusColors.Muted,
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    MedCatalog.PRESET_TIMES.forEach { slot ->
                        FilterChip(
                            selected = draft == slot,
                            onClick = { draft = slot },
                            label = { Text(slot) },
                        )
                    }
                }
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it.take(5) },
                    label = { Text("Godzina HH:MM") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text("Anuluj")
                    }
                    Button(
                        onClick = { onConfirm(draft) },
                        modifier = Modifier.weight(1f),
                    ) { Text("Ustaw") }
                }
            }
        }
    }
}
