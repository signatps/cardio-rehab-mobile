package pl.cardioscp.rehab.ui.shell

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LocalHospital
import androidx.compose.material.icons.outlined.Medication
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Today
import androidx.compose.ui.graphics.vector.ImageVector
import pl.cardioscp.rehab.auth.AppRole

enum class AppDestination(
    val label: String,
    val phoneLabel: String,
    val icon: ImageVector,
) {
    PATIENTS("Pacjenci", "Pacjenci", Icons.Outlined.People),
    DASHBOARD("Pulpit", "Pulpit", Icons.Outlined.Home),
    SESSIONS("Sesje", "Sesje", Icons.Outlined.CalendarMonth),
    DAY_PLAN("Plan dnia", "Plan", Icons.Outlined.Today),
    MEASUREMENTS("Pomiary", "Pomiary", Icons.Outlined.History),
    MEDS("Leki", "Leki", Icons.Outlined.Medication),
    DISEASES("Choroby", "Choroby", Icons.Outlined.LocalHospital),
    REHAB("Sesja rehab", "Rehab", Icons.Outlined.MonitorHeart),
    ECG("EKG", "EKG", Icons.Outlined.FavoriteBorder),
    DEVICE("Urządzenie", "BT", Icons.Outlined.Settings),
    ;

    companion object {
        fun visibleFor(role: AppRole): List<AppDestination> = when (role) {
            AppRole.ADMIN -> entries.filter { it != PATIENTS }
            AppRole.PATIENT -> listOf(
                DASHBOARD,
                SESSIONS,
                MEASUREMENTS,
                MEDS,
                DISEASES,
                REHAB,
                DEVICE,
            )
            AppRole.DOCTOR -> listOf(
                PATIENTS,
                SESSIONS,
                MEASUREMENTS,
            )
        }

        fun defaultFor(role: AppRole): AppDestination =
            visibleFor(role).first()

        fun phoneTabsFor(role: AppRole): List<AppDestination> = when (role) {
            AppRole.ADMIN -> listOf(
                DASHBOARD,
                SESSIONS,
                MEASUREMENTS,
                MEDS,
                REHAB,
                DEVICE,
            )
            AppRole.PATIENT -> listOf(
                DASHBOARD,
                SESSIONS,
                MEASUREMENTS,
                MEDS,
                REHAB,
                DEVICE,
            )
            AppRole.DOCTOR -> listOf(
                PATIENTS,
                SESSIONS,
                MEASUREMENTS,
            )
        }
    }
}
