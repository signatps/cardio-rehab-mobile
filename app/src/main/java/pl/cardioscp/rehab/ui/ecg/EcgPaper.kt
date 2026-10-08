package pl.cardioscp.rehab.ui.ecg

import android.graphics.Paint
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import pl.cardioscp.rehab.ecg.AveragePqr
import pl.cardioscp.rehab.ecg.EcgAnalysis
import pl.cardioscp.rehab.ecg.QrsCandidateDecision
import pl.cardioscp.rehab.ecg.QrsCandidateDiag
import pl.cardioscp.rehab.ecg.WaveMark
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.min

private val Paper = Color(0xFFFFFCF7)
private val Minor = Color(0xFFF3D2D2)
private val Major = Color(0xFFE7A091)
private val Trace = Color(0xFF1A1A1A)
private val Mark = EcgUiColors.Accent
private val Cecha = EcgUiColors.Navy
private val AxisInk = EcgUiColors.Navy

/** Wysokość rzędu odprowadzenia w mm papieru EKG. */
private const val RowMm = 26f
/** Szerokość etykiety odprowadzenia w mm. */
private const val LabelMm = 12f
/** Cecha 1 mV: szerokość impulsu kalibracyjnego w mm (przy 25 mm/s ≈ 0,2 s). */
private const val CechaWidthMm = 5f
private const val CechaMv = 1.0
/** Pas osi czasu pod przebiegami. */
private const val AxisMm = 8f

/**
 * Papier EKG w skali milimetrowej z osią czasu u dołu.
 *
 * Skala z wysokości wierszy (nie „ściskaj 30 s do szerokości”) — zapis dłuższy
 * niż okno da się przesuwać w poziomie. Pinch / +/− = zoom.
 */
