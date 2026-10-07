package pl.cardioscp.rehab.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = DeepTeal,
    onPrimary = Color.White,
    secondary = Seafoam,
    onSecondary = Color.White,
    tertiary = SignalCoral,
    background = Sand,
    onBackground = Ink,
    surface = Mist,
    onSurface = Ink,
    surfaceVariant = SoftMint,
    onSurfaceVariant = DeepTeal,
)

private val DarkColors = darkColorScheme(
    primary = SoftMint,
    onPrimary = DeepTeal,
    secondary = Seafoam,
    onSecondary = Color.White,
    tertiary = SignalCoral,
    background = Ink,
    onBackground = Mist,
    surface = DeepTeal,
    onSurface = Mist,
)

@Composable
fun CardioRehabTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = CardioTypography,
        content = content,
    )
}
