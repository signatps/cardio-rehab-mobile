package pl.cardioscp.rehab.ui.ecg

import androidx.compose.ui.graphics.Color

/** Paleta z CardioSCP-mobile (Pro-PLUS) — kolory odprowadzeń i papieru. */
object EcgUiColors {
    val Navy = Color(0xFF002660)
    val Accent = Color(0xFF007DC4)
    val AccentBright = Color(0xFF00A0D4)
    val Surface = Color(0xFFFFFFFF)
    val Ink = Color(0xFF4D4E4C)
    val Muted = Color(0xFF787A77)
    val Danger = Color(0xFFB42318)
}

object LeadColors {
    fun of(label: String): Color = when (label) {
        "I" -> Color(0xFF002660)
        "II" -> Color(0xFF007DC4)
        "III" -> Color(0xFF00A0D4)
        "aVR" -> Color(0xFFC45C26)
        "aVL" -> Color(0xFF5B7A2A)
        "aVF" -> Color(0xFF8B5A2B)
        "V1", "Vx" -> Color(0xFFB42318)
        "V2" -> Color(0xFFD4780A)
        "V3" -> Color(0xFF1B7A3D)
        "V4" -> Color(0xFF0B6E6E)
        "V5" -> Color(0xFF1565C0)
        "V6" -> Color(0xFF4D4E4C)
        else -> EcgUiColors.Ink
    }
}
