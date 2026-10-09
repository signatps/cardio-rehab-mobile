package pl.cardioscp.rehab.ui.screens.session

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.DirectionsRun
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Assignment
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Hotel
import androidx.compose.material.icons.outlined.HourglassBottom
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.MonitorWeight
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Summarize
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import pl.cardioscp.rehab.R
import pl.cardioscp.rehab.bluetooth.protocol.ElectrodeStatus
import pl.cardioscp.rehab.session.BaselineEcgPhase
import pl.cardioscp.rehab.session.CycleHeartRateLimit
import pl.cardioscp.rehab.session.DefaultRehabSurvey
import pl.cardioscp.rehab.session.EcgHrTrend
import pl.cardioscp.rehab.session.EcgHrTrendEngine
import pl.cardioscp.rehab.session.ExerciseKind
import pl.cardioscp.rehab.session.HeartRateCoach
import pl.cardioscp.rehab.session.HeartRateCoachCue
import pl.cardioscp.rehab.session.HeartRateValueTone
import pl.cardioscp.rehab.session.RehabStep
import pl.cardioscp.rehab.session.TrainingCoachVisual
import pl.cardioscp.rehab.session.TrainingPhaseKind
import pl.cardioscp.rehab.ui.ble.MeasurePopup
import pl.cardioscp.rehab.ui.components.AnalogGauge
import pl.cardioscp.rehab.ui.components.CoachBannerColors
import pl.cardioscp.rehab.ui.components.ElectrodeMannequin
import pl.cardioscp.rehab.ui.components.FlashingCoachBanner
import pl.cardioscp.rehab.ui.screens.HomeViewModel
import pl.cardioscp.rehab.ui.theme.ProPlusColors

