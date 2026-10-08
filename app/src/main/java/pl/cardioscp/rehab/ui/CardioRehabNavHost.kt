package pl.cardioscp.rehab.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import pl.cardioscp.rehab.ui.screens.BrandSplashScreen
import pl.cardioscp.rehab.ui.screens.EcgBrowserScreen
import pl.cardioscp.rehab.ui.screens.EcgViewerScreen
import pl.cardioscp.rehab.ui.screens.HomeScreen
import pl.cardioscp.rehab.ui.screens.HomeViewModel
import pl.cardioscp.rehab.ui.screens.RecordingsScreen
import pl.cardioscp.rehab.ui.screens.clinic.DashboardScreen
import pl.cardioscp.rehab.ui.screens.clinic.DayPlanScreen
import pl.cardioscp.rehab.ui.screens.clinic.DiseasesScreen
import pl.cardioscp.rehab.ui.screens.clinic.MeasurementsScreen
import pl.cardioscp.rehab.ui.screens.clinic.MedsScreen
import pl.cardioscp.rehab.ui.screens.clinic.SessionsCalendarScreen
import pl.cardioscp.rehab.ui.screens.session.RehabSessionScreen
import pl.cardioscp.rehab.ui.shell.AppDestination
import pl.cardioscp.rehab.ui.shell.AppShell

object Routes {
    const val SPLASH = "splash"
    const val MAIN = "main"
    const val RECORDINGS = "recordings"
    const val ECG_VIEWER = "ecg_viewer"
}

@Composable
fun CardioRehabNavHost(
    viewModel: HomeViewModel = viewModel(),
) {
    val navController = rememberNavController()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val clinic by viewModel.clinic.collectAsStateWithLifecycle()
    val viewerRecording by viewModel.viewerRecording.collectAsStateWithLifecycle()
    val viewerError by viewModel.viewerError.collectAsStateWithLifecycle()
    val viewerTitle by viewModel.viewerTitle.collectAsStateWithLifecycle()
    val openViewerRequest by viewModel.openViewerRequest.collectAsStateWithLifecycle()

    LaunchedEffect(openViewerRequest) {
        if (openViewerRequest > 0) {
            navController.navigate(Routes.ECG_VIEWER) {
                launchSingleTop = true
            }
        }
    }

    NavHost(navController = navController, startDestination = Routes.SPLASH) {
        composable(Routes.SPLASH) {
            LaunchedEffect(Unit) { viewModel.warmVoiceGreeting() }
            BrandSplashScreen(
                onFinished = {
                    navController.navigate(Routes.MAIN) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.MAIN) {
            var destination by rememberSaveable { mutableStateOf(AppDestination.DASHBOARD.name) }
            val current = runCatching { AppDestination.valueOf(destination) }
                .getOrDefault(AppDestination.DASHBOARD)

            AppShell(
                destination = current,
                onDestination = { dest ->
                    if (dest == AppDestination.ECG) {
                        viewModel.refreshEcgArchive()
                    }
                    destination = dest.name
                },
                patientName = clinic.patientName,
            ) {
                when (current) {
                    AppDestination.DASHBOARD -> DashboardScreen(
                        clinic = clinic,
                        livePulseBpm = state.lastPulseBpm,
                        onOpenRehab = { destination = AppDestination.REHAB.name },
                        onOpenDayPlan = { destination = AppDestination.DAY_PLAN.name },
                        onOpenMeds = { destination = AppDestination.MEDS.name },
                        onOpenMeasurements = { destination = AppDestination.MEASUREMENTS.name },
                        onSpeakWelcome = viewModel::speakDayPlanWelcome,
                    )
                    AppDestination.SESSIONS -> SessionsCalendarScreen(
                        clinic = clinic,
                        onStartRehab = { destination = AppDestination.REHAB.name },
                    )
                    AppDestination.DAY_PLAN -> DayPlanScreen(clinic = clinic)
                    AppDestination.MEASUREMENTS -> MeasurementsScreen(clinic = clinic)
                    AppDestination.MEDS -> MedsScreen(
                        clinic = clinic,
                        onMarkTaken = viewModel::markDoseTaken,
                        onAddMedication = viewModel::addMedication,
                    )
                    AppDestination.DISEASES -> DiseasesScreen(
                        clinic = clinic,
                        onAddDisease = viewModel::addDisease,
                        onSetStatus = viewModel::setDiseaseStatus,
                        onRemove = viewModel::removeDisease,
                    )
                    AppDestination.REHAB -> RehabSessionScreen(
                        viewModel = viewModel,
                        onBack = { destination = AppDestination.DASHBOARD.name },
                        onOpenEcg = {
                            destination = AppDestination.ECG.name
                        },
                    )
                    AppDestination.DEVICE -> HomeScreen(
                        viewModel = viewModel,
                        onOpenRecordings = {
                            viewModel.refreshEcgArchive()
                            destination = AppDestination.ECG.name
                        },
                        onOpenRehabSession = { destination = AppDestination.REHAB.name },
                    )
                    AppDestination.ECG -> EcgBrowserScreen(viewModel = viewModel)
                }
            }
        }
        composable(Routes.RECORDINGS) {
            RecordingsScreen(
                recordings = state.recordings,
                onBack = { navController.popBackStack() },
                onOpen = { rec ->
                    viewModel.openRecording(rec)
                    navController.navigate(Routes.ECG_VIEWER)
                },
            )
        }
        composable(Routes.ECG_VIEWER) {
            EcgViewerScreen(
                title = viewerTitle,
                recording = viewerRecording,
                error = viewerError,
                onBack = {
                    viewModel.clearViewer()
                    navController.popBackStack()
                },
            )
        }
    }
}
