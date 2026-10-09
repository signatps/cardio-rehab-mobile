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
import pl.cardioscp.rehab.auth.AppRole
import pl.cardioscp.rehab.ui.ble.MeasurePopup
import pl.cardioscp.rehab.ui.components.RehabPinDialog
import pl.cardioscp.rehab.ui.screens.BrandSplashScreen
import pl.cardioscp.rehab.ui.screens.EcgBrowserScreen
import pl.cardioscp.rehab.ui.screens.EcgViewerScreen
import pl.cardioscp.rehab.ui.screens.HomeScreen
import pl.cardioscp.rehab.ui.screens.HomeViewModel
import pl.cardioscp.rehab.ui.screens.RecordingsScreen
import pl.cardioscp.rehab.ui.screens.RolePinLoginScreen
import pl.cardioscp.rehab.ui.screens.clinic.DashboardScreen
import pl.cardioscp.rehab.ui.screens.clinic.DayPlanScreen
import pl.cardioscp.rehab.ui.screens.clinic.DiseasesScreen
import pl.cardioscp.rehab.ui.screens.clinic.MeasurementsMode
import pl.cardioscp.rehab.ui.screens.clinic.MeasurementsScreen
import pl.cardioscp.rehab.ui.screens.clinic.MedsScreen
import pl.cardioscp.rehab.ui.screens.clinic.PatientsListScreen
import pl.cardioscp.rehab.ui.screens.clinic.SessionsCalendarScreen
import pl.cardioscp.rehab.ui.screens.session.RehabSessionScreen
import pl.cardioscp.rehab.ui.shell.AppDestination
import pl.cardioscp.rehab.ui.shell.AppShell
import pl.cardioscp.rehab.ui.shell.shellRoleLabel

object Routes {
    const val SPLASH = "splash"
    const val LOGIN = "login"
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
    val authUser by viewModel.authUser.collectAsStateWithLifecycle()
    val selectedPatientId by viewModel.selectedPatientId.collectAsStateWithLifecycle()
    val viewerRecording by viewModel.viewerRecording.collectAsStateWithLifecycle()
    val viewerError by viewModel.viewerError.collectAsStateWithLifecycle()
    val viewerTitle by viewModel.viewerTitle.collectAsStateWithLifecycle()
    val openViewerRequest by viewModel.openViewerRequest.collectAsStateWithLifecycle()
    val showRehabPin by viewModel.showRehabPinDialog.collectAsStateWithLifecycle()

    LaunchedEffect(openViewerRequest, authUser) {
        if (openViewerRequest > 0 && viewModel.canBrowseEcg()) {
            navController.navigate(Routes.ECG_VIEWER) {
                launchSingleTop = true
            }
        }
    }

    if (showRehabPin) {
        RehabPinDialog(
            onDismiss = viewModel::dismissRehabPinDialog,
            onSubmit = viewModel::submitRehabPin,
        )
    }

