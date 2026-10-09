package pl.cardioscp.rehab.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import pl.cardioscp.rehab.R
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * Dwuklatkowa animacja Nordic walking (A/B + kołysanie) — tylko gdy [animated]=true.
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
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 720, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "nordic-phase",
    )
    val wave = sin(phase * 2.0 * PI).toFloat()
    val bob = abs(sin(phase * 2.0 * PI)).toFloat()
    // Pierwsza połowa cyklu = klatka A, druga = B (przeciwny krok).
    val showB = phase >= 0.5f

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(
                if (showB) R.drawable.coach_nordic_b else R.drawable.coach_nordic,
            ),
            contentDescription = contentDescription,
            modifier = Modifier
                .size(size)
                .graphicsLayer {
                    translationY = -bob * 7.dp.toPx()
                    translationX = wave * 3.dp.toPx()
                    rotationZ = wave * 5f
                    scaleY = 1f + bob * 0.03f
                    scaleX = 1f - bob * 0.015f
                },
            contentScale = ContentScale.Fit,
        )
    }
}
