package pl.cardioscp.rehab.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pl.cardioscp.rehab.R
import pl.cardioscp.rehab.bluetooth.protocol.ElectrodeContact
import pl.cardioscp.rehab.bluetooth.protocol.ElectrodeSite
import pl.cardioscp.rehab.bluetooth.protocol.ElectrodeStatus
import pl.cardioscp.rehab.ui.theme.ProPlusColors

/**
 * Normalized electrode positions on the frontal 3D mannequin (same stickman art as coach).
 * X/Y are fractions of the image box; origin top-left.
 */
private data class ElectrodeAnchor(
    val site: ElectrodeSite,
    val x: Float,
    val y: Float,
)

private val ElectrodeAnchors = listOf(
    ElectrodeAnchor(ElectrodeSite.RA, 0.30f, 0.30f),
    ElectrodeAnchor(ElectrodeSite.LA, 0.70f, 0.30f),
    ElectrodeAnchor(ElectrodeSite.V1, 0.46f, 0.40f),
    ElectrodeAnchor(ElectrodeSite.RF, 0.38f, 0.90f),
    ElectrodeAnchor(ElectrodeSite.LF, 0.62f, 0.90f),
)

@Composable
fun ElectrodeMannequin(
    status: ElectrodeStatus,
    modifier: Modifier = Modifier,
    size: Dp = 160.dp,
    showLegend: Boolean = true,
    /** Compact OK / fault / unknown icon under the figure (dashboard). */
    showStatusPictogram: Boolean = false,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        BoxWithConstraints(Modifier.size(size)) {
            Image(
                painter = painterResource(R.drawable.coach_hold_still),
                contentDescription = "Manekin elektrod EKG",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
            val density = LocalDensity.current
            val boxW = constraints.maxWidth.toFloat()
            val boxH = constraints.maxHeight.toFloat()
            // Kropki skalują się z ludzikiem — nie zasłaniają sylwetki przy większym manekinie.
            val dot = (size * 0.11f).coerceIn(14.dp, 26.dp)
            val labelSp = ((dot.value * 0.42f).coerceIn(7f, 11f)).sp
            ElectrodeAnchors.forEach { anchor ->
                val contact = status.sites[anchor.site] ?: ElectrodeContact.UNKNOWN
                val cx = boxW * anchor.x
                val cy = boxH * anchor.y
                val xDp = with(density) { (cx - with(density) { dot.toPx() } / 2f).toDp() }
                val yDp = with(density) { (cy - with(density) { dot.toPx() } / 2f).toDp() }
                ElectrodeDot(
                    site = anchor.site,
                    contact = contact,
                    labelSize = labelSp,
                    modifier = Modifier
                        .offset(x = xDp, y = yDp)
                        .size(dot),
                )
            }
        }
        if (showStatusPictogram) {
            ElectrodeStatusPictogram(status = status)
        }
        if (showLegend) {
            Text(
                status.summaryPl,
                style = MaterialTheme.typography.labelMedium,
                color = when {
                    status.anyDetachedLike() -> ProPlusColors.ResultAlert
                    status.allAttached -> ProPlusColors.ResultGood
                    else -> ProPlusColors.Muted
                },
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LegendChip("OK", ProPlusColors.ResultGood)
                LegendChip("Odpięta", ProPlusColors.ResultAlert)
                LegendChip("?", ProPlusColors.Muted)
            }
        }
    }
}

@Composable
fun ElectrodeStatusPictogram(
    status: ElectrodeStatus,
    modifier: Modifier = Modifier,
    iconSize: Dp = 28.dp,
) {
    val (icon, tint, label) = when {
        status.allAttached -> Triple(
            Icons.Outlined.CheckCircle,
            ProPlusColors.ResultGood,
            "Elektrody OK",
        )
        status.anyDetachedLike() -> Triple(
            Icons.Outlined.Cancel,
            ProPlusColors.ResultAlert,
            "Elektrody odpięte",
        )
        else -> Triple(
            Icons.Outlined.HelpOutline,
            ProPlusColors.Muted,
            "Status elektrod nieznany",
        )
    }
    Icon(
        imageVector = icon,
        contentDescription = label,
        tint = tint,
        modifier = modifier.size(iconSize),
    )
}

private fun ElectrodeStatus.anyDetachedLike(): Boolean =
    sites.values.any { it == ElectrodeContact.DETACHED }

@Composable
private fun ElectrodeDot(
    site: ElectrodeSite,
    contact: ElectrodeContact,
    modifier: Modifier = Modifier,
    labelSize: androidx.compose.ui.unit.TextUnit = 8.sp,
) {
    val color = when (contact) {
        ElectrodeContact.ATTACHED -> ProPlusColors.ResultGood
        ElectrodeContact.DETACHED -> ProPlusColors.ResultAlert
        ElectrodeContact.UNKNOWN -> ProPlusColors.Muted
    }
    Box(
        modifier = modifier
            .background(color, CircleShape)
            .border(1.5.dp, Color.White, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            site.label,
            color = Color.White,
            fontSize = labelSize,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

@Composable
private fun LegendChip(label: String, color: Color) {
    Text(
        label,
        modifier = Modifier
            .background(color.copy(alpha = 0.14f), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        style = MaterialTheme.typography.labelSmall,
        color = color,
        fontWeight = FontWeight.SemiBold,
    )
}
