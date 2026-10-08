package pl.cardioscp.rehab.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val scheme = lightColorScheme(
    primary = ProPlusColors.Accent,
    onPrimary = Color.White,
    secondary = ProPlusColors.Navy,
    onSecondary = Color.White,
    tertiary = ProPlusColors.AccentBright,
    onTertiary = Color.White,
    background = ProPlusColors.Bg,
    onBackground = ProPlusColors.Ink,
    surface = ProPlusColors.Surface,
    onSurface = ProPlusColors.Ink,
    onSurfaceVariant = ProPlusColors.Muted,
    outline = ProPlusColors.Line,
    outlineVariant = ProPlusColors.Line,
    error = ProPlusColors.Danger,
    onError = Color.White,
)

private val TabletType = Typography(
    headlineLarge = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Bold, color = ProPlusColors.Navy),
    headlineMedium = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.SemiBold, color = ProPlusColors.Navy),
    titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 20.sp, lineHeight = 28.sp, color = ProPlusColors.Ink),
    bodyMedium = TextStyle(fontSize = 18.sp, lineHeight = 26.sp, color = ProPlusColors.Muted),
    labelLarge = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
)

private val PhoneType = Typography(
    headlineLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, color = ProPlusColors.Navy),
    headlineMedium = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = ProPlusColors.Navy),
    titleLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, color = ProPlusColors.Ink),
    bodyMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, color = ProPlusColors.Muted),
    labelLarge = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 9.sp, fontWeight = FontWeight.Medium),
)

fun isTabletSw(smallestWidthDp: Int): Boolean = smallestWidthDp >= 600

@Composable
fun CardioRehabTheme(content: @Composable () -> Unit) {
    val sw = LocalConfiguration.current.smallestScreenWidthDp
    val type = if (isTabletSw(sw)) TabletType else PhoneType
    MaterialTheme(colorScheme = scheme, typography = type, content = content)
}