    NavHost(navController = navController, startDestination = Routes.SPLASH) {
        composable(Routes.SPLASH) {
            LaunchedEffect(Unit) { viewModel.warmVoiceGreeting() }
            BrandSplashScreen(
                onFinished = {
                    val next = if (authUser != null) Routes.MAIN else Routes.LOGIN
                    navController.navigate(next) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.LOGIN) {
            RolePinLoginScreen(
                onLogin = { pin ->
                    val user = viewModel.loginWithPin(pin)
                    if (user != null) {
                        navController.navigate(Routes.MAIN) {
                            popUpTo(Routes.LOGIN) { inclusive = true }
                        }
                    }
                    user
                },
            )
        }
        composable(Routes.MAIN) {
            val role = authUser?.role
            LaunchedEffect(role) {
                if (role == null) {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(Routes.MAIN) { inclusive = true }
                    }
                }
            }
            if (role == null || authUser == null) return@composable

            val visible = AppDestination.visibleFor(role)
            val phoneTabs = AppDestination.phoneTabsFor(role)
            var destination by rememberSaveable(role) {
                mutableStateOf(AppDestination.defaultFor(role).name)
            }
            val current = runCatching { AppDestination.valueOf(destination) }
                .getOrDefault(AppDestination.defaultFor(role))
                .let { dest ->
                    if (dest in visible) dest else AppDestination.defaultFor(role)
                }
            LaunchedEffect(current) {
                if (current.name != destination) destination = current.name
            }

            val headerName = when (role) {
                AppRole.DOCTOR -> {
                    val doctorClinic = viewModel.doctorClinicForSelected()
                    "${authUser!!.displayName} · ${doctorClinic.patientName}"
                }
                else -> authUser!!.displayName
            }

            AppShell(
                destination = current,
                onDestination = { dest ->
                    if (dest !in visible) return@AppShell
                    if (dest == AppDestination.ECG) {
                        viewModel.refreshEcgArchive()
                    }
                    destination = dest.name
                },
                visibleDestinations = visible,
                phoneTabs = phoneTabs,
                headerName = headerName,
                roleLabel = role.shellRoleLabel(),
                onLogout = {
                    viewModel.logout()
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(Routes.MAIN) { inclusive = true }
                    }
                },
            ) {
                when (current) {
                    AppDestination.PATIENTS -> PatientsListScreen(
                        rows = viewModel.doctorPatientRows(),
                        selectedPatientId = selectedPatientId,
                        onSelect = viewModel::selectDoctorPatient,
                        onOpenCalendar = {
                            viewModel.selectDoctorPatient(it)
                            destination = AppDestination.SESSIONS.name
                        },
                    )
                    AppDestination.DASHBOARD -> {
                        val electrodes by viewModel.electrodeStatus.collectAsStateWithLifecycle()
                        DashboardScreen(
                            clinic = clinic,
                            livePulseBpm = state.lastPulseBpm,
                            todaySession = viewModel.todayArchivedSession(),
                            alertCount = viewModel.dashboardAlertCount(),
                            electrodes = electrodes,
                            connection = state.connection,
                            patientHomeMode = role == AppRole.PATIENT,
                            onOpenRehab = {
                                viewModel.requestOpenRehab {
                                    destination = AppDestination.REHAB.name
                                }
                            },
                            onOpenDayPlan = { destination = AppDestination.DAY_PLAN.name },
                            onOpenMeds = { destination = AppDestination.MEDS.name },
                            onOpenAlerts = { destination = AppDestination.MEDS.name },
                            onMeasureBp = viewModel::measureBpStandalone,
                            onMeasureWeight = viewModel::measureWeightStandalone,
                            onSpeakWelcome = viewModel::speakDayPlanWelcome,
                            onReconnectDevice = viewModel::onConnectClicked,
                        )
                        MeasurePopup(controller = viewModel.bleMeasure)
                    }
                    AppDestination.SESSIONS -> {
                        val sessionsClinic = if (role == AppRole.DOCTOR) {
                            viewModel.doctorClinicForSelected()
                        } else {
                            clinic
                        }
                        SessionsCalendarScreen(
                            clinic = sessionsClinic,
                            canStartRehab = role != AppRole.DOCTOR,
                            subtitle = if (role == AppRole.DOCTOR) {
                                "Pacjent: ${sessionsClinic.patientName}"
                            } else {
                                null
                            },
                            onStartRehab = {
                                viewModel.requestOpenRehab {
                                    destination = AppDestination.REHAB.name
                                }
                            },
                        )
                    }
                    AppDestination.DAY_PLAN -> DayPlanScreen(clinic = clinic)
                    AppDestination.MEASUREMENTS -> {
                        when (role) {
                            AppRole.PATIENT -> {
                                MeasurementsScreen(
                                    clinic = clinic,
                                    mode = MeasurementsMode.PATIENT,
                                    onMeasureBp = viewModel::measureBpStandalone,
                                    onMeasureWeight = viewModel::measureWeightStandalone,
                                )
                                MeasurePopup(controller = viewModel.bleMeasure)
                            }
                            AppRole.DOCTOR -> {
                                val doctorClinic = viewModel.doctorClinicForSelected()
                                MeasurementsScreen(
                                    clinic = doctorClinic.copy(
                                        measurements = viewModel.doctorRehabMeasurements(),
                                    ),
                                    mode = MeasurementsMode.DOCTOR_REHAB_ONLY,
                                )
                            }
                            AppRole.ADMIN -> MeasurementsScreen(clinic = clinic)
                        }
                    }
                    AppDestination.MEDS -> MedsScreen(
                        clinic = clinic,
                        onConfirmTaken = viewModel::confirmDoseTaken,
                        onSkip = viewModel::skipDose,
                        onSnooze = viewModel::snoozeDose,
                        onAddMedication = viewModel::addMedication,
                        onRemoveMedication = viewModel::removeMedication,
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
                        canOpenEcg = viewModel.canBrowseEcg(),
                        onOpenEcg = {
                            if (viewModel.canBrowseEcg()) {
                                destination = AppDestination.ECG.name
                            }
                        },
                    )
                    AppDestination.DEVICE -> HomeScreen(
                        viewModel = viewModel,
                        connectionOnly = role == AppRole.PATIENT,
                        onOpenRecordings = {
                            if (viewModel.canBrowseEcg()) {
                                viewModel.refreshEcgArchive()
                                destination = AppDestination.ECG.name
                            }
                        },
                        onOpenRehabSession = {
                            viewModel.requestOpenRehab {
                                destination = AppDestination.REHAB.name
                            }
                        },
                    )
                    AppDestination.ECG -> {
                        if (role == AppRole.ADMIN) {
                            EcgBrowserScreen(viewModel = viewModel)
                        } else {
                            LaunchedEffect(role) {
                                destination = AppDestination.defaultFor(role).name
                            }
                        }
                    }
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
