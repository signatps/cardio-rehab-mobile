package pl.cardioscp.rehab.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import pl.cardioscp.rehab.ui.screens.BrandSplashScreen
import pl.cardioscp.rehab.ui.screens.EcgViewerScreen
import pl.cardioscp.rehab.ui.screens.HomeScreen
import pl.cardioscp.rehab.ui.screens.HomeViewModel
import pl.cardioscp.rehab.ui.screens.RecordingsScreen
import pl.cardioscp.rehab.ui.screens.session.RehabSessionScreen

object Routes {
    const val SPLASH = "splash"
    const val HOME = "home"
    const val RECORDINGS = "recordings"
    const val ECG_VIEWER = "ecg_viewer"
    const val REHAB_SESSION = "rehab_session"
}

@Composable
fun CardioRehabNavHost(
    viewModel: HomeViewModel = viewModel(),
) {
    val navController = rememberNavController()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
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
            BrandSplashScreen(
                onFinished = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.HOME) {
            HomeScreen(
                viewModel = viewModel,
                onOpenRecordings = {
                    viewModel.refreshRecordings()
                    navController.navigate(Routes.RECORDINGS)
                },
                onOpenRehabSession = {
                    navController.navigate(Routes.REHAB_SESSION)
                },
            )
        }
        composable(Routes.REHAB_SESSION) {
            RehabSessionScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onOpenEcg = {
                    navController.navigate(Routes.ECG_VIEWER) {
                        launchSingleTop = true
                    }
                },
            )
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
