package pl.cardioscp.rehab.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import pl.cardioscp.rehab.ui.theme.ProPlusColors
import kotlin.math.cos
import kotlin.math.sin

/**
 * Kompaktowy wskaźnik analogowy tętna (półokrąg + wskazówka).
 * Tylko do podglądu BPM w trakcie wysiłku sesji rehab.
 */
@Composable
fun AnalogGauge(
    value: Float?,
    minValue: Float,
    maxValue: Float,
    label: String,
    unit: String,
    modifier: Modifier = Modifier,
    accent: Color = ProPlusColors.Accent,
    diameter: Dp = 140.dp,
) {
    val fraction = remember { Animatable(0f) }
    val target = when {
        value == null -> 0f
        else -> ((value - minValue) / (maxValue - minValue)).coerceIn(0f, 1f)
    }
    LaunchedEffect(target) {
        fraction.animateTo(
            target,
            animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing),
        )
    }

    val gaugeHeight = diameter * 0.58f

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .width(diameter)
                .height(gaugeHeight)
                .padding(horizontal = 4.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Canvas(Modifier.matchParentSize()) {
                val stroke = 8.dp.toPx()
                val arcW = size.width - stroke
                val arcSize = Size(arcW, arcW)
                val topLeft = Offset(stroke / 2f, size.height - arcSize.height / 2f - stroke / 2f)
                drawArc(
                    color = ProPlusColors.Line,
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
                drawArc(
                    color = ProPlusColors.ResultGood.copy(alpha = 0.55f),
                    startAngle = 180f,
                    sweepAngle = 70f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Butt),
                )
                drawArc(
                    color = ProPlusColors.ResultWatch.copy(alpha = 0.55f),
                    startAngle = 250f,
                    sweepAngle = 60f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Butt),
                )
                drawArc(
                    color = ProPlusColors.ResultAlert.copy(alpha = 0.45f),
                    startAngle = 310f,
                    sweepAngle = 50f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Butt),
                )
                drawArc(
                    color = accent,
                    startAngle = 180f,
                    sweepAngle = 180f * fraction.value,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke * 0.5f, cap = StrokeCap.Round),
                )
                val cx = size.width / 2f
                val cy = topLeft.y + arcSize.height / 2f
                val angleDeg = 180f + 180f * fraction.value
                val angleRad = Math.toRadians(angleDeg.toDouble())
                val needleLen = arcSize.width / 2f - stroke
                val tip = Offset(
                    cx + (cos(angleRad) * needleLen).toFloat(),
                    cy + (sin(angleRad) * needleLen).toFloat(),
                )
                drawLine(
                    color = ProPlusColors.Navy,
                    start = Offset(cx, cy),
                    end = tip,
                    strokeWidth = 2.5.dp.toPx(),
                    cap = StrokeCap.Round,
                )
                drawCircle(color = ProPlusColors.Navy, radius = 4.dp.toPx(), center = Offset(cx, cy))
                drawCircle(color = Color.White, radius = 1.8.dp.toPx(), center = Offset(cx, cy))
                for (i in 0..6) {
                    val a = Math.toRadians(180.0 + i * 30.0)
                    val r0 = arcSize.width / 2f - stroke * 1.35f
                    val r1 = arcSize.width / 2f - stroke * 0.8f
                    drawLine(
                        color = ProPlusColors.Muted,
                        start = Offset(cx + (cos(a) * r0).toFloat(), cy + (sin(a) * r0).toFloat()),
                        end = Offset(cx + (cos(a) * r1).toFloat(), cy + (sin(a) * r1).toFloat()),
                        strokeWidth = 1.5.dp.toPx(),
                    )
                }
            }
        }
        Text(
            text = value?.let { "${it.toInt()} $unit" } ?: "— $unit",
            style = MaterialTheme.typography.titleLarge,
            color = ProPlusColors.Navy,
            fontWeight = FontWeight.Bold,
        )
        Text(label, style = MaterialTheme.typography.labelMedium, color = ProPlusColors.Muted)
    }
}