@Composable
fun RehabSessionScreen(
    viewModel: HomeViewModel,
    onBack: () -> Unit,
    onOpenEcg: () -> Unit,
) {
    val session by viewModel.rehabSession.collectAsStateWithLifecycle()
    val electrodes by viewModel.electrodeStatus.collectAsStateWithLifecycle()
    val state = session

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 4.dp, vertical = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = {
                viewModel.cancelRehabSession()
                onBack()
            }) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Wróć")
            }
            Text(
                "Sesja rehabilitacji",
                style = MaterialTheme.typography.titleLarge,
                color = ProPlusColors.Navy,
                modifier = Modifier.weight(1f),
            )
        }

        if (state == null) {
            IntroContent(
                electrodes = electrodes,
                onRefreshElectrodes = viewModel::refreshElectrodes,
                onStart = { includeWeight, limits ->
                    viewModel.startRehabSession(includeWeight, limits)
                },
            )
            return
        }

        StepHeader(state.step)
        // Głosowe komunikaty główne przy zmianie kroku sesji.
        LaunchedEffect(state.step, state.baselineEcgPhase) {
            val phrase = when (state.step) {
                RehabStep.ECG_BASELINE -> when (state.baselineEcgPhase) {
                    BaselineEcgPhase.ACQUIRING -> "Trwa zapis EKG"
                    else -> null
                }
                RehabStep.VITALS_BP -> "Wykonaj pomiar ciśnienia"
                RehabStep.VITALS_WEIGHT -> "Wykonaj pomiar masy ciała"
                RehabStep.SURVEY -> "Uzupełnij ankietę o stanie zdrowia"
                RehabStep.SUMMARY -> "Sesja rehabilitacji zakończona"
                else -> null
            }
            phrase?.let(viewModel::speakCoachMessage)
        }
        state.statusMessage?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = ProPlusColors.Navy)
            Spacer(Modifier.height(8.dp))
        }
        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
        }

        when (state.step) {
            RehabStep.INTRO -> IntroContent(
                electrodes = electrodes,
                onRefreshElectrodes = viewModel::refreshElectrodes,
                onStart = { includeWeight, limits ->
                    viewModel.startRehabSession(includeWeight, limits)
                },
            )
            RehabStep.ECG_BASELINE -> {
                EcgHoldStillPanel(
                    subtitle = "EKG kwalifikacyjne (przed sesją)",
                    busy = state.busy,
                    phase = state.baselineEcgPhase ?: BaselineEcgPhase.ELECTRODE_CHECK,
                    electrodes = electrodes,
                    onRetry = viewModel::beginBaselineEcg,
                )
            }
            RehabStep.VITALS_BP -> {
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CoachArt(
                        resId = R.drawable.coach_bp,
                        contentDescription = "Pomiar ciśnienia",
                        size = 150.dp,
                    )
                    Text(
                        "Wykonaj pomiar ciśnienia",
                        style = MaterialTheme.typography.headlineSmall,
                        color = ProPlusColors.Navy,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        "Użyj ciśnieniomierza BLE.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ProPlusColors.Muted,
                        textAlign = TextAlign.Center,
                    )
                    state.vitals.bloodPressure?.let {
                        Text("Ostatni wynik: ${it.summary}", fontWeight = FontWeight.SemiBold)
                    }
                    Button(onClick = viewModel::retryBpMeasure, modifier = Modifier.widthIn(min = 220.dp, max = 320.dp)) {
                        Text("Uruchom pomiar ciśnienia")
                    }
                    OutlinedButton(
                        onClick = viewModel::simulateBpMeasure,
                        modifier = Modifier.widthIn(min = 220.dp, max = 320.dp),
                    ) {
                        Text("Symuluj wynik ciśnienia")
                    }
                }
            }
            RehabStep.VITALS_WEIGHT -> {
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CoachArt(
                        resId = R.drawable.coach_weight,
                        contentDescription = "Pomiar wagi",
                        size = 150.dp,
                    )
                    Text(
                        "Wykonaj pomiar masy ciała",
                        style = MaterialTheme.typography.headlineSmall,
                        color = ProPlusColors.Navy,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        "Niewydolność serca — użyj wagi BLE.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ProPlusColors.Muted,
                        textAlign = TextAlign.Center,
                    )
                    state.vitals.weight?.let {
                        Text("Ostatni wynik: ${it.summary}", fontWeight = FontWeight.SemiBold)
                    }
                    Button(onClick = viewModel::retryWeightMeasure, modifier = Modifier.widthIn(min = 220.dp, max = 320.dp)) {
                        Text("Uruchom pomiar wagi")
                    }
                    OutlinedButton(
                        onClick = viewModel::simulateWeightMeasure,
                        modifier = Modifier.widthIn(min = 220.dp, max = 320.dp),
                    ) {
                        Text("Symuluj wynik wagi")
                    }
                    TextButton(onClick = viewModel::skipWeight) { Text("Pomiń wagę") }
                }
            }
            RehabStep.SURVEY -> SurveyContent(
                answers = state.surveyAnswers,
                onAnswer = viewModel::answerSurvey,
                onSubmit = viewModel::submitSurvey,
            )
            RehabStep.SURVEY_DISQUALIFIED -> {
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    CoachArt(
                        resId = R.drawable.coach_stop,
                        contentDescription = "Trening wstrzymany",
                        size = 150.dp,
                    )
                    Text(
                        "Na podstawie ankiety trening nie może się odbyć.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                    )
                    Button(
                        onClick = viewModel::finishDisqualified,
                        modifier = Modifier.widthIn(min = 220.dp, max = 320.dp),
                    ) {
                        Text("Przejdź do podsumowania")
                    }
                }
            }
            RehabStep.ADMISSION_WAIT -> {
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    CoachArt(
                        resId = R.drawable.coach_wait,
                        contentDescription = "Gotowość do treningu",
                        size = 150.dp,
                    )
                    Text(
                        "Kwalifikacja, pomiary i ankieta zakończone.",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        "Rozpocznij sesję treningową, gdy będziesz gotowy.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ProPlusColors.Muted,
                        textAlign = TextAlign.Center,
                    )
                    Button(
                        onClick = viewModel::confirmStartTraining,
                        modifier = Modifier.widthIn(min = 220.dp, max = 320.dp),
                    ) {
                        Text("Rozpocznij sesję", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            RehabStep.TRAINING -> {
                val t = state.training
                if (t == null) {
                    Text("Przygotowanie treningu…")
                } else {
                    TrainingPhaseContent(
                        training = t,
                        exerciseKind = state.trainingPlan.exerciseKind,
                        electrodes = electrodes,
                        onSpeakCue = viewModel::speakHeartRateCue,
                        onSpeakCoach = viewModel::speakCoachMessage,
                        onComment = viewModel::reportEcgEvent,
                        onConfirmEndExercise = { viewModel.confirmEcgEvent(endTraining = false) },
                        onConfirmEndTraining = { viewModel.confirmEcgEvent(endTraining = true) },
                        onDismissEvent = viewModel::dismissEcgEvent,
                    )
                }
            }
            RehabStep.SUMMARY -> SummaryContent(
                state = state,
                onOpenEcg = { entry ->
                    viewModel.openSessionEcg(entry)
                    onOpenEcg()
                },
                onDone = {
                    viewModel.markTodayRehabSessionDone()
                    viewModel.cancelRehabSession()
                    onBack()
                },
            )
        }
    }

    MeasurePopup(controller = viewModel.bleMeasure)
}

