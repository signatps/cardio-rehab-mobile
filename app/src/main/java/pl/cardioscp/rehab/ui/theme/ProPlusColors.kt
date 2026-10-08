package pl.cardioscp.rehab.ui.theme

import androidx.compose.ui.graphics.Color

/** Paleta publiczna Pro-PLUS SA (CardioSCP Mobile / mobile-DSD / Elementor). */
object ProPlusColors {
    val Navy = Color(0xFF002660)
    val Accent = Color(0xFF007DC4)
    val AccentBright = Color(0xFF00A0D4)
    val AccentHot = Color(0xFF00B4FF)
    val Ice = Color(0xFFB1E1FF)
    val Mist = Color(0xFFDAF1FF)
    val Bg = Color(0xFFF1F3F4)
    val Surface = Color(0xFFFFFFFF)
    val Ink = Color(0xFF4D4E4C)
    val Muted = Color(0xFF787A77)
    val Line = Color(0xFFD5E4EE)
    val Danger = Color(0xFFB42318)
    val Ok = Color(0xFF007DC4)
    val Warn = Color(0xFF00A0D4)
    val ResultGood = Color(0xFF1B7A4A)
    val ResultWatch = Color(0xFFB7791F)
    val ResultAlert = Color(0xFFB42318)
}

object LeadColors {
    fun of(label: String): Color = when (label) {
        "I" -> Color(0xFF002660)
        "II" -> Color(0xFF007DC4)
        "III" -> Color(0xFF00A0D4)
        "aVR" -> Color(0xFFC45C26)
        "aVL" -> Color(0xFF5B7A2A)
        "aVF" -> Color(0xFF8B5A2B)
        "V1" -> Color(0xFFB42318)
        "V2" -> Color(0xFFD4780A)
        "V3" -> Color(0xFF1B7A3D)
        "V4" -> Color(0xFF0B6E6E)
        "V5" -> Color(0xFF1565C0)
        "V6" -> Color(0xFF4D4E4C)
        else -> ProPlusColors.Ink
    }
}
