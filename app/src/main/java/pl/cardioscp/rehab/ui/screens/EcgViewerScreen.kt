package pl.cardioscp.rehab.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import pl.cardioscp.rehab.scp.ScpEcgRecording
import pl.cardioscp.rehab.ui.theme.DeepTeal
import pl.cardioscp.rehab.ui.theme.Sand
import pl.cardioscp.rehab.ui.theme.Seafoam
import kotlin.math.max
import kotlin.math.min

@Composable
fun EcgViewerScreen(
    title: String,
    recording: ScpEcgRecording?,
    error: String?,
    onBack: () -> Unit,
) {
    var selectedLead by remember(recording) { mutableIntStateOf(0) }
    var scaleX by remember { mutableFloatStateOf(1f) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Sand)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Wróć")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = DeepTeal,
                )
                if (recording != null) {
                    Text(
                        text = String.format(
                            "%.1f s · %d Hz · %d B · AVM %d",
                            recording.durationSeconds,
                            recording.samplingHz,
                            recording.fileSize,
                            recording.avm,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        when {
            error != null -> {
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.tertiary,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            recording == null -> {
                Text(
                    text = "Ładowanie przebiegu…",
                    style = MaterialTheme.typography.bodyLarge,
                    color = DeepTeal,
                )
            }
            else -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    recording.leads.forEachIndexed { index, lead ->
                        FilterChip(
                            selected = selectedLead == index,
                            onClick = { selectedLead = index },
                            label = { Text(lead.label) },
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                val lead = recording.leads.getOrNull(selectedLead)
                if (lead == null) {
                    Text("Brak odprowadzenia")
                } else {
                    Text(
                        text = "Odprowadzenie ${lead.label} · ${lead.samples.size} próbek · pinch zoom",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp)
                            .background(Color(0xFFFFF8F8))
                            .pointerInput(Unit) {
                                detectTransformGestures { _, _, zoom, _ ->
                                    scaleX = (scaleX * zoom).coerceIn(0.5f, 12f)
                                }
                            },
                    ) {
                        val samples = lead.samples
                        if (samples.isEmpty()) return@Canvas
                        // ECG paper-ish grid
                        val minor = size.height / 20f
                        for (i in 0..20) {
                            val y = i * minor
                            drawLine(
                                color = Color(0x33E57373),
                                start = Offset(0f, y),
                                end = Offset(size.width, y),
                                strokeWidth = if (i % 5 == 0) 1.5f else 0.8f,
                            )
                        }
                        val step = max(1, (samples.size / (size.width * scaleX)).toInt())
                        var minV = Short.MAX_VALUE.toInt()
                        var maxV = Short.MIN_VALUE.toInt()
                        var i = 0
                        while (i < samples.size) {
                            val v = samples[i].toInt()
                            minV = min(minV, v)
                            maxV = max(maxV, v)
                            i += step
                        }
                        if (minV == maxV) {
                            minV -= 1
                            maxV += 1
                        }
                        val path = Path()
                        val usableH = size.height * 0.85f
                        val topPad = size.height * 0.075f
                        var first = true
                        var xIndex = 0
                        i = 0
                        while (i < samples.size) {
                            val x = (xIndex.toFloat() / (samples.size / step.toFloat())) * size.width
                            val norm = (samples[i].toInt() - minV).toFloat() / (maxV - minV)
                            val y = topPad + (1f - norm) * usableH
                            if (first) {
                                path.moveTo(x, y)
                                first = false
                            } else {
                                path.lineTo(x, y)
                            }
                            xIndex++
                            i += step
                        }
                        drawPath(
                            path = path,
                            color = Seafoam,
                            style = Stroke(width = 2.2f, cap = StrokeCap.Round),
                        )
                    }
                }
            }
        }
    }
}
