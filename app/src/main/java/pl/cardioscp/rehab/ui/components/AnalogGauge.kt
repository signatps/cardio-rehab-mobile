package pl.cardioscp.rehab.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pl.cardioscp.rehab.ui.theme.ProPlusColors
import kotlin.math.cos
import kotlin.math.sin

/**
 * Wskaźnik analogowy tętna — półokrąg, wartość BPM w środku łuku, etykieta pod spodem.
 */
@Composable
fun AnalogGauge(
    value: Float?,
    minValue: Float,
    maxValue: Float,
    label: String,
    unit: String,
    modifier: Modifier = Modifier,
    valueColor: Color = ProPlusColors.Navy,
    /** Dolna granica strefy docelowej (ta sama skala co [minValue]/[maxValue]). */
    zoneMin: Float? = null,
    /** Górna granica strefy docelowej. */
    zoneMax: Float? = null,
    diameter: Dp = 182.dp,
) {
    val fraction = remember { Animatable(0f) }
    val span = (maxValue - minValue).coerceAtLeast(1f)
    val target = when {
        value == null -> 0f
        else -> ((value - minValue) / span).coerceIn(0f, 1f)
    }
    LaunchedEffect(target) {
        fraction.animateTo(
            target,
            animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing),
        )
    }

    val gaugeHeight = diameter * 0.62f
    val zMin = zoneMin
    val zMax = zoneMax

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .width(diameter)
                .height(gaugeHeight)
                .padding(horizontal = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.matchParentSize()) {
                val stroke = 11.dp.toPx()
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
                if (zMin != null && zMax != null && zMax > zMin) {
                    fun toFrac(v: Float) = ((v - minValue) / span).coerceIn(0f, 1f)
                    val low = toFrac(zMin)
                    val high = toFrac(zMax)
                    // poza limitem (nisko)
                    if (low > 0f) {
                        drawArc(
                            color = ProPlusColors.ResultAlert.copy(alpha = 0.45f),
                            startAngle = 180f,
                            sweepAngle = 180f * low,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = stroke, cap = StrokeCap.Butt),
                        )
                    }
                    // w limicie
                    drawArc(
                        color = ProPlusColors.ResultGood.copy(alpha = 0.55f),
                        startAngle = 180f + 180f * low,
                        sweepAngle = 180f * (high - low).coerceAtLeast(0f),
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Butt),
                    )
                    // poza limitem (wysoko)
                    if (high < 1f) {
                        drawArc(
                            color = ProPlusColors.ResultAlert.copy(alpha = 0.45f),
                            startAngle = 180f + 180f * high,
                            sweepAngle = 180f * (1f - high),
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = stroke, cap = StrokeCap.Butt),
                        )
                    }
                } else {
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
                }
                drawArc(
                    color = valueColor,
                    startAngle = 180f,
                    sweepAngle = 180f * fraction.value,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke * 0.45f, cap = StrokeCap.Round),
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
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round,
                )
                drawCircle(color = ProPlusColors.Navy, radius = 5.dp.toPx(), center = Offset(cx, cy))
                drawCircle(color = Color.White, radius = 2.dp.toPx(), center = Offset(cx, cy))
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
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = (-6).dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = value?.let { "${it.toInt()}" } ?: "—",
                    color = valueColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 36.sp,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.displaySmall,
                )
                Text(
                    text = unit,
                    color = valueColor.copy(alpha = 0.85f),
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = ProPlusColors.Muted,
            textAlign = TextAlign.Center,
        )
    }
}
