package pl.cardioscp.rehab.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import pl.cardioscp.rehab.R

private val ProPlusBlue = Color(0xFF0072BC)
private val ProPlusGray = Color(0xFF6B6E70)

/**
 * White start screen with Pro-PLUS SA brand mark (logo from mobile-dsd).
 */
@Composable
fun BrandSplashScreen(
    onFinished: () -> Unit,
) {
    val logoAlpha = remember { Animatable(0f) }
    val logoScale = remember { Animatable(0.9f) }
    val textAlpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        delay(80)
        launch { logoAlpha.animateTo(1f, tween(420, easing = FastOutSlowInEasing)) }
        launch { logoScale.animateTo(1f, tween(520, easing = FastOutSlowInEasing)) }
        delay(260)
        textAlpha.animateTo(1f, tween(380, easing = FastOutSlowInEasing))
        delay(420)
        logoScale.animateTo(1.03f, tween(240, easing = FastOutSlowInEasing))
        logoScale.animateTo(1f, tween(240, easing = FastOutSlowInEasing))
        delay(500)
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier
                .padding(horizontal = 32.dp)
                .widthIn(max = 360.dp)
                .fillMaxWidth(),
        ) {
            Image(
                painter = painterResource(R.drawable.proplus_logo),
                contentDescription = stringResource(R.string.brand_name),
                modifier = Modifier
                    .fillMaxWidth(0.82f)
                    .scale(logoScale.value)
                    .alpha(logoAlpha.value),
                contentScale = ContentScale.Fit,
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.alpha(textAlpha.value),
            ) {
                Text(
                    text = stringResource(R.string.brand_name),
                    color = ProPlusGray,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = stringResource(R.string.brand_tagline),
                    color = ProPlusBlue,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
