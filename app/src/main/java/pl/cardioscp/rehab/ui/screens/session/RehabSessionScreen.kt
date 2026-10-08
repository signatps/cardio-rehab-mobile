package pl.cardioscp.rehab.ui.screens.session

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.DirectionsRun
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Assignment
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Hotel
import androidx.compose.material.icons.outlined.HourglassBottom
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.MonitorWeight
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Summarize
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import pl.cardioscp.rehab.session.CycleHeartRateLimit
import pl.cardioscp.rehab.session.DefaultRehabSurvey
import pl.cardioscp.rehab.session.HeartRateCoach
import pl.cardioscp.rehab.session.HeartRateCoachCue
import pl.cardioscp.rehab.session.HeartRateValueTone
import pl.cardioscp.rehab.session.RehabStep
import pl.cardioscp.rehab.session.TrainingPhaseKind
import pl.cardioscp.rehab.ui.ble.MeasurePopup
import pl.cardioscp.rehab.ui.components.AnalogGauge
import pl.cardioscp.rehab.ui.components.CoachBannerColors
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
                onStart = { includeWeight, limits ->
                    viewModel.startRehabSession(includeWeight, limits)
                },
            )
            return
        }

        StepHeader(state.step)
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
                onStart = { includeWeight, limits ->
                    viewModel.startRehabSession(includeWeight, limits)
                },
            )
            RehabStep.ECG_BASELINE -> {
                Text(
                    "Inicjalizacja EHO-Mini, zapis EKG Offline i pobranie SCP…",
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (state.busy) {
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { 0f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = viewModel::beginBaselineEcg, modifier = Modifier.fillMaxWidth()) {
                        Text("Ponów EKG spoczynkowe")
                    }
                }
            }
            RehabStep.VITALS_BP -> {
                Text("Zmierz ciśnienie ciśnieniomierzem BLE.", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(8.dp))
                state.vitals.bloodPressure?.let {
                    Text("Ostatni wynik: ${it.summary}", fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(8.dp))
                Button(onClick = viewModel::retryBpMeasure, modifier = Modifier.fillMaxWidth()) {
                    Text("Uruchom pomiar ciśnienia")
                }
                OutlinedButton(
                    onClick = viewModel::simulateBpMeasure,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Symuluj wynik ciśnienia")
                }
            }
            RehabStep.VITALS_WEIGHT -> {
                Text(
                    "Niewydolność serca — zmierz masę ciała wagą BLE.",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(Modifier.height(8.dp))
                state.vitals.weight?.let {
                    Text("Ostatni wynik: ${it.summary}", fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(8.dp))
                Button(onClick = viewModel::retryWeightMeasure, modifier = Modifier.fillMaxWidth()) {
                    Text("Uruchom pomiar wagi")
                }
                OutlinedButton(
                    onClick = viewModel::simulateWeightMeasure,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Symuluj wynik wagi")
                }
                TextButton(onClick = viewModel::skipWeight) { Text("Pomiń wagę") }
            }
            RehabStep.SURVEY -> SurveyContent(
                answers = state.surveyAnswers,
                onAnswer = viewModel::answerSurvey,
                onSubmit = viewModel::submitSurvey,
            )
            RehabStep.SURVEY_DISQUALIFIED -> {
                Text(
                    "Na podstawie ankiety trening nie może się odbyć.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.height(12.dp))
                Button(onClick = viewModel::finishDisqualified, modifier = Modifier.fillMaxWidth()) {
                    Text("Przejdź do podsumowania")
                }
            }
            RehabStep.ADMISSION_WAIT -> {
                Text(
                    "Ankieta zaliczona. Oczekiwanie na dopuszczenie do treningu…",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(Modifier.height(12.dp))
                val left = state.admissionRemainingSec ?: 0
                Text(
                    "%d s".format(left),
                    style = MaterialTheme.typography.displaySmall,
                    color = ProPlusColors.Navy,
                    fontWeight = FontWeight.Bold,
                )
                LinearProgressIndicator(
                    progress = {
                        val total = state.trainingPlan.admissionWaitSec.coerceAtLeast(1)
                        1f - left.toFloat() / total
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            RehabStep.TRAINING -> {
                val t = state.training
                if (t == null) {
                    Text("Przygotowanie treningu…")
                } else {
                    Text(t.phase.label, style = MaterialTheme.typography.headlineSmall, color = ProPlusColors.Navy)
                    Text(
                        when (t.phase.kind) {
                            TrainingPhaseKind.ECG_REST_START -> "Akwizycja EKG spoczynkowego"
                            TrainingPhaseKind.EXERCISE -> "Ćwicz — pomiar tętna z EHO-Mini"
                            TrainingPhaseKind.ECG_PEAK -> "Akwizycja EKG w szczycie wysiłku"
                            TrainingPhaseKind.REST -> "Odpoczynek"
                        },
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Spacer(Modifier.height(8.dp))
                    // Wskaźnik + coaching tylko w fazie wysiłku przy pomiarze tętna.
                    if (t.phase.kind == TrainingPhaseKind.EXERCISE && t.measuringPulse) {
                        val lim = t.heartRateLimit
                        val tone = HeartRateCoach.valueTone(t.pulseBpm, lim)
                        val valueColor = when (tone) {
                            HeartRateValueTone.IN_ZONE -> ProPlusColors.ResultGood
                            HeartRateValueTone.NEAR_EDGE -> ProPlusColors.ResultWatch
                            HeartRateValueTone.OUT_OF_ZONE -> ProPlusColors.ResultAlert
                            HeartRateValueTone.WAITING -> ProPlusColors.Muted
                        }
                        val cueText = HeartRateCoach.screenText(t.heartRateCue)
                        if (cueText != null) {
                            LaunchedEffect(t.heartRateCue, t.phase.index) {
                                while (true) {
                                    HeartRateCoach.speakText(t.heartRateCue)?.let { phrase ->
                                        viewModel.speakHeartRateCue(phrase)
                                    }
                                    delay(4_000)
                                }
                            }
                        }
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                lim?.let {
                                    Text(
                                        "Cel cyklu ${it.cycle}: ${it.minBpm}–${it.maxBpm} bpm",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = ProPlusColors.Navy,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Spacer(Modifier.height(6.dp))
                                }
                                AnalogGauge(
                                    value = t.pulseBpm?.takeIf { it > 0 }?.toFloat(),
                                    minValue = 40f,
                                    maxValue = 180f,
                                    label = if (t.pulseBpm != null && t.pulseBpm > 0) {
                                        "Tętno z EKG"
                                    } else {
                                        "Oczekiwanie na tętno…"
                                    },
                                    unit = "bpm",
                                    valueColor = valueColor,
                                    zoneMin = lim?.minBpm?.toFloat(),
                                    zoneMax = lim?.maxBpm?.toFloat(),
                                    diameter = 182.dp,
                                )
                                if (cueText != null) {
                                    Spacer(Modifier.height(10.dp))
                                    FlashingCoachBanner(
                                        text = cueText,
                                        accent = when (t.heartRateCue) {
                                            HeartRateCoachCue.SPEED_UP -> CoachBannerColors.speedUp
                                            else -> CoachBannerColors.slowDown
                                        },
                                        modifier = Modifier.widthIn(max = 320.dp),
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    val total = t.phase.durationSec.coerceAtLeast(1)
                    if (t.phase.kind == TrainingPhaseKind.EXERCISE ||
                        t.phase.kind == TrainingPhaseKind.REST
                    ) {
                        LinearProgressIndicator(
                            progress = { t.phaseElapsedSec.toFloat() / total },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            "Pozostało %d:%02d".format(
                                t.phaseRemainingSec / 60,
                                t.phaseRemainingSec % 60,
                            ),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    if (t.acquiringEcg) {
                        Spacer(Modifier.height(8.dp))
                        Text("Init → Offline → pobieranie SCP…", color = ProPlusColors.Navy)
                        LinearProgressIndicator(
                            progress = { 0f },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (t.message.isNotBlank()) {
                        Text(t.message, style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(Modifier.height(12.dp))
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        OutlinedButton(
                            onClick = viewModel::reportEcgEvent,
                            modifier = Modifier.widthIn(min = 200.dp, max = 280.dp),
                        ) {
                            Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Komentarz pacjenta")
                        }
                    }
                    if (t.pausedForEvent) {
                        Spacer(Modifier.height(8.dp))
                        if (t.phase.kind == TrainingPhaseKind.EXERCISE) {
                            Button(
                                onClick = { viewModel.confirmEcgEvent(endTraining = false) },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("Zakończ wysiłek (dalej EKG szczyt)") }
                        }
                        Button(
                            onClick = { viewModel.confirmEcgEvent(endTraining = true) },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Zakończ trening") }
                        TextButton(onClick = viewModel::dismissEcgEvent) { Text("Kontynuuj fazę") }
                    }
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
        RehabStep.ADMISSION_WAIT -> "4. Dopuszczenie"
        RehabStep.TRAINING -> "5. Trening sekwencyjny"
        RehabStep.SUMMARY -> "6. Podsumowanie"
    }
    Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun IntroContent(
    onStart: (includeWeight: Boolean, limits: List<CycleHeartRateLimit>) -> Unit,
) {
    var includeWeight by remember { mutableStateOf(true) }
    val defaults = remember { HeartRateCoach.defaultLimits(2) }
    var cycleMins by remember { mutableStateOf(defaults.map { it.minBpm }) }
    var cycleMaxs by remember { mutableStateOf(defaults.map { it.maxBpm }) }
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
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Rozpocznij sesję")
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
        TimelineStep(Icons.Outlined.HourglassBottom, "10s", "Dopuszczenie"),
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
    Surface(
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, ProPlusColors.Line),
        color = ProPlusColors.Surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                Icons.Outlined.Favorite,
                contentDescription = null,
                tint = ProPlusColors.Accent,
                modifier = Modifier.size(20.dp),
            )
            Text(
                "C$cycle",
                fontWeight = FontWeight.Bold,
                color = ProPlusColors.Navy,
                modifier = Modifier.width(28.dp),
            )
            CompactBpmStepper(
                value = minBpm,
                onChange = onMinChange,
                contentDescription = "Min cykl $cycle",
            )
            Text("–", color = ProPlusColors.Muted, fontWeight = FontWeight.Bold)
            CompactBpmStepper(
                value = maxBpm,
                onChange = onMaxChange,
                contentDescription = "Max cykl $cycle",
            )
            Text("bpm", style = MaterialTheme.typography.labelSmall, color = ProPlusColors.Muted)
        }
    }
}

@Composable
private fun CompactBpmStepper(
    value: Int,
    onChange: (Int) -> Unit,
    contentDescription: String,
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, ProPlusColors.Line),
        color = ProPlusColors.Bg,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 2.dp),
        ) {
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
                modifier = Modifier.width(32.dp),
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
}

@Composable
private fun SurveyContent(
    answers: Map<String, Boolean>,
    onAnswer: (String, Boolean) -> Unit,
    onSubmit: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "Ankieta przed treningiem",
            style = MaterialTheme.typography.titleMedium,
            color = ProPlusColors.Navy,
        )
        Text(
            "Pogrubiona odpowiedź to ścieżka kwalifikująca; inna dyskwalifikuje.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        DefaultRehabSurvey.questions.forEach { q ->
            Column(Modifier.fillMaxWidth()) {
                Text(q.text, style = MaterialTheme.typography.bodyLarge)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val yesSelected = answers[q.id] == true
                    val noSelected = answers[q.id] == false
                    Row(
                        Modifier
                            .selectable(
                                selected = yesSelected,
                                onClick = { onAnswer(q.id, true) },
                                role = Role.RadioButton,
                            )
                            .padding(end = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = yesSelected, onClick = { onAnswer(q.id, true) })
                        Text(
                            "TAK",
                            fontWeight = if (q.safeAnswerYes) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                    Row(
                        Modifier.selectable(
                            selected = noSelected,
                            onClick = { onAnswer(q.id, false) },
                            role = Role.RadioButton,
                        ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = noSelected, onClick = { onAnswer(q.id, false) })
                        Text(
                            "NIE",
                            fontWeight = if (!q.safeAnswerYes) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
            }
            HorizontalDivider()
        }
        Button(onClick = onSubmit, modifier = Modifier.fillMaxWidth()) {
            Text("Wyślij ankietę")
        }
    }
}

@Composable
private fun SummaryContent(
    state: pl.cardioscp.rehab.session.RehabSessionState,
    onOpenEcg: (pl.cardioscp.rehab.session.SessionEcgEntry) -> Unit,
    onDone: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Podsumowanie sesji", style = MaterialTheme.typography.titleMedium, color = ProPlusColors.Navy)
        state.vitals.bloodPressure?.let {
            Text("Ciśnienie: ${it.summary}")
            if (state.vitals.bloodPressureNote.isNotBlank()) {
                Text("Notatka: ${state.vitals.bloodPressureNote}", style = MaterialTheme.typography.bodySmall)
            }
        }
        state.vitals.weight?.let {
            Text("Waga: ${it.summary}")
        }
        val survey = DefaultRehabSurvey.evaluate(state.surveyAnswers)
        Text("Ankieta: $survey")
        HorizontalDivider()
        Text("Badania EKG z sesji (${state.ecgEntries.size})", fontWeight = FontWeight.SemiBold)
        if (state.ecgEntries.isEmpty()) {
            Text("Brak zapisanych EKG w tej sesji.")
        } else {
            state.ecgEntries.forEach { entry ->
                OutlinedButton(
                    onClick = { onOpenEcg(entry) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("${entry.label} · ${entry.recording.displayName}", maxLines = 2)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
            Text("Zakończ")
        }
    }
}