@Composable
fun EcgPaper(
    leads: List<Pair<String, DoubleArray>>,
    samplingHz: Int,
    mmPerSec: Int,
    mmPerMv: Int,
    rPeaks: IntArray,
    showR: Boolean,
    modifier: Modifier = Modifier,
    followLive: Boolean = false,
    /** Automatyczne znakowanie PQRST na wybranym odprowadzeniu. */
    showWaveMarks: Boolean = false,
    analysisLeadLabel: String = "II",
    waveMarks: List<WaveMark> = emptyList(),
    /** Widok diagnostyczny: ticki QRS na I/II/V5 + odrzucone kandydaty. */
    showDiag: Boolean = false,
    detectionDiagnostics: List<QrsCandidateDiag> = emptyList(),
) {
    val density = LocalDensity.current
    val samples = leads.firstOrNull()?.second?.size ?: 0
    val durationSec = if (samplingHz == 0) 0f else samples / samplingHz.toFloat()
    val traceMm = durationSec * mmPerSec
    val contentMmW = LabelMm + CechaWidthMm + traceMm.coerceAtLeast(10f)
    val leadsMmH = (leads.size * RowMm).coerceAtLeast(RowMm)
    val contentMmH = leadsMmH + AxisMm
    val textPx = with(density) { 11.dp.toPx() }
    val labelPaint = remember(textPx) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#002660")
            textSize = textPx
        }
    }
    val axisPaint = remember(textPx) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#002660")
            textSize = textPx * 0.9f
            textAlign = Paint.Align.CENTER
        }
    }
    val cechaPaint = remember(textPx) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#002660")
            textSize = textPx * 0.85f
        }
    }
    var scroll by remember { mutableStateOf(Offset.Zero) }
    var wasFollowingLive by remember { mutableStateOf(false) }
    /** 1 = skala z wysokości wierszy; >1 powiększenie (pinch / +). */
    var userZoom by remember { mutableFloatStateOf(1f) }
    BoxWithConstraints(modifier) {
        val viewW = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val viewH = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        // Zawsze skala z wysokości — 30 s nie jest ściskane do szerokości (wtedy maxX=0 i brak pan).
        val basePx = (viewH / contentMmH).coerceIn(1.2f, 18f)
        val pxPerMm = (basePx * userZoom).coerceIn(1.0f, 28f)
        val contentW = contentMmW * pxPerMm
        val contentH = contentMmH * pxPerMm
        val maxX = (contentW - viewW).coerceAtLeast(0f)
        val maxY = (contentH - viewH).coerceAtLeast(0f)
        val labelPx = LabelMm * pxPerMm
        val cechaPx = CechaWidthMm * pxPerMm
        val traceLeft = labelPx + cechaPx
        val axisH = AxisMm * pxPerMm

        LaunchedEffect(followLive, samples, maxX, maxY, mmPerSec, mmPerMv, userZoom) {
            if (followLive && maxX > 0f) {
                wasFollowingLive = true
                scroll = Offset(x = maxX, y = scroll.y.coerceIn(0f, maxY))
            } else if (wasFollowingLive && !followLive) {
                wasFollowingLive = false
                scroll = Offset(x = 0f, y = scroll.y.coerceIn(0f, maxY))
            } else if (!followLive) {
                scroll = Offset(
                    scroll.x.coerceIn(0f, maxX),
                    scroll.y.coerceIn(0f, maxY),
                )
            }
        }

        Box(Modifier.fillMaxSize().clipToBounds()) {
            Canvas(
                Modifier
                    .fillMaxSize()
                    .clipToBounds()
                    .pointerInput(maxX, maxY, followLive, userZoom) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            if (!(followLive && maxX > 0f && zoom == 1f)) {
                                // Pinch = zoom; jeden palec / pan = przesuwanie.
                                if (zoom != 1f) {
                                    val oldZoom = userZoom
                                    userZoom = (userZoom * zoom).coerceIn(0.55f, 3.5f)
                                    // Kotwica zoomu w środku widoku.
                                    val ratio = userZoom / oldZoom
                                    val centerX = scroll.x + viewW / 2f
                                    val centerY = scroll.y + viewH / 2f
                                    val newMaxX = ((contentMmW * basePx * userZoom) - viewW).coerceAtLeast(0f)
                                    val newMaxY = ((contentMmH * basePx * userZoom) - viewH).coerceAtLeast(0f)
                                    scroll = Offset(
                                        (centerX * ratio - viewW / 2f).coerceIn(0f, newMaxX),
                                        (centerY * ratio - viewH / 2f).coerceIn(0f, newMaxY),
                                    )
                                }
                                if (!followLive || maxX <= 0f || zoom != 1f) {
                                    val newMaxX = ((contentMmW * basePx * userZoom) - viewW).coerceAtLeast(0f)
                                    val newMaxY = ((contentMmH * basePx * userZoom) - viewH).coerceAtLeast(0f)
                                    scroll = Offset(
                                        (scroll.x - pan.x).coerceIn(0f, newMaxX),
                                        (scroll.y - pan.y).coerceIn(0f, newMaxY),
                                    )
                                }
                            }
                        }
                    },
            ) {
            // Twarde przycięcie — bez tego siatka/markery przy pan w pionie wchodzą na pasek narzędzi.
            clipRect(left = 0f, top = 0f, right = size.width, bottom = size.height) {
            drawRect(Paper)
            val gridBottom = (leadsMmH * pxPerMm - scroll.y).coerceAtMost(size.height)
            val firstMm = floor(scroll.x / pxPerMm).toInt() - 1
            val lastMm = ceil((scroll.x + size.width) / pxPerMm).toInt() + 1
            for (mm in firstMm..lastMm) {
                val x = mm * pxPerMm - scroll.x
                if (x < labelPx) continue
                drawLine(
                    if (mm % 5 == 0) Major else Minor,
                    Offset(x, 0f),
                    Offset(x, gridBottom.coerceAtLeast(0f)),
                    strokeWidth = if (mm % 5 == 0) 1.4f else 0.7f,
                )
            }
            val rowPx = RowMm * pxPerMm
            val firstRow = floor(scroll.y / rowPx).toInt().coerceAtLeast(0)
            val lastRow = ceil((scroll.y + size.height) / rowPx).toInt().coerceAtMost(leads.lastIndex.coerceAtLeast(0))
            if (leads.isNotEmpty()) {
                for (row in firstRow..lastRow) {
                    val y0 = row * rowPx - scroll.y
                    for (mm in 0..RowMm.toInt()) {
                        val y = y0 + mm * pxPerMm
                        if (y < 0f || y > gridBottom) continue
                        drawLine(
                            if (mm % 5 == 0) Major else Minor,
                            Offset(labelPx, y),
                            Offset(size.width, y),
                            strokeWidth = if (mm % 5 == 0) 1.4f else 0.7f,
                        )
                    }
                }
            }
            val pxPerSample = mmPerSec * pxPerMm / samplingHz.coerceAtLeast(1)
            val cechaHeightPx = (CechaMv * mmPerMv * pxPerMm).toFloat()
            leads.forEachIndexed { index, (name, values) ->
                val leadColor = LeadColors.of(name)
                val rowTop = index * rowPx - scroll.y
                val rowBottom = rowTop + rowPx
                if (rowBottom < 0f || rowTop > size.height) return@forEachIndexed
                val base = rowTop + rowPx * 0.62f
                // Etykieta w kolorze odprowadzenia.
                drawRect(Paper, topLeft = Offset(0f, rowTop), size = Size(labelPx, rowPx))
                labelPaint.color = android.graphics.Color.argb(
                    255,
                    (leadColor.red * 255).toInt(),
                    (leadColor.green * 255).toInt(),
                    (leadColor.blue * 255).toInt(),
                )
                drawContext.canvas.nativeCanvas.drawText(
                    name,
                    6f,
                    base.coerceIn(rowTop + textPx, rowBottom - 2f),
                    labelPaint,
                )

                // Przebieg / cecha / markery wyłącznie w swoim wierszu siatki (zoom nie wychodzi poza mm).
                clipRect(
                    left = labelPx,
                    top = rowTop.coerceAtLeast(0f),
                    right = size.width,
                    bottom = rowBottom.coerceAtMost(gridBottom.coerceAtLeast(rowTop + 1f)),
                ) {
                    val cechaLeft = labelPx - scroll.x
                    if (cechaLeft + cechaPx > labelPx && cechaLeft < size.width) {
                        val path = Path().apply {
                            val y0 = base
                            val y1 = base - cechaHeightPx
                            val x0 = cechaLeft
                            val x1 = cechaLeft + cechaPx * 0.15f
                            val x2 = cechaLeft + cechaPx * 0.85f
                            val x3 = cechaLeft + cechaPx
                            moveTo(x0, y0)
                            lineTo(x1, y0)
                            lineTo(x1, y1)
                            lineTo(x2, y1)
                            lineTo(x2, y0)
                            lineTo(x3, y0)
                        }
                        drawPath(path, leadColor, style = Stroke(width = 1.8f, cap = StrokeCap.Square))
                        if (index == firstRow) {
                            cechaPaint.color = labelPaint.color
                            drawContext.canvas.nativeCanvas.drawText(
                                "1 mV",
                                (cechaLeft + 2f).coerceAtLeast(labelPx + 2f),
                                (base - cechaHeightPx - 4f).coerceAtLeast(rowTop + textPx),
                                cechaPaint,
                            )
                        }
                    }

                    if (values.isEmpty()) return@clipRect
                    val first = ((scroll.x - cechaPx - 40f) / pxPerSample).toInt().coerceIn(0, values.lastIndex)
                    val last = ((scroll.x + size.width) / pxPerSample).toInt().coerceIn(first, values.lastIndex)
                    val path = Path()
                    for (i in first..last) {
                        val x = traceLeft + i * pxPerSample - scroll.x
                        val y = (base - (values[i] * mmPerMv * pxPerMm).toFloat())
                            .coerceIn(rowTop + 0.5f, rowBottom - 0.5f)
                        if (i == first) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    drawPath(path, leadColor, style = Stroke(width = 1.7f, cap = StrokeCap.Round))
                    val isAnalysisLead = name == analysisLeadLabel
                    val diagLeads = setOf("I", "II", "V5")
                    val showRhythmTicks = (showR && isAnalysisLead) || (showDiag && name in diagLeads)
                    if (showRhythmTicks) {
                        // Marker zdarzenia QRS (rytm) — kreska; nie etykietuj jako „R”.
                        for (peak in rPeaks) {
                            val x = traceLeft + peak * pxPerSample - scroll.x
                            if (x in labelPx..size.width) {
                                drawLine(
                                    Color(0xFF1F6B4A),
                                    Offset(x, (base - 3f * pxPerMm).coerceIn(rowTop, rowBottom)),
                                    Offset(x, (base - 11f * pxPerMm).coerceIn(rowTop, rowBottom)),
                                    strokeWidth = 1.8f,
                                )
                            }
                        }
                    }
                    if (showDiag && name in diagLeads) {
                        for (cand in detectionDiagnostics) {
                            if (cand.decision == QrsCandidateDecision.Accepted) continue
                            if (cand.sourceLead.label != name &&
                                cand.confirmingLeads.none { it.label == name }
                            ) {
                                continue
                            }
                            val x = traceLeft + cand.sampleIndex * pxPerSample - scroll.x
                            if (x !in labelPx..size.width) continue
                            val color = when (cand.decision) {
                                QrsCandidateDecision.Rejected -> Color(0x66C62828)
                                QrsCandidateDecision.Uncertain -> Color(0x66F9A825)
                                else -> Color.Transparent
                            }
                            drawLine(
                                color,
                                Offset(x, (base - 2f * pxPerMm).coerceIn(rowTop, rowBottom)),
                                Offset(x, (base - 8f * pxPerMm).coerceIn(rowTop, rowBottom)),
                                strokeWidth = 1.2f,
                            )
                        }
                    }
                    if (showWaveMarks && isAnalysisLead && waveMarks.isNotEmpty()) {
                        val labelPaintLocal = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            textSize = textPx * 0.7f
                            textAlign = Paint.Align.CENTER
                        }
                        for (mark in waveMarks) {
                            fun dot(sample: Int?, label: String, color: Color) {
                                if (sample == null || sample !in values.indices) return
                                val x = traceLeft + sample * pxPerSample - scroll.x
                                if (x !in (labelPx - 8f)..(size.width + 8f)) return
                                val y = (base - (values[sample] * mmPerMv * pxPerMm).toFloat())
                                    .coerceIn(rowTop + 2f, rowBottom - 2f)
                                val r = 2.6f * pxPerMm / 3.5f
                                val radius = r.coerceIn(3.2f, 5.5f)
                                drawCircle(color, radius = radius, center = Offset(x, y))
                                drawCircle(Color.White, radius = radius * 0.35f, center = Offset(x, y))
                                labelPaintLocal.color = android.graphics.Color.argb(
                                    255,
                                    (color.red * 255).toInt(),
                                    (color.green * 255).toInt(),
                                    (color.blue * 255).toInt(),
                                )
                                drawContext.canvas.nativeCanvas.drawText(
                                    label,
                                    x,
                                    (y - radius - 2f).coerceAtLeast(rowTop + textPx * 0.7f),
                                    labelPaintLocal,
                                )
                            }
                            // qrsEvent ≠ anatomiczne R — nie dubluj jako „R”.
                            dot(mark.p, "P", Color(0xFF00A0D4))
                            dot(mark.q, "Q", Color(0xFF787A77))
                            dot(mark.r, "R", Color(0xFF007DC4))
                            dot(mark.s, "S", Color(0xFF4D4E4C))
                            dot(mark.t, "T", Color(0xFF002660))
                        }
                    }
                }
            }

            // Oś czasu u dołu — etykiety w sekundach, bez zniekształcania cechy.
            val axisTop = leadsMmH * pxPerMm - scroll.y
            if (axisTop < size.height) {
                drawRect(
                    Paper,
                    topLeft = Offset(0f, axisTop.coerceAtLeast(0f)),
                    size = Size(size.width, size.height - axisTop.coerceAtLeast(0f)),
                )
                drawLine(AxisInk, Offset(labelPx, axisTop), Offset(size.width, axisTop), strokeWidth = 1.4f)
                labelPaint.color = android.graphics.Color.parseColor("#002660")
                drawContext.canvas.nativeCanvas.drawText("t", 8f, axisTop + axisH * 0.7f, labelPaint)
                val stepSec = when {
                    mmPerSec >= 50 -> 0.5f
                    mmPerSec >= 25 -> 1f
                    mmPerSec >= 10 -> 2f
                    else -> 5f
                }
                val startSec = (scroll.x / (mmPerSec * pxPerMm)).coerceAtLeast(0f)
                val endSec = ((scroll.x + size.width) / (mmPerSec * pxPerMm)).coerceAtMost(durationSec.coerceAtLeast(1f))
                var sec = (floor(startSec / stepSec) * stepSec)
                while (sec <= endSec + 0.001f) {
                    val x = traceLeft + sec * mmPerSec * pxPerMm - scroll.x
                    if (x >= labelPx - 2f && x <= size.width) {
                        val major = absMod(sec, 1f) < 0.001f || absMod(sec, 1f) > 0.999f
                        drawLine(
                            AxisInk,
                            Offset(x, axisTop),
                            Offset(x, axisTop + if (major) axisH * 0.45f else axisH * 0.25f),
                            strokeWidth = if (major) 1.4f else 1f,
                        )
                        if (major || stepSec >= 1f) {
                            val label = if (sec == sec.toInt().toFloat()) {
                                "${sec.toInt()} s"
                            } else {
                                "${"%.1f".format(sec)} s"
                            }
                            drawContext.canvas.nativeCanvas.drawText(label, x, axisTop + axisH * 0.85f, axisPaint)
                        }
                    }
                    sec += stepSec
                }
            }
            } // clipRect całego papieru
            }

            // Zoom +/− — pinch też działa; przy 30 s przesuwaj palcem w poziomie.
            Column(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ZoomChip(Icons.Outlined.Add, "Powiększ") {
                    userZoom = (userZoom * 1.25f).coerceIn(0.55f, 3.5f)
                }
                ZoomChip(Icons.Outlined.Remove, "Pomniejsz") {
                    userZoom = (userZoom / 1.25f).coerceIn(0.55f, 3.5f)
                }
                Text(
                    "${"%.0f".format(userZoom * 100)}%",
                    color = EcgUiColors.Navy,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .background(Color.White.copy(alpha = 0.85f), CircleShape)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun ZoomChip(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = Color.White.copy(alpha = 0.92f),
        shadowElevation = 2.dp,
        modifier = Modifier.size(40.dp),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = label, tint = EcgUiColors.Navy, modifier = Modifier.size(22.dp))
        }
    }
}

