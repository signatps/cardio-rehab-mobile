package pl.cardioscp.rehab.ui.screens.clinic

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pl.cardioscp.rehab.clinic.ClinicSnapshot
import pl.cardioscp.rehab.clinic.VitalKind
import pl.cardioscp.rehab.ui.components.AnalogGauge
import pl.cardioscp.rehab.ui.theme.ProPlusColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MeasurementsScreen(clinic: ClinicSnapshot) {
    var filter by remember { mutableStateOf<VitalKind?>(null) }
    val items = clinic.measurements.filter { filter == null || it.kind == filter }
    val latestBpSys = clinic.measurements
        .firstOrNull { it.kind == VitalKind.BLOOD_PRESSURE }
        ?.valueText?.substringBefore("/")?.toFloatOrNull()
    val timeFmt = remember { SimpleDateFormat("d.MM.yyyy HH:mm", Locale("pl")) }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Pomiary", style = MaterialTheme.typography.headlineMedium, color = ProPlusColors.Navy)
        AnalogGauge(
            value = latestBpSys,
            minValue = 80f,
            maxValue = 200f,
            label = "Ostatnie SYS",
            unit = "mmHg",
            accent = ProPlusColors.AccentBright,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = filter == null, onClick = { filter = null }, label = { Text("Wszystkie") })
            VitalKind.entries.forEach { k ->
                FilterChip(
                    selected = filter == k,
                    onClick = { filter = k },
                    label = {
                        Text(
                            when (k) {
                                VitalKind.BLOOD_PRESSURE -> "Ciśnienie"
                                VitalKind.WEIGHT -> "Waga"
                                VitalKind.SPO2 -> "SpO₂"
                                VitalKind.PULSE -> "Tętno"
                            },
                        )
                    },
                )
            }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(items, key = { it.id }) { m ->
                Surface(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, ProPlusColors.Line),
                    color = ProPlusColors.Surface,
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(m.label, style = MaterialTheme.typography.labelLarge, color = ProPlusColors.Muted)
                        Text(
                            m.valueText,
                            style = MaterialTheme.typography.titleLarge,
                            color = ProPlusColors.Navy,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            timeFmt.format(Date(m.measuredAtMs)),
                            style = MaterialTheme.typography.bodyMedium,
                            color = ProPlusColors.Muted,
                        )
                    }
                }
            }
        }
    }
}
