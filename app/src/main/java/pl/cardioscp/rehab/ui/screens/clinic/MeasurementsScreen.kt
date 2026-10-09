package pl.cardioscp.rehab.ui.screens.clinic

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Bloodtype
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.MonitorWeight
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pl.cardioscp.rehab.clinic.ClinicMeasurement
import pl.cardioscp.rehab.clinic.ClinicSnapshot
import pl.cardioscp.rehab.clinic.VitalKind
import pl.cardioscp.rehab.ui.theme.ProPlusColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class MeasurementsMode {
    /** Admin — pełna historia i filtry. */
    FULL,
    /** Pacjent — piktogramy DSD + rejestracja pomiarów + historia. */
    PATIENT,
    /** Lekarz — tylko wyniki z sesji rehab. */
    DOCTOR_REHAB_ONLY,
}

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

/** Typy z rejestracją na koncie pacjenta (jak DSD: BP, masa, saturacja/SpO₂, glikemia). */
private val patientRegisterKinds = listOf(
    VitalKind.BLOOD_PRESSURE,
    VitalKind.WEIGHT,
    VitalKind.SPO2,
    VitalKind.GLYCEMIA,
)

@Composable
fun MeasurementsScreen(
    clinic: ClinicSnapshot,
    mode: MeasurementsMode = MeasurementsMode.FULL,
    onMeasureBp: () -> Unit = {},
    onMeasureWeight: () -> Unit = {},
    onMeasureSpo2: () -> Unit = {},
    onMeasureGlycemia: () -> Unit = {},
) {
    val patientKinds = patientRegisterKinds.toSet()
    val baseMeasurements = remember(clinic.measurements, mode) {
        when (mode) {
            MeasurementsMode.FULL -> clinic.measurements
            MeasurementsMode.PATIENT -> clinic.measurements.filter {
                it.kind in patientKinds || it.sessionGroupId != null
            }
            MeasurementsMode.DOCTOR_REHAB_ONLY -> clinic.measurements.filter {
                it.sessionGroupId != null
            }
        }
    }
    var filter by remember(mode) {
        mutableStateOf(
            when (mode) {
                MeasurementsMode.DOCTOR_REHAB_ONLY -> MeasureFilter.RehabSessions
                else -> MeasureFilter.All
            },
        )
    }
    val timeFmt = remember { SimpleDateFormat("d.MM.yyyy HH:mm", Locale("pl")) }

    val sessionBuckets = remember(baseMeasurements) {
        baseMeasurements
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
    val standalone = remember(baseMeasurements, mode) {
        val rows = baseMeasurements.filter { it.sessionGroupId == null }
        when (mode) {
            MeasurementsMode.PATIENT -> rows.filter { it.kind in patientKinds }
            MeasurementsMode.DOCTOR_REHAB_ONLY -> emptyList()
            MeasurementsMode.FULL -> rows
        }
    }
    val kindChips = when (mode) {
        MeasurementsMode.FULL -> VitalKind.entries.toList()
        MeasurementsMode.PATIENT -> patientRegisterKinds
        MeasurementsMode.DOCTOR_REHAB_ONLY -> emptyList()
    }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            when (mode) {
                MeasurementsMode.DOCTOR_REHAB_ONLY -> "Wyniki sesji rehab"
                else -> "Pomiary"
            },
            style = MaterialTheme.typography.headlineMedium,
            color = ProPlusColors.Navy,
        )

        if (mode == MeasurementsMode.PATIENT) {
            Text(
                "Zarejestruj pomiar lub przejrzyj historię.",
                style = MaterialTheme.typography.bodyMedium,
                color = ProPlusColors.Muted,
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                patientRegisterKinds.forEach { kind ->
                    val latest = clinic.measurements.firstOrNull {
                        it.kind == kind && it.sessionGroupId == null
                    } ?: clinic.measurements.firstOrNull { it.kind == kind }
                    PatientMeasureTile(
                        kind = kind,
                        latestValue = latest?.valueText,
                        onMeasure = {
                            when (kind) {
                                VitalKind.BLOOD_PRESSURE -> onMeasureBp()
                                VitalKind.WEIGHT -> onMeasureWeight()
                                VitalKind.SPO2 -> onMeasureSpo2()
                                VitalKind.GLYCEMIA -> onMeasureGlycemia()
                                else -> Unit
                            }
                        },
                        onFilter = { filter = MeasureFilter.Kind(kind) },
                        modifier = Modifier.widthIn(min = 168.dp, max = 220.dp),
                    )
                }
            }
        }

        if (mode != MeasurementsMode.DOCTOR_REHAB_ONLY) {
            // Piktogramy filtrów jak MeasurementTypeFilterRow w mobile-DSD.
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TypePictogramChip(
                    selected = filter is MeasureFilter.All,
                    contentDescription = "Wszystkie pomiary",
                    onClick = { filter = MeasureFilter.All },
                ) {
                    Icon(
                        Icons.Outlined.Apps,
                        contentDescription = null,
                        tint = if (filter is MeasureFilter.All) Color.White else ProPlusColors.Navy,
                        modifier = Modifier.size(22.dp),
                    )
                }
                if (mode == MeasurementsMode.FULL) {
                    TypePictogramChip(
                        selected = filter is MeasureFilter.RehabSessions,
                        contentDescription = "Sesje rehab",
                        onClick = { filter = MeasureFilter.RehabSessions },
                    ) {
                        Icon(
                            Icons.Outlined.Timeline,
                            contentDescription = null,
                            tint = if (filter is MeasureFilter.RehabSessions) {
                                Color.White
                            } else {
                                ProPlusColors.Navy
                            },
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
                kindChips.forEach { k ->
                    val selected = filter == MeasureFilter.Kind(k)
                    TypePictogramChip(
                        selected = selected,
                        contentDescription = k.shortLabel(),
                        onClick = { filter = MeasureFilter.Kind(k) },
                    ) {
                        VitalKindPictogram(
                            kind = k,
                            tint = if (selected) Color.White else ProPlusColors.Navy,
                            iconSize = 18.dp,
                        )
                    }
                }
            }
        }

        when (val f = filter) {
            is MeasureFilter.All -> {
                if (sessionBuckets.isEmpty() && standalone.isEmpty()) {
                    Text(
                        "Brak pomiarów. Historia pojawi się po wykonaniu pomiaru lub sesji.",
                        color = ProPlusColors.Muted,
                    )
                } else {
                    HistoryHeader()
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(0.dp),
                        contentPadding = PaddingValues(bottom = 16.dp),
                        modifier = Modifier.weight(1f, fill = true),
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
            }
            is MeasureFilter.RehabSessions -> {
                if (sessionBuckets.isEmpty()) {
                    Text("Brak sesji rehabilitacji z badaniami.", color = ProPlusColors.Muted)
                } else {
                    HistoryHeader()
                    LazyColumn(
                        contentPadding = PaddingValues(bottom = 16.dp),
                        modifier = Modifier.weight(1f, fill = true),
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
                    }
                }
            }
            is MeasureFilter.Kind -> {
                val rows = baseMeasurements.filter { it.kind == f.kind }
                if (rows.isEmpty()) {
                    Text("Brak pomiarów w tym filtrze.", color = ProPlusColors.Muted)
                } else {
                    HistoryHeader()
                    LazyColumn(
                        contentPadding = PaddingValues(bottom = 16.dp),
                        modifier = Modifier.weight(1f, fill = true),
                    ) {
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
private fun PatientMeasureTile(
    kind: VitalKind,
    latestValue: String?,
    onMeasure: () -> Unit,
    onFilter: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.heightIn(min = 88.dp),
        shape = RoundedCornerShape(12.dp),
        color = ProPlusColors.Surface,
        border = BorderStroke(1.dp, ProPlusColors.Line),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                Modifier
                    .weight(1f)
                    .clickable(onClick = onFilter),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    Modifier
                        .size(40.dp)
                        .background(ProPlusColors.Accent.copy(alpha = 0.14f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    VitalKindPictogram(
                        kind = kind,
                        tint = ProPlusColors.Accent,
                        iconSize = 18.dp,
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        kind.shortLabel(),
                        style = MaterialTheme.typography.labelLarge,
                        color = ProPlusColors.Muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        latestValue ?: "—",
                        style = MaterialTheme.typography.titleSmall,
                        color = ProPlusColors.Navy,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Button(
                onClick = onMeasure,
                modifier = Modifier.heightIn(min = 36.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ProPlusColors.Accent),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text("Pomiar", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun TypePictogramChip(
    selected: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        Modifier
            .heightIn(min = 48.dp)
            .widthIn(min = 48.dp)
            .background(
                if (selected) ProPlusColors.Accent else ProPlusColors.Surface,
                RoundedCornerShape(12.dp),
            )
            .clickable(onClick = onClick)
            .semantics { this.contentDescription = contentDescription }
            .padding(horizontal = 4.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun VitalKindPictogram(
    kind: VitalKind,
    tint: Color,
    iconSize: androidx.compose.ui.unit.Dp,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        Icon(
            imageVector = kind.pictogram(),
            contentDescription = kind.shortLabel(),
            tint = tint,
            modifier = Modifier.size(iconSize),
        )
        Text(
            kind.abbr(),
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 9.sp,
            ),
            color = tint,
            maxLines = 1,
        )
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
                m.kind.listLabel(),
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
    VitalKind.WEIGHT -> "Masa"
    VitalKind.SPO2 -> "SpO₂"
    VitalKind.GLYCEMIA -> "Glikemia"
    VitalKind.PULSE -> "Tętno"
    VitalKind.ECG -> "EKG"
}

private fun VitalKind.listLabel(): String = when (this) {
    VitalKind.BLOOD_PRESSURE -> "Ciśnienie"
    VitalKind.WEIGHT -> "Masa ciała"
    VitalKind.SPO2 -> "Saturacja/SpO₂"
    VitalKind.GLYCEMIA -> "Glikemia"
    VitalKind.PULSE -> "Tętno"
    VitalKind.ECG -> "EKG"
}

private fun VitalKind.abbr(): String = when (this) {
    VitalKind.BLOOD_PRESSURE -> "BP"
    VitalKind.WEIGHT -> "kg"
    VitalKind.SPO2 -> "O2"
    VitalKind.GLYCEMIA -> "Glu"
    VitalKind.PULSE -> "PR"
    VitalKind.ECG -> "EKG"
}

private fun VitalKind.pictogram(): ImageVector = when (this) {
    VitalKind.BLOOD_PRESSURE -> Icons.Outlined.MonitorHeart
    VitalKind.WEIGHT -> Icons.Outlined.MonitorWeight
    VitalKind.SPO2 -> Icons.Outlined.Bloodtype
    VitalKind.GLYCEMIA -> Icons.Outlined.WaterDrop
    VitalKind.PULSE -> Icons.Outlined.FavoriteBorder
    VitalKind.ECG -> Icons.Outlined.Timeline
}
