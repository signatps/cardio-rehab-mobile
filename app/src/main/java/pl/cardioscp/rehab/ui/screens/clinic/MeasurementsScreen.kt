package pl.cardioscp.rehab.ui.screens.clinic

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bloodtype
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.MonitorWeight
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pl.cardioscp.rehab.clinic.ClinicMeasurement
import pl.cardioscp.rehab.clinic.ClinicSnapshot
import pl.cardioscp.rehab.clinic.VitalKind
import pl.cardioscp.rehab.ui.theme.ProPlusColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private sealed class MeasureFilter {
    data object All : MeasureFilter()
    data object RehabSessions : MeasureFilter()
    data class Kind(val kind: VitalKind) : MeasureFilter()
}

private data class SessionBucket(
    val id: String,
    val title: String,
    val measuredAtMs: Long,
    val items: List<ClinicMeasurement>,
)

@Composable
fun MeasurementsScreen(clinic: ClinicSnapshot) {
    var filter by remember { mutableStateOf<MeasureFilter>(MeasureFilter.All) }
    val timeFmt = remember { SimpleDateFormat("d.MM.yyyy HH:mm", Locale("pl")) }

    val sessionBuckets = remember(clinic.measurements) {
        clinic.measurements
            .filter { it.sessionGroupId != null }
            .groupBy { it.sessionGroupId!! }
            .map { (id, items) ->
                SessionBucket(
                    id = id,
                    title = items.first().sessionGroupTitle ?: "Sesja rehab",
                    measuredAtMs = items.maxOf { it.measuredAtMs },
                    items = items.sortedBy { it.measuredAtMs },
                )
            }
            .sortedByDescending { it.measuredAtMs }
    }
    val standalone = remember(clinic.measurements) {
        clinic.measurements.filter { it.sessionGroupId == null }
    }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Pomiary", style = MaterialTheme.typography.headlineMedium, color = ProPlusColors.Navy)
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = filter is MeasureFilter.All,
                onClick = { filter = MeasureFilter.All },
                label = { Text("Wszystkie") },
            )
            FilterChip(
                selected = filter is MeasureFilter.RehabSessions,
                onClick = { filter = MeasureFilter.RehabSessions },
                label = { Text("Sesje rehab") },
            )
            VitalKind.entries.forEach { k ->
                FilterChip(
                    selected = filter == MeasureFilter.Kind(k),
                    onClick = { filter = MeasureFilter.Kind(k) },
                    label = { Text(k.shortLabel()) },
                    leadingIcon = {
                        Icon(k.pictogram(), contentDescription = null, modifier = Modifier.size(18.dp))
                    },
                )
            }
        }

        when (val f = filter) {
            is MeasureFilter.All -> {
                HistoryHeader()
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                    contentPadding = PaddingValues(bottom = 16.dp),
                ) {
                    sessionBuckets.forEach { bucket ->
                        item(key = "g-${bucket.id}") {
                            SessionGroupHeader(bucket = bucket, timeFmt = timeFmt)
                        }
                        items(bucket.items, key = { it.id }) { m ->
                            MeasurementRow(m = m, timeFmt = timeFmt, indented = true)
                        }
                        item(key = "d-${bucket.id}") {
                            HorizontalDivider(
                                Modifier.padding(vertical = 6.dp),
                                color = ProPlusColors.Line,
                            )
                        }
                    }
                    if (standalone.isNotEmpty()) {
                        item(key = "standalone-h") {
                            Text(
                                "Pomiary poza sesją",
                                style = MaterialTheme.typography.titleMedium,
                                color = ProPlusColors.Navy,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(vertical = 6.dp),
                            )
                        }
                        items(standalone, key = { it.id }) { m ->
                            MeasurementRow(m = m, timeFmt = timeFmt, indented = false)
                        }
                    }
                }
            }
            is MeasureFilter.RehabSessions -> {
                if (sessionBuckets.isEmpty()) {
                    Text("Brak sesji rehabilitacji z badaniami.", color = ProPlusColors.Muted)
                } else {
                    HistoryHeader()
                    LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                        sessionBuckets.forEach { bucket ->
                            item(key = "g-${bucket.id}") {
                                SessionGroupHeader(bucket = bucket, timeFmt = timeFmt)
                            }
                            items(bucket.items, key = { it.id }) { m ->
                                MeasurementRow(m = m, timeFmt = timeFmt, indented = true)
                            }
                            item(key = "d-${bucket.id}") {
                                HorizontalDivider(
                                    Modifier.padding(vertical = 6.dp),
                                    color = ProPlusColors.Line,
                                )
                            }
                        }
                    }
                }
            }
            is MeasureFilter.Kind -> {
                val rows = clinic.measurements.filter { it.kind == f.kind }
                if (rows.isEmpty()) {
                    Text("Brak pomiarów w tym filtrze.", color = ProPlusColors.Muted)
                } else {
                    HistoryHeader()
                    LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                        items(rows, key = { it.id }) { m ->
                            MeasurementRow(m = m, timeFmt = timeFmt, indented = false)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryHeader() {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Data",
            style = MaterialTheme.typography.labelLarge,
            color = ProPlusColors.Muted,
            modifier = Modifier.weight(1f),
        )
        Text(
            "Typ",
            style = MaterialTheme.typography.labelLarge,
            color = ProPlusColors.Muted,
            modifier = Modifier.weight(1f),
        )
        Text(
            "Wartość",
            style = MaterialTheme.typography.labelLarge,
            color = ProPlusColors.Muted,
            modifier = Modifier.weight(1.4f),
        )
    }
}

@Composable
private fun SessionGroupHeader(bucket: SessionBucket, timeFmt: SimpleDateFormat) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            Icons.Outlined.Timeline,
            contentDescription = null,
            tint = ProPlusColors.Accent,
            modifier = Modifier.size(22.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                bucket.title,
                style = MaterialTheme.typography.titleMedium,
                color = ProPlusColors.Navy,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "${timeFmt.format(Date(bucket.measuredAtMs))} · ${bucket.items.size} badań",
                style = MaterialTheme.typography.bodyMedium,
                color = ProPlusColors.Muted,
            )
        }
    }
}

