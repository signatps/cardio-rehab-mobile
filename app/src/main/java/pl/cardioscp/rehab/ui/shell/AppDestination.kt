package pl.cardioscp.rehab.ui.shell

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LocalHospital
import androidx.compose.material.icons.outlined.Medication
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Today
import androidx.compose.ui.graphics.vector.ImageVector

enum class AppDestination(
    val label: String,
    val phoneLabel: String,
    val icon: ImageVector,
) {
    DASHBOARD("Pulpit", "Pulpit", Icons.Outlined.Home),
    SESSIONS("Sesje", "Sesje", Icons.Outlined.CalendarMonth),
    DAY_PLAN("Plan dnia", "Plan", Icons.Outlined.Today),
    MEASUREMENTS("Pomiary", "Pomiary", Icons.Outlined.History),
    MEDS("Leki", "Leki", Icons.Outlined.Medication),
    DISEASES("Choroby", "Choroby", Icons.Outlined.LocalHospital),
    REHAB("Sesja rehab", "Rehab", Icons.Outlined.MonitorHeart),
    ECG("EKG", "EKG", Icons.Outlined.FavoriteBorder),
    DEVICE("Urządzenie", "BT", Icons.Outlined.Settings),
}