@Composable
private fun StepHeader(step: RehabStep) {
    val label = when (step) {
        RehabStep.INTRO -> "Start"
        RehabStep.ECG_BASELINE -> "1. EKG spoczynkowe + SCP"
        RehabStep.VITALS_BP -> "2. Ciśnienie"
        RehabStep.VITALS_WEIGHT -> "2b. Waga"
        RehabStep.SURVEY -> "3. Ankieta"
        RehabStep.SURVEY_DISQUALIFIED -> "Ankieta — dyskwalifikacja"
        RehabStep.ADMISSION_WAIT -> "4. Start treningu"
        RehabStep.TRAINING -> "5. Trening sekwencyjny"
        RehabStep.SUMMARY -> "6. Podsumowanie"
    }
    Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun IntroContent(
    electrodes: ElectrodeStatus,
    onRefreshElectrodes: () -> Unit,
    onStart: (includeWeight: Boolean, limits: List<CycleHeartRateLimit>) -> Unit,
) {
    var includeWeight by remember { mutableStateOf(true) }
    val defaults = remember { HeartRateCoach.defaultLimits(2) }
    var cycleMins by remember { mutableStateOf(defaults.map { it.minBpm }) }
    var cycleMaxs by remember { mutableStateOf(defaults.map { it.maxBpm }) }
    val canStart = electrodes.allAttached
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            "Przebieg sesji",
            style = MaterialTheme.typography.titleMedium,
            color = ProPlusColors.Navy,
        )
        SessionTimelineStrip(includeWeight = includeWeight)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = includeWeight, onCheckedChange = { includeWeight = it })
            Text("Niewydolność serca — mierz także wagę")
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "Limity tętna",
                    style = MaterialTheme.typography.titleMedium,
                    color = ProPlusColors.Navy,
                )
                Text(
                    "Poniżej → PRZYSPIESZ, powyżej → ZWOLNIJ",
                    style = MaterialTheme.typography.bodySmall,
                    color = ProPlusColors.Muted,
                )
                defaults.indices.forEach { idx ->
                    HeartRateLimitStepper(
                        cycle = idx + 1,
                        minBpm = cycleMins[idx],
                        maxBpm = cycleMaxs[idx],
                        onMinChange = { v ->
                            cycleMins = cycleMins.toMutableList().also { list ->
                                list[idx] = v.coerceIn(40, (cycleMaxs[idx] - 1).coerceAtLeast(41))
                            }
                        },
                        onMaxChange = { v ->
                            cycleMaxs = cycleMaxs.toMutableList().also { list ->
                                list[idx] = v.coerceIn((cycleMins[idx] + 1).coerceAtMost(219), 220)
                            }
                        },
                    )
                }
            }
            Column(
                Modifier.widthIn(min = 168.dp, max = 230.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ElectrodeMannequin(
                    status = electrodes,
                    size = 132.dp,
                    showLegend = true,
                )
                TextButton(onClick = onRefreshElectrodes) {
                    Text("Odśwież elektrody")
                }
                Button(
                    onClick = {
                        val limits = defaults.indices.map { idx ->
                            CycleHeartRateLimit(
                                cycle = idx + 1,
                                minBpm = cycleMins[idx],
                                maxBpm = cycleMaxs[idx],
                            )
                        }
                        onStart(includeWeight, limits)
                    },
                    enabled = canStart,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Rozpocznij sesję", style = MaterialTheme.typography.labelLarge)
                }
                if (!canStart) {
                    Text(
                        "Podłącz wszystkie elektrody (RA, LA, LF, RF, V1), aby startować.",
                        style = MaterialTheme.typography.labelSmall,
                        color = ProPlusColors.ResultAlert,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

private data class TimelineStep(
    val icon: ImageVector,
    val badge: String,
    val label: String,
    val enabled: Boolean = true,
)

@Composable
private fun SessionTimelineStrip(includeWeight: Boolean) {
    val steps = listOf(
        TimelineStep(Icons.Outlined.Hotel, "K", "Kwalifikacja EKG"),
        TimelineStep(Icons.Outlined.MonitorHeart, "BP", "Ciśnienie"),
        TimelineStep(Icons.Outlined.MonitorWeight, "kg", "Waga", enabled = includeWeight),
        TimelineStep(Icons.Outlined.Assignment, "A", "Ankieta"),
        TimelineStep(Icons.Outlined.HourglassBottom, "▶", "Start treningu"),
        TimelineStep(Icons.AutoMirrored.Outlined.DirectionsRun, "T", "Trening"),
        TimelineStep(Icons.Outlined.Summarize, "Σ", "Podsumowanie"),
    )
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            steps.forEachIndexed { index, step ->
                if (index > 0) {
                    Box(
                        Modifier
                            .width(18.dp)
                            .height(2.dp)
                            .background(
                                if (step.enabled) ProPlusColors.Accent.copy(alpha = 0.45f)
                                else ProPlusColors.Line,
                            ),
                    )
                }
                TimelinePictogram(step)
            }
        }
    }
}

@Composable
private fun TimelinePictogram(step: TimelineStep) {
    val tint = if (step.enabled) ProPlusColors.Navy else ProPlusColors.Muted.copy(alpha = 0.45f)
    val border = if (step.enabled) ProPlusColors.Line else ProPlusColors.Line.copy(alpha = 0.5f)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(64.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (step.enabled) ProPlusColors.Surface else ProPlusColors.Bg,
            border = BorderStroke(1.dp, border),
            modifier = Modifier.size(52.dp),
        ) {
            Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(step.icon, contentDescription = step.label, tint = tint, modifier = Modifier.size(22.dp))
                Text(
                    step.badge,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (step.enabled) ProPlusColors.Accent else ProPlusColors.Muted,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            step.label,
            style = MaterialTheme.typography.labelSmall,
            color = if (step.enabled) ProPlusColors.Muted else ProPlusColors.Muted.copy(alpha = 0.45f),
            textAlign = TextAlign.Center,
            maxLines = 2,
            fontSize = 10.sp,
            lineHeight = 11.sp,
        )
    }
}

@Composable
private fun HeartRateLimitStepper(
    cycle: Int,
    minBpm: Int,
    maxBpm: Int,
    onMinChange: (Int) -> Unit,
    onMaxChange: (Int) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            "Cykl $cycle",
            fontWeight = FontWeight.Bold,
            color = ProPlusColors.Navy,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.width(64.dp),
        )
        CompactBpmStepper(
            value = minBpm,
            onChange = onMinChange,
            contentDescription = "Min cykl $cycle",
            boundLabel = "MIN",
        )
        CompactBpmStepper(
            value = maxBpm,
            onChange = onMaxChange,
            contentDescription = "Max cykl $cycle",
            boundLabel = "MAX",
        )
    }
}