/**
 * Panel średniego odcinka PQRST (wyrównanie do R, −200…+450 ms).
 * Analogicznie do widoku uśrednionego zespołu w analizatorze EHO12.
 */
@Composable
fun AveragePqrPanel(
    average: AveragePqr?,
    mmPerMv: Int,
    analysis: EcgAnalysis? = null,
    liveHrBpm: Int? = null,
    liveBeating: Boolean = false,
    qualityMessage: String? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .background(EcgUiColors.Surface)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Średni PQRST",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = EcgUiColors.Navy,
            )
            if (liveHrBpm != null || liveBeating) {
                LiveSideHr(bpm = liveHrBpm, beating = liveBeating)
            }
        }
        Text(
            when {
                // Nie powtarzaj komunikatu jakości w nagłówku — jest w pustym obszarze poniżej.
                average == null && qualityMessage != null -> "Brak uśrednienia"
                average == null -> "Czeka na ≥2 zespoły QRS"
                else -> "${average.lead.label} · ${average.beatCount} zespołów nałożonych"
            },
            style = MaterialTheme.typography.labelSmall,
            color = EcgUiColors.Muted,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        Box(Modifier.fillMaxWidth().weight(1f)) {
            if (average == null) {
                Box(Modifier.fillMaxSize().background(Paper)) {
                    Text(
                        qualityMessage
                            ?: "Uśredniony odcinek pojawi się po dobrym sygnale i załamkach R.",
                        style = MaterialTheme.typography.labelSmall,
                        color = EcgUiColors.Muted,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            } else {
                AveragePqrCanvas(
                    average = average,
                    mmPerMv = mmPerMv,
                    waveMarks = analysis?.waveMarks.orEmpty(),
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        if (analysis != null && average != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                "Pomiary",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = EcgUiColors.Navy,
            )
            // HR min / avg / max — średnia pogrubiona.
            if (analysis.hrMinBpm != null || analysis.hrAvgBpm != null || analysis.heartRateBpm != null) {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 1.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("HR", style = MaterialTheme.typography.labelSmall, color = EcgUiColors.Muted)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "${analysis.hrMinBpm ?: "—"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = EcgUiColors.Ink,
                        )
                        Text("/", style = MaterialTheme.typography.labelSmall, color = EcgUiColors.Muted)
                        Text(
                            "${analysis.hrAvgBpm ?: analysis.heartRateBpm ?: "—"}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = EcgUiColors.Navy,
                        )
                        Text("/", style = MaterialTheme.typography.labelSmall, color = EcgUiColors.Muted)
                        Text(
                            "${analysis.hrMaxBpm ?: "—"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = EcgUiColors.Ink,
                        )
                        Text("/min", style = MaterialTheme.typography.labelSmall, color = EcgUiColors.Muted)
                    }
                }
            }
            analysis.measurementTable().filterNot { it.first == "HR" }.forEach { (name, value) ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 1.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(name, style = MaterialTheme.typography.labelSmall, color = EcgUiColors.Muted)
                    Text(value, style = MaterialTheme.typography.labelSmall, color = EcgUiColors.Ink)
                }
            }
            analysis.measurementReasons().take(4).forEach { (name, reason) ->
                Text(
                    "$name: $reason",
                    style = MaterialTheme.typography.labelSmall,
                    color = EcgUiColors.Muted,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                )
            }
            if (average.unavailableReason != null && average.beatCount == 0) {
                Text(
                    average.unavailableReason!!,
                    style = MaterialTheme.typography.labelSmall,
                    color = EcgUiColors.Muted,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun LiveSideHr(bpm: Int?, beating: Boolean) {
    val infinite = rememberInfiniteTransition(label = "sideHr")
    val alpha by infinite.animateFloat(
        initialValue = 1f,
        targetValue = if (beating && bpm != null) 0.45f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = bpm?.let { (30_000 / it).coerceIn(280, 700) } ?: 500),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "sideHrAlpha",
    )
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(
            Icons.Outlined.Favorite,
            contentDescription = null,
            tint = Color(0xFFE53935).copy(alpha = alpha),
            modifier = Modifier.size(16.dp),
        )
        Text(
            bpm?.let { "$it/min" } ?: "—",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFB71C1C),
        )
    }
}

@Composable
private fun AveragePqrCanvas(
    average: AveragePqr,
    mmPerMv: Int,
    waveMarks: List<WaveMark> = emptyList(),
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val textPx = with(density) { 11.dp.toPx() }
    val labelPaint = remember(textPx) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#002660")
            textSize = textPx
        }
    }
    val overlay = Color(0xFF1A1A1A).copy(alpha = 0.18f)
    val meanColor = LeadColors.of(average.lead.label)
    val durationSec = average.durationSec
    val labelMm = 10f
    val contentMmW = labelMm + durationSec * 50f
    // Mediana offsetów P/Q/S/T względem R (nie jeden beat) → stabilniejsze kropki na średnim.
    val relativeMark = remember(waveMarks, average.preSamples, average.postSamples) {
        if (waveMarks.isEmpty()) return@remember null
        fun medianOffset(pick: (WaveMark) -> Int?): Int? {
            val offs = waveMarks.mapNotNull { mark ->
                val at = pick(mark) ?: return@mapNotNull null
                val ref = mark.r ?: mark.qrsEvent
                at - ref
            }.sorted()
            if (offs.isEmpty()) return null
            return offs[offs.size / 2]
        }
        val rIdx = average.preSamples
        WaveMark(
            qrsEvent = rIdx,
            p = medianOffset { it.p }?.let { rIdx + it },
            q = medianOffset { it.q }?.let { rIdx + it },
            r = rIdx,
            s = medianOffset { it.s }?.let { rIdx + it },
            t = medianOffset { it.t }?.let { rIdx + it },
        )
    }
    Canvas(modifier) {
        drawRect(Paper)
        val pxPerMm = min(size.width / contentMmW, size.height / 36f).coerceIn(1.6f, 16f)
        val labelPx = labelMm * pxPerMm
        val mmPerSec = 50f
        val pxPerSample = mmPerSec * pxPerMm / average.samplingHz.coerceAtLeast(1)
        val gridH = size.height - 14f
        val totalMmW = ceil(contentMmW).toInt()
        for (mm in 0..totalMmW) {
            val x = mm * pxPerMm
            if (x > size.width) break
            drawLine(
                if (mm % 5 == 0) Major else Minor,
                Offset(x.coerceAtLeast(labelPx), 0f),
                Offset(x.coerceAtLeast(labelPx), gridH),
                strokeWidth = if (mm % 5 == 0) 1.2f else 0.6f,
            )
        }
        val mmH = ceil(gridH / pxPerMm).toInt()
        for (mm in 0..mmH) {
            val y = mm * pxPerMm
            if (y > gridH) break
            drawLine(
                if (mm % 5 == 0) Major else Minor,
                Offset(labelPx, y),
                Offset(size.width, y),
                strokeWidth = if (mm % 5 == 0) 1.2f else 0.6f,
            )
        }
        val base = gridH * 0.62f
        drawRect(Paper, topLeft = Offset(0f, 0f), size = Size(labelPx, gridH))
        labelPaint.color = android.graphics.Color.argb(
            255,
            (meanColor.red * 255).toInt(),
            (meanColor.green * 255).toInt(),
            (meanColor.blue * 255).toInt(),
        )
        drawContext.canvas.nativeCanvas.drawText(average.lead.label, 4f, base, labelPaint)
        val cechaHeightPx = (1.0 * mmPerMv * pxPerMm).toFloat().coerceAtMost(gridH * 0.55f)
        val x0 = labelPx + 2f
        drawLine(meanColor, Offset(x0, base), Offset(x0 + 2f, base), strokeWidth = 1.4f)
        drawLine(meanColor, Offset(x0 + 2f, base), Offset(x0 + 2f, base - cechaHeightPx), strokeWidth = 1.4f)
        drawLine(meanColor, Offset(x0 + 2f, base - cechaHeightPx), Offset(x0 + 6f, base - cechaHeightPx), strokeWidth = 1.4f)
        drawLine(meanColor, Offset(x0 + 6f, base - cechaHeightPx), Offset(x0 + 6f, base), strokeWidth = 1.4f)

        // Przebiegi tylko wewnątrz siatki — duże artefakty nie wychodzą poza panel.
        clipRect(left = labelPx, top = 0f, right = size.width, bottom = gridH) {
            fun stroke(values: DoubleArray, color: Color, width: Float) {
                if (values.isEmpty()) return
                val path = Path()
                for (i in values.indices) {
                    val x = labelPx + i * pxPerSample
                    val y = (base - (values[i] * mmPerMv * pxPerMm).toFloat())
                        .coerceIn(1f, gridH - 1f)
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, color, style = Stroke(width = width, cap = StrokeCap.Round))
            }
            for (beat in average.overlays) stroke(beat, overlay, 1.1f)
            stroke(average.mean, meanColor, 2.2f)

            val mark = relativeMark
            if (mark != null && average.mean.isNotEmpty()) {
                val labelPaintLocal = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    textSize = textPx * 0.75f
                    textAlign = Paint.Align.CENTER
                }
                fun dot(idx: Int?, label: String, color: Color) {
                    if (idx == null || idx !in average.mean.indices) return
                    val x = labelPx + idx * pxPerSample
                    val y = (base - (average.mean[idx] * mmPerMv * pxPerMm).toFloat())
                        .coerceIn(4f, gridH - 4f)
                    val r = 2.8f * pxPerMm / 3.5f
                    val radius = r.coerceIn(3.4f, 6f)
                    drawCircle(color, radius = radius, center = Offset(x, y))
                    drawCircle(Color.White, radius = radius * 0.35f, center = Offset(x, y))
                    labelPaintLocal.color = android.graphics.Color.argb(
                        255,
                        (color.red * 255).toInt(),
                        (color.green * 255).toInt(),
                        (color.blue * 255).toInt(),
                    )
                    drawContext.canvas.nativeCanvas.drawText(
                        label,
                        x,
                        (y - radius - 2f).coerceAtLeast(textPx),
                        labelPaintLocal,
                    )
                }
                dot(mark.p, "P", Color(0xFF00A0D4))
                dot(mark.q, "Q", Color(0xFF787A77))
                dot(mark.r, "R", Color(0xFF007DC4))
                dot(mark.s, "S", Color(0xFF4D4E4C))
                dot(mark.t, "T", Color(0xFF002660))
            }
        }
    }
}

private fun absMod(value: Float, mod: Float): Float {
    val r = value % mod
    return if (r < 0) r + mod else r
}
