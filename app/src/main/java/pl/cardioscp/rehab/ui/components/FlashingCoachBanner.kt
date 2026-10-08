package pl.cardioscp.rehab.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pl.cardioscp.rehab.ui.theme.ProPlusColors

/** Migający komunikat coachingu (PRZYSPIESZ / ZWOLNIJ). */
@Composable
fun FlashingCoachBanner(
    text: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val blink = rememberInfiniteTransition(label = "hr-coach-blink")
    val alpha by blink.animateFloat(
        initialValue = 1f,
        targetValue = 0.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 450),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "hr-coach-alpha",
    )
    Box(
        modifier
            .fillMaxWidth()
            .alpha(alpha)
            .background(accent.copy(alpha = 0.18f), RoundedCornerShape(12.dp))
            .padding(vertical = 14.dp, horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = accent,
            fontWeight = FontWeight.Black,
            fontSize = 34.sp,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.headlineLarge,
        )
    }
}

object CoachBannerColors {
    val speedUp = ProPlusColors.AccentBright
    val slowDown = ProPlusColors.Danger
}