@Composable
private fun CompactBpmStepper(
    value: Int,
    onChange: (Int) -> Unit,
    contentDescription: String,
    boundLabel: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Outlined.Favorite,
                contentDescription = null,
                tint = ProPlusColors.Accent,
                modifier = Modifier.size(18.dp),
            )
            Text(
                boundLabel,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = ProPlusColors.Accent,
                lineHeight = 11.sp,
            )
        }
        IconButton(
            onClick = { onChange(value - 1) },
            modifier = Modifier.size(32.dp),
        ) {
            Icon(Icons.Outlined.Remove, contentDescription = "Zmniejsz $contentDescription")
        }
        Text(
            "$value",
            fontWeight = FontWeight.Bold,
            color = ProPlusColors.Navy,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(36.dp),
            style = MaterialTheme.typography.titleMedium,
        )
        IconButton(
            onClick = { onChange(value + 1) },
            modifier = Modifier.size(32.dp),
        ) {
            Icon(Icons.Outlined.Add, contentDescription = "Zwiększ $contentDescription")
        }
    }
}

@Composable
private fun SurveyContent(
    answers: Map<String, Boolean>,
    onAnswer: (String, Boolean) -> Unit,
    onSubmit: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        val allAnswered = DefaultRehabSurvey.questions.all { it.id in answers }
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CoachArt(
                resId = R.drawable.coach_survey,
                contentDescription = "Ankieta",
                size = 72.dp,
            )
            Column(Modifier.weight(1f)) {
                Text(
                    "Uzupełnij ankietę o stanie zdrowia",
                    style = MaterialTheme.typography.titleSmall,
                    color = ProPlusColors.Navy,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    if (allAnswered) {
                        "Pogrubiona = odpowiedź kwalifikująca"
                    } else {
                        "Odpowiedz na wszystkie pytania"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = ProPlusColors.Muted,
                )
            }
            Button(
                onClick = onSubmit,
                enabled = allAnswered,
                modifier = Modifier.height(36.dp),
                contentPadding = ButtonDefaults.ContentPadding,
            ) {
                Icon(
                    Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text("Zatwierdź", style = MaterialTheme.typography.labelLarge)
            }
        }
        DefaultRehabSurvey.questions.forEach { q ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    q.text,
                    style = MaterialTheme.typography.bodySmall,
                    color = ProPlusColors.Navy,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    fontSize = 12.sp,
                    lineHeight = 14.sp,
                )
                val yesSelected = answers[q.id] == true
                val noSelected = answers[q.id] == false
                SurveyAnswerChip(
                    label = "TAK",
                    selected = yesSelected,
                    preferred = allAnswered && q.safeAnswerYes,
                    onClick = { onAnswer(q.id, true) },
                )
                SurveyAnswerChip(
                    label = "NIE",
                    selected = noSelected,
                    preferred = allAnswered && !q.safeAnswerYes,
                    onClick = { onAnswer(q.id, false) },
                )
            }
        }
    }
}

