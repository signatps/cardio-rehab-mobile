package pl.cardioscp.rehab.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import pl.cardioscp.rehab.R
import pl.cardioscp.rehab.ui.theme.ProPlusColors
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Nordic walking: cykl chodu (naprzemienne nogi + ręce + kije).
 * Bez bujania / obrotu całego ludzika na boki.
 */
@Composable
fun AnimatedNordicWalker(
    contentDescription: String,
    size: Dp = 110.dp,
    animated: Boolean = true,
    modifier: Modifier = Modifier,
) {
    if (!animated) {
        Image(
            painter = painterResource(R.drawable.coach_nordic),
            contentDescription = contentDescription,
            modifier = modifier.size(size),
            contentScale = ContentScale.Fit,
        )
        return
    }

    val transition = rememberInfiniteTransition(label = "nordic-walk")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 820, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "walk-phase",
    )

    Canvas(
        modifier = modifier
            .size(size)
            .semantics { this.contentDescription = contentDescription },
    ) {
        drawWalkingNordic(
            phase = phase,
            body = Color(0xFFF7F8FA),
            outline = Color(0xFFE0E4E8),
            pole = ProPlusColors.Accent,
            shadow = Color(0x28002660),
        )
    }
}

private fun DrawScope.drawWalkingNordic(
    phase: Float,
    body: Color,
    outline: Color,
    pole: Color,
    shadow: Color,
) {
    val s = minOf(size.width, size.height)
    val cx = size.width * 0.52f
    val groundY = size.height * 0.90f
    val bob = sin(phase * 2f) * s * 0.02f
    val hip = Offset(cx, groundY - s * 0.40f + bob)
    val shoulder = Offset(cx + s * 0.015f, hip.y - s * 0.23f)
    val head = Offset(shoulder.x + s * 0.01f, shoulder.y - s * 0.145f)
    val headR = s * 0.10f
    val limbW = s * 0.058f
    val poleW = s * 0.030f

    drawOval(
        color = shadow,
        topLeft = Offset(cx - s * 0.24f, groundY - s * 0.015f),
        size = Size(s * 0.48f, s * 0.055f),
    )

    val leftLeg = strideLimb(hip, phase, s * 0.21f, s * 0.20f, thighAmp = 34f, kneeMax = 52f)
    val rightLeg = strideLimb(hip, phase + PI.toFloat(), s * 0.21f, s * 0.20f, thighAmp = 34f, kneeMax = 52f)
    val leftArm = strideLimb(shoulder, phase + PI.toFloat(), s * 0.16f, s * 0.15f, thighAmp = 30f, kneeMax = 24f)
    val rightArm = strideLimb(shoulder, phase, s * 0.16f, s * 0.15f, thighAmp = 30f, kneeMax = 24f)

    fun limb(points: Limb, color: Color = body) {
        drawLine(color, points.a, points.b, limbW, StrokeCap.Round)
        drawLine(color, points.b, points.c, limbW, StrokeCap.Round)
        drawCircle(color, limbW * 0.55f, points.b)
        drawCircle(color, limbW * 0.5f, points.c)
    }

    fun poleFrom(hand: Offset, tipBias: Float) {
        val tip = Offset(hand.x + tipBias * s, groundY)
        drawLine(pole, hand, tip, poleW, StrokeCap.Round)
        drawCircle(pole, poleW, tip)
    }

    val leftFront = sin(phase) > 0f
    if (leftFront) {
        limb(rightLeg)
        limb(leftArm)
        poleFrom(leftArm.c, tipBias = -0.05f)
        limb(leftLeg)
        limb(rightArm)
        poleFrom(rightArm.c, tipBias = 0.10f)
    } else {
        limb(leftLeg)
        limb(rightArm)
        poleFrom(rightArm.c, tipBias = -0.05f)
        limb(rightLeg)
        limb(leftArm)
        poleFrom(leftArm.c, tipBias = 0.10f)
    }

    drawLine(body, hip, shoulder, limbW * 1.2f, StrokeCap.Round)
    drawCircle(body, headR, head)
    drawCircle(outline, headR, head, style = Stroke(width = s * 0.014f))
}

private data class Limb(val a: Offset, val b: Offset, val c: Offset)

private fun strideLimb(
    root: Offset,
    phase: Float,
    upper: Float,
    lower: Float,
    thighAmp: Float,
    kneeMax: Float,
): Limb {
    val thighDeg = sin(phase) * thighAmp
    val kneeBend = abs(minOf(0f, sin(phase))) * kneeMax + abs(cos(phase)) * kneeMax * 0.22f
    val thighRad = Math.toRadians(thighDeg.toDouble())
    val mid = Offset(
        root.x + sin(thighRad).toFloat() * upper,
        root.y + cos(thighRad).toFloat() * upper,
    )
    val shinRad = Math.toRadians((thighDeg - kneeBend).toDouble())
    val end = Offset(
        mid.x + sin(shinRad).toFloat() * lower,
        mid.y + cos(shinRad).toFloat() * lower,
    )
    return Limb(root, mid, end)
}
