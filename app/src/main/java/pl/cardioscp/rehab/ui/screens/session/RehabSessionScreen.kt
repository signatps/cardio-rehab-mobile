package pl.cardioscp.rehab.ui.screens.session

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.cardioscp.rehab.session.DefaultRehabSurvey
import pl.cardioscp.rehab.session.RehabStep
import pl.cardioscp.rehab.session.TrainingPhaseKind
import pl.cardioscp.rehab.ui.ble.MeasurePopup
import pl.cardioscp.rehab.ui.screens.HomeViewModel
import pl.cardioscp.rehab.ui.theme.DeepTeal
import pl.cardioscp.rehab.ui.theme.Sand

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
            .background(Sand)
            .padding(horizontal = 16.dp, vertical = 8.dp),
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
                color = DeepTeal,
                modifier = Modifier.weight(1f),
            )
        }

        if (state == null) {
            IntroContent(
                onStart = { includeWeight -> viewModel.startRehabSession(includeWeight) },
            )
            return
        }

        StepHeader(state.step)
        state.statusMessage?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = DeepTeal)
            Spacer(Modifier.height(8.dp))
        }
        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
        }

        when (state.step) {
            RehabStep.INTRO -> IntroContent(
                onStart = { includeWeight -> viewModel.startRehabSession(includeWeight) },
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
                    color = DeepTeal,
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
                    Text(t.phase.label, style = MaterialTheme.typography.headlineSmall, color = DeepTeal)
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
                    if (t.measuringPulse) {
                        Text(
                            t.pulseBpm?.let { "Tętno: $it bpm" } ?: "Tętno: oczekiwanie…",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = DeepTeal,
                        )
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
                        Text("Init → Offline → pobieranie SCP…", color = DeepTeal)
                        LinearProgressIndicator(
                            progress = { 0f },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (t.message.isNotBlank()) {
                        Text(t.message, style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = viewModel::reportEcgEvent,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Zdarzenie EKG (pacjent)")
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
private fun IntroContent(onStart: (includeWeight: Boolean) -> Unit) {
    var includeWeight by remember { mutableStateOf(true) }
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "Przebieg sesji",
            style = MaterialTheme.typography.titleMedium,
            color = DeepTeal,
        )
        Text("1. EKG spoczynkowe: Init → Offline → pobranie SCP")
        Text("2. Ciśnienie tętnicze (BLE)")
        Text("3. Waga — gdy niewydolność serca (BLE)")
        Text("4. Ankieta kwalifikacyjna")
        Text("5. Oczekiwanie na dopuszczenie (10 s)")
        Text("6. Trening: EKG spoczynkowe → 3× (1 min ćwiczenie + puls → EKG szczyt → 1 min odpoczynek)")
        Text("7. Podsumowanie i przegląd EKG z sesji")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = includeWeight, onCheckedChange = { includeWeight = it })
            Text("Niewydolność serca — mierz także wagę")
        }
        Button(onClick = { onStart(includeWeight) }, modifier = Modifier.fillMaxWidth()) {
            Text("Rozpocznij sesję")
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
            color = DeepTeal,
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
        Text("Podsumowanie sesji", style = MaterialTheme.typography.titleMedium, color = DeepTeal)
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