@Composable
private fun SurveyAnswerChip(
    label: String,
    selected: Boolean,
    preferred: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = when {
            selected -> ProPlusColors.Accent.copy(alpha = 0.18f)
            else -> ProPlusColors.Surface
        },
        border = BorderStroke(
            1.dp,
            if (selected) ProPlusColors.Accent else ProPlusColors.Line,
        ),
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (preferred || selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) ProPlusColors.Accent else ProPlusColors.Navy,
        )
    }
}

@Composable
private fun TrainingPhaseContent(
    training: pl.cardioscp.rehab.session.TrainingLiveState,
    exerciseKind: ExerciseKind,
    electrodes: ElectrodeStatus,
    onSpeakCue: (String) -> Unit,
    onSpeakCoach: (String) -> Unit,
    onComment: () -> Unit,
    onConfirmEndExercise: () -> Unit,
    onConfirmEndTraining: () -> Unit,
    onDismissEvent: () -> Unit,
) {
    val visual = training.coachVisual
        ?: when (training.phase.kind) {
            TrainingPhaseKind.EXERCISE -> TrainingCoachVisual.EXERCISE
            TrainingPhaseKind.REST -> TrainingCoachVisual.REST
            TrainingPhaseKind.ECG_PEAK ->
                if (training.acquiringEcg) TrainingCoachVisual.HOLD_STILL_ECG
                else TrainingCoachVisual.STOP_BEFORE_PEAK_ECG
            TrainingPhaseKind.ECG_REST_START -> TrainingCoachVisual.HOLD_STILL_ECG
        }
    val headline = when (visual) {
        TrainingCoachVisual.EXERCISE ->
            if (training.phaseElapsedSec < 3) "Rozpocznij ćwiczenie" else "Ćwicz"
        TrainingCoachVisual.STOP_BEFORE_PEAK_ECG -> "Zatrzymaj się"
        TrainingCoachVisual.HOLD_STILL_ECG -> "Trwa zapis EKG"
        TrainingCoachVisual.REST -> "Odpocznij"
    }
    // Głos przy wejściu w fazę (raz na fazę / zmianę wizualną).
    LaunchedEffect(visual, training.phase.index, training.acquiringEcg) {
        val phrase = when (visual) {
            TrainingCoachVisual.EXERCISE -> "Rozpocznij ćwiczenie"
            TrainingCoachVisual.STOP_BEFORE_PEAK_ECG -> "Zatrzymaj się"
            TrainingCoachVisual.HOLD_STILL_ECG -> "Trwa zapis EKG"
            TrainingCoachVisual.REST -> "Odpocznij"
        }
        onSpeakCoach(phrase)
    }
    val pictogramRes = when (visual) {
        TrainingCoachVisual.EXERCISE -> when (exerciseKind) {
            ExerciseKind.NORDIC_WALKING -> R.drawable.coach_nordic
        }
        TrainingCoachVisual.STOP_BEFORE_PEAK_ECG -> R.drawable.coach_stop
        TrainingCoachVisual.HOLD_STILL_ECG -> R.drawable.coach_hold_still
        TrainingCoachVisual.REST -> R.drawable.coach_rest
    }
    val timed = training.phase.kind == TrainingPhaseKind.EXERCISE ||
        training.phase.kind == TrainingPhaseKind.REST ||
        visual == TrainingCoachVisual.STOP_BEFORE_PEAK_ECG
    val totalTimed = when (visual) {
        TrainingCoachVisual.STOP_BEFORE_PEAK_ECG ->
            (training.phaseElapsedSec + training.phaseRemainingSec).coerceAtLeast(1)
        else -> training.phase.durationSec.coerceAtLeast(1)
    }
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                training.phase.label,
                style = MaterialTheme.typography.labelLarge,
                color = ProPlusColors.Muted,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(
                onClick = onComment,
                modifier = Modifier.height(34.dp),
                contentPadding = ButtonDefaults.ContentPadding,
            ) {
                Icon(
                    Icons.Outlined.ChatBubbleOutline,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text("Komentarz", style = MaterialTheme.typography.labelMedium)
            }
        }
        if (timed) {
            LinearProgressIndicator(
                progress = { training.phaseElapsedSec.toFloat() / totalTimed },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Pozostało %d:%02d".format(
                    training.phaseRemainingSec / 60,
                    training.phaseRemainingSec % 60,
                ),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                CoachArt(
                    resId = pictogramRes,
                    contentDescription = headline,
                    size = 110.dp,
                )
                if (visual == TrainingCoachVisual.EXERCISE) {
                    Text(
                        exerciseKind.displayNamePl,
                        style = MaterialTheme.typography.labelLarge,
                        color = ProPlusColors.Accent,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Text(
                    headline,
                    style = MaterialTheme.typography.titleLarge,
                    color = when (visual) {
                        TrainingCoachVisual.STOP_BEFORE_PEAK_ECG -> ProPlusColors.ResultAlert
                        TrainingCoachVisual.HOLD_STILL_ECG -> ProPlusColors.Navy
                        TrainingCoachVisual.REST -> ProPlusColors.Accent
                        TrainingCoachVisual.EXERCISE -> ProPlusColors.Navy
                    },
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
            }
            Column(
                Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                ElectrodeMannequin(
                    status = electrodes,
                    size = 96.dp,
                    showLegend = false,
                    showStatusPictogram = true,
                )
                when {
                    visual == TrainingCoachVisual.EXERCISE && training.measuringPulse -> {
                        val lim = training.heartRateLimit
                        val tone = HeartRateCoach.valueTone(training.pulseBpm, lim)
                        val valueColor = when (tone) {
                            HeartRateValueTone.IN_ZONE -> ProPlusColors.ResultGood
                            HeartRateValueTone.NEAR_EDGE -> ProPlusColors.ResultWatch
                            HeartRateValueTone.OUT_OF_ZONE -> ProPlusColors.ResultAlert
                            HeartRateValueTone.WAITING -> ProPlusColors.Muted
                        }
                        val cueText = HeartRateCoach.screenText(training.heartRateCue)
                        if (cueText != null) {
                            LaunchedEffect(training.heartRateCue, training.phase.index) {
                                while (true) {
                                    HeartRateCoach.speakText(training.heartRateCue)?.let(onSpeakCue)
                                    delay(4_000)
                                }
                            }
                        }
                        lim?.let {
                            Text(
                                "Cel cyklu ${it.cycle}: ${it.minBpm}–${it.maxBpm} bpm",
                                style = MaterialTheme.typography.labelLarge,
                                color = ProPlusColors.Navy,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        AnalogGauge(
                            value = training.pulseBpm?.takeIf { it > 0 }?.toFloat(),
                            minValue = 40f,
                            maxValue = 180f,
                            label = if (training.pulseBpm != null && training.pulseBpm > 0) {
                                "Tętno z EKG"
                            } else {
                                "Oczekiwanie…"
                            },
                            unit = "bpm",
                            valueColor = valueColor,
                            zoneMin = lim?.minBpm?.toFloat(),
                            zoneMax = lim?.maxBpm?.toFloat(),
                            diameter = 196.dp,
                        )
                        if (cueText != null) {
                            FlashingCoachBanner(
                                text = cueText,
                                accent = when (training.heartRateCue) {
                                    HeartRateCoachCue.SPEED_UP -> CoachBannerColors.speedUp
                                    else -> CoachBannerColors.slowDown
                                },
                                modifier = Modifier.widthIn(max = 280.dp),
                            )
                        }
                    }
                    visual == TrainingCoachVisual.REST && training.measuringPulse -> {
                        Text(
                            training.pulseBpm?.takeIf { it > 0 }?.toString() ?: "—",
                            style = MaterialTheme.typography.displaySmall,
                            color = ProPlusColors.Navy,
                            fontWeight = FontWeight.Bold,
                        )
                        Text("bpm", style = MaterialTheme.typography.titleSmall, color = ProPlusColors.Muted)
                    }
                    visual == TrainingCoachVisual.HOLD_STILL_ECG -> {
                        LinearProgressIndicator(
                            progress = { 0f },
                            modifier = Modifier.fillMaxWidth(0.85f),
                        )
                    }
                }
            }
        }
        if (training.pausedForEvent) {
            if (training.phase.kind == TrainingPhaseKind.EXERCISE) {
                Button(
                    onClick = onConfirmEndExercise,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Zakończ wysiłek (dalej EKG szczyt)") }
            }
            Button(
                onClick = onConfirmEndTraining,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Zakończ trening") }
            TextButton(onClick = onDismissEvent) { Text("Kontynuuj fazę") }
        }
    }
}

@Composable
private fun CoachArt(
    @DrawableRes resId: Int,
    contentDescription: String,
    size: Dp = 150.dp,
    modifier: Modifier = Modifier,
) {
    Image(
        painter = painterResource(resId),
        contentDescription = contentDescription,
        modifier = modifier.size(size),
        contentScale = ContentScale.Fit,
    )
}

@Composable
private fun EcgHoldStillPanel(
    subtitle: String,
    busy: Boolean,
    phase: BaselineEcgPhase,
    electrodes: ElectrodeStatus,
    onRetry: () -> Unit,
) {
    val checking = phase == BaselineEcgPhase.ELECTRODE_CHECK
    val coachRes = if (checking) R.drawable.coach_stop else R.drawable.coach_hold_still
    val headline = if (checking) {
        "Sprawdzanie elektrod — nie ruszaj się"
    } else {
        "Trwa zapis EKG"
    }
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            subtitle,
            style = MaterialTheme.typography.labelLarge,
            color = ProPlusColors.Muted,
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CoachArt(
                resId = coachRes,
                contentDescription = headline,
                size = 150.dp,
                modifier = Modifier.weight(1f),
            )
            ElectrodeMannequin(
                status = electrodes,
                size = 150.dp,
                showLegend = true,
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            headline,
            style = MaterialTheme.typography.headlineSmall,
            color = if (checking) ProPlusColors.ResultAlert else ProPlusColors.Navy,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        if (busy) {
            LinearProgressIndicator(
                progress = { if (checking) 0.35f else 0f },
                modifier = Modifier.fillMaxWidth(0.7f),
            )
        } else {
            Button(onClick = onRetry, modifier = Modifier.widthIn(min = 160.dp, max = 240.dp)) {
                Text("Ponów EKG")
            }
        }
    }
}

@Composable
private fun SummaryContent(
    state: pl.cardioscp.rehab.session.RehabSessionState,
    onOpenEcg: (pl.cardioscp.rehab.session.SessionEcgEntry) -> Unit,
    onDone: () -> Unit,
) {
    Row(
        Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            Modifier
                .weight(0.38f)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CoachArt(
                resId = R.drawable.coach_summary,
                contentDescription = "Podsumowanie",
                size = 120.dp,
            )
            Text(
                "Sesja rehabilitacji zakończona",
                style = MaterialTheme.typography.titleMedium,
                color = ProPlusColors.Navy,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            state.vitals.bloodPressure?.let {
                Text(
                    "RR ${it.summary}",
                    style = MaterialTheme.typography.titleSmall,
                    color = ProPlusColors.Navy,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            state.vitals.weight?.let {
                Text(
                    "Masa ${it.summary}",
                    style = MaterialTheme.typography.titleSmall,
                    color = ProPlusColors.Navy,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                "Ankieta: ${DefaultRehabSurvey.evaluate(state.surveyAnswers)}",
                style = MaterialTheme.typography.titleSmall,
                color = ProPlusColors.Navy,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.weight(1f))
            Button(
                onClick = onDone,
                modifier = Modifier.widthIn(min = 140.dp, max = 200.dp),
            ) {
                Text("Zakończ", style = MaterialTheme.typography.labelLarge)
            }
        }
        Column(
            Modifier
                .weight(0.62f)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "Tętno EKG · pocz. / śr. / końc.",
                style = MaterialTheme.typography.labelMedium,
                color = ProPlusColors.Muted,
            )
            if (state.ecgEntries.isEmpty()) {
                Text("Brak zapisanych EKG w tej sesji.", color = ProPlusColors.Muted)
            } else {
                state.ecgEntries.forEach { entry ->
                    val trend = remember(entry.recording.file.absolutePath, entry.hrAvgBpm) {
                        if (entry.hrAvgBpm != null || entry.hrStartBpm != null) {
                            EcgHrTrend(entry.hrStartBpm, entry.hrAvgBpm, entry.hrEndBpm)
                        } else {
                            EcgHrTrendEngine.fromRecording(entry.recording)
                        }
                    }
                    EcgHrTrendCard(
                        title = EcgHrTrendEngine.shortLabel(entry.label),
                        trend = trend,
                        onClick = { onOpenEcg(entry) },
                    )
                }
            }
        }
    }
}

@Composable
private fun EcgHrTrendCard(
    title: String,
    trend: EcgHrTrend,
    onClick: () -> Unit,
) {
    Surface(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, ProPlusColors.Line),
        color = ProPlusColors.Surface,
    ) {
        Column(
            Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.labelLarge,
                    color = ProPlusColors.Navy,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                Text(
                    "${trend.startBpm ?: "—"} / ${trend.avgBpm ?: "—"} / ${trend.endBpm ?: "—"}",
                    style = MaterialTheme.typography.titleSmall,
                    color = ProPlusColors.Accent,
                    fontWeight = FontWeight.Bold,
                )
            }
            EcgHrMiniChart(
                start = trend.startBpm,
                avg = trend.avgBpm,
                end = trend.endBpm,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            )
        }
    }
}

@Composable
private fun EcgHrMiniChart(
    start: Int?,
    avg: Int?,
    end: Int?,
    modifier: Modifier = Modifier,
) {
    val values = listOf(start, avg, end)
    val present = values.mapNotNull { it }
    if (present.isEmpty()) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text("Brak danych tętna", color = ProPlusColors.Muted)
        }
        return
    }
    val minY = (present.minOrNull()!! - 8).coerceAtLeast(40)
    val maxY = (present.maxOrNull()!! + 8).coerceAtMost(220)
    val span = (maxY - minY).coerceAtLeast(1).toFloat()
    val lineColor = ProPlusColors.Accent
    val gridColor = ProPlusColors.Line
    Canvas(modifier) {
        val padL = 4.dp.toPx()
        val padR = 4.dp.toPx()
        val padT = 4.dp.toPx()
        val padB = 4.dp.toPx()
        val w = size.width - padL - padR
        val h = size.height - padT - padB
        val xs = listOf(0.1f, 0.5f, 0.9f).map { padL + it * w }
        // lekka siatka
        for (i in 0..2) {
            val y = padT + h * i / 2f
            drawLine(
                color = gridColor,
                start = Offset(padL, y),
                end = Offset(padL + w, y),
                strokeWidth = 0.8.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f)),
            )
        }
        val pts = values.mapIndexedNotNull { i, v ->
            v?.let {
                val y = padT + h * (1f - (it - minY) / span)
                Offset(xs[i], y)
            }
        }
        if (pts.size >= 2) {
            for (i in 0 until pts.lastIndex) {
                drawLine(
                    color = lineColor,
                    start = pts[i],
                    end = pts[i + 1],
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
        }
        pts.forEach { p ->
            drawCircle(color = lineColor, radius = 3.dp.toPx(), center = p)
            drawCircle(color = Color.White, radius = 1.5.dp.toPx(), center = p)
        }
    }
}
