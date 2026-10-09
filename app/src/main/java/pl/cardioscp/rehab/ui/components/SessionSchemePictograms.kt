package pl.cardioscp.rehab.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DirectionsRun
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Hotel
import androidx.compose.material.icons.outlined.HourglassBottom
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.MonitorWeight
import androidx.compose.material.icons.outlined.Summarize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pl.cardioscp.rehab.session.SessionSchemeProgress
import pl.cardioscp.rehab.session.SessionSchemeStep
import pl.cardioscp.rehab.session.SessionSchemeStepId
import pl.cardioscp.rehab.ui.theme.ProPlusColors

fun SessionSchemeStepId.pictogram(): ImageVector = when (this) {
    SessionSchemeStepId.QUAL_ECG -> Icons.Outlined.Hotel
    SessionSchemeStepId.BP -> Icons.Outlined.MonitorHeart
    SessionSchemeStepId.WEIGHT -> Icons.Outlined.MonitorWeight
    SessionSchemeStepId.SURVEY -> Icons.Outlined.Assignment
    SessionSchemeStepId.ADMISSION -> Icons.Outlined.HourglassBottom
    SessionSchemeStepId.TRAINING -> Icons.AutoMirrored.Outlined.DirectionsRun
    SessionSchemeStepId.BORG -> Icons.Outlined.FavoriteBorder
    SessionSchemeStepId.SUMMARY -> Icons.Outlined.Summarize
}

/** Pełny pasek schematu (jak przy starcie sesji u pacjenta). */
@Composable
fun SessionSchemeTimelineStrip(
    includeWeight: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val progress = SessionSchemeProgress.schemeTemplate(includeWeight)
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            progress.steps.forEachIndexed { index, step ->
                if (index > 0) {
                    Box(
                        Modifier
                            .width(18.dp)
                            .height(2.dp)
                            .background(
                                if (step.inPlan) ProPlusColors.Accent.copy(alpha = 0.45f)
                                else ProPlusColors.Line,
                            ),
                    )
                }
                SessionSchemePictogram(
                    step = step,
                    showLabel = true,
                    size = 52.dp,
                    iconSize = 22.dp,
                )
            }
        }
    }
}

/** Kompaktowy rząd statusów na pasku pacjenta (lekarz). */
@Composable
fun SessionSchemeStatusRow(
    progress: SessionSchemeProgress,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        progress.steps.filter { it.inPlan }.forEach { step ->
            SessionSchemePictogram(
                step = step,
                showLabel = false,
                size = 36.dp,
                iconSize = 16.dp,
                compact = true,
            )
        }
    }
}

@Composable
fun SessionSchemePictogram(
    step: SessionSchemeStep,
    showLabel: Boolean,
    size: Dp,
    iconSize: Dp,
    compact: Boolean = false,
) {
    val active = step.inPlan
    val done = step.done && active
    val tint = when {
        !active -> ProPlusColors.Muted.copy(alpha = 0.45f)
        done -> ProPlusColors.ResultGood
        else -> ProPlusColors.Muted
    }
    val border = when {
        !active -> ProPlusColors.Line.copy(alpha = 0.5f)
        done -> ProPlusColors.ResultGood
        else -> ProPlusColors.Line
    }
    val bg = when {
        !active -> ProPlusColors.Bg
        done -> ProPlusColors.ResultGood.copy(alpha = 0.12f)
        else -> ProPlusColors.Surface
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = if (showLabel) Modifier.width(64.dp) else Modifier,
    ) {
        Surface(
            shape = RoundedCornerShape(if (compact) 8.dp else 12.dp),
            color = bg,
            border = BorderStroke(1.dp, border),
            modifier = Modifier.size(size),
        ) {
            Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    step.id.pictogram(),
                    contentDescription = step.label,
                    tint = tint,
                    modifier = Modifier.size(iconSize),
                )
                Text(
                    step.badge,
                    fontSize = if (compact) 8.sp else 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        !active -> ProPlusColors.Muted
                        done -> ProPlusColors.ResultGood
                        else -> ProPlusColors.Accent
                    },
                )
            }
        }
        if (showLabel) {
            Spacer(Modifier.height(4.dp))
            Text(
                step.label,
                style = MaterialTheme.typography.labelSmall,
                color = if (active) ProPlusColors.Muted else ProPlusColors.Muted.copy(alpha = 0.45f),
                textAlign = TextAlign.Center,
                maxLines = 2,
                fontSize = 10.sp,
                lineHeight = 11.sp,
            )
        }
    }
}