@Composable
private fun MeasurementRow(
    m: ClinicMeasurement,
    timeFmt: SimpleDateFormat,
    indented: Boolean,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(
                start = if (indented) 16.dp else 4.dp,
                end = 4.dp,
                top = 8.dp,
                bottom = 8.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            timeFmt.format(Date(m.measuredAtMs)),
            style = MaterialTheme.typography.bodyMedium,
            color = ProPlusColors.Ink,
            modifier = Modifier.weight(1f),
        )
        Row(
            Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                m.kind.pictogram(),
                contentDescription = null,
                tint = ProPlusColors.Navy,
                modifier = Modifier.size(22.dp),
            )
            Text(
                m.label,
                style = MaterialTheme.typography.bodyMedium,
                color = ProPlusColors.Navy,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
            )
        }
        Text(
            m.valueText,
            style = MaterialTheme.typography.titleMedium,
            color = ProPlusColors.Navy,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1.4f),
        )
    }
}

private fun VitalKind.shortLabel(): String = when (this) {
    VitalKind.BLOOD_PRESSURE -> "Ciśnienie"
    VitalKind.WEIGHT -> "Waga"
    VitalKind.SPO2 -> "SpO₂"
    VitalKind.PULSE -> "Tętno"
    VitalKind.ECG -> "EKG"
}

private fun VitalKind.pictogram(): ImageVector = when (this) {
    VitalKind.BLOOD_PRESSURE -> Icons.Outlined.MonitorHeart
    VitalKind.WEIGHT -> Icons.Outlined.MonitorWeight
    VitalKind.SPO2 -> Icons.Outlined.Bloodtype
    VitalKind.PULSE -> Icons.Outlined.FavoriteBorder
    VitalKind.ECG -> Icons.Outlined.Timeline
}
