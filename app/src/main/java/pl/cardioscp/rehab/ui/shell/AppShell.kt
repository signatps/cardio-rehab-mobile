package pl.cardioscp.rehab.ui.shell

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pl.cardioscp.rehab.ui.theme.ProPlusColors
import pl.cardioscp.rehab.ui.theme.isTabletSw

@Composable
fun AppShell(
    destination: AppDestination,
    onDestination: (AppDestination) -> Unit,
    patientName: String,
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(ProPlusColors.Bg)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        val tablet = isTabletSw(minOf(maxWidth.value, maxHeight.value).toInt())
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                if (tablet) {
                    SideRail(
                        destination = destination,
                        onDestination = onDestination,
                        patientName = patientName,
                    )
                }
                // Prawa ramka treści (jak mobile-DSD)
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(
                            start = if (tablet) 8.dp else 10.dp,
                            end = if (tablet) 16.dp else 10.dp,
                            top = if (tablet) 12.dp else 8.dp,
                            bottom = if (tablet) 12.dp else 4.dp,
                        ),
                    shape = RoundedCornerShape(if (tablet) 16.dp else 12.dp),
                    color = ProPlusColors.Surface,
                    border = BorderStroke(1.dp, ProPlusColors.Line),
                    shadowElevation = 0.dp,
                    tonalElevation = 0.dp,
                ) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .padding(if (tablet) 20.dp else 12.dp),
                    ) {
                        content()
                    }
                }
            }
            if (!tablet) {
                PhoneBar(destination = destination, onDestination = onDestination)
            }
        }
    }
}

@Composable
private fun SideRail(
    destination: AppDestination,
    onDestination: (AppDestination) -> Unit,
    patientName: String,
) {
    NavigationRail(
        modifier = Modifier
            .fillMaxHeight()
            .width(96.dp)
            .padding(vertical = 8.dp),
        containerColor = ProPlusColors.Bg,
        contentColor = ProPlusColors.Navy,
        header = {
            Column(
                Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "Pro-PLUS",
                    style = MaterialTheme.typography.labelMedium,
                    color = ProPlusColors.Accent,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "Cardio Rehab",
                    style = MaterialTheme.typography.labelSmall,
                    color = ProPlusColors.Navy,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    patientName,
                    style = MaterialTheme.typography.labelSmall,
                    color = ProPlusColors.Muted,
                    maxLines = 2,
                )
            }
        },
    ) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            AppDestination.entries.forEach { dest ->
                NavigationRailItem(
                    selected = destination == dest,
                    onClick = { onDestination(dest) },
                    icon = {
                        Icon(dest.icon, contentDescription = dest.label)
                    },
                    label = {
                        Text(dest.label, style = MaterialTheme.typography.labelSmall, maxLines = 2)
                    },
                    alwaysShowLabel = true,
                )
            }
        }
    }
}

@Composable
private fun PhoneBar(
    destination: AppDestination,
    onDestination: (AppDestination) -> Unit,
) {
    // Na telefonie skrócona belka — najważniejsze zakładki
    val phoneTabs = listOf(
        AppDestination.DASHBOARD,
        AppDestination.SESSIONS,
        AppDestination.MEASUREMENTS,
        AppDestination.MEDS,
        AppDestination.REHAB,
        AppDestination.DEVICE,
    )
    NavigationBar(
        containerColor = ProPlusColors.Surface,
        tonalElevation = 0.dp,
        modifier = Modifier.height(64.dp),
    ) {
        phoneTabs.forEach { dest ->
            NavigationBarItem(
                selected = destination == dest ||
                    (destination == AppDestination.DAY_PLAN && dest == AppDestination.SESSIONS) ||
                    (destination == AppDestination.DISEASES && dest == AppDestination.MEDS) ||
                    (destination == AppDestination.ECG && dest == AppDestination.REHAB),
                onClick = { onDestination(dest) },
                icon = { Icon(dest.icon, contentDescription = dest.phoneLabel) },
                label = {
                    Text(dest.phoneLabel, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                },
            )
        }
    }
}
