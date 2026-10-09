package pl.cardioscp.rehab.session

import pl.cardioscp.rehab.clinic.PlannedSessionStatus

/** Krok schematu sesji rehab — te same etapy co pasek startu u pacjenta. */
enum class SessionSchemeStepId {
    QUAL_ECG,
    BP,
    WEIGHT,
    SURVEY,
    ADMISSION,
    TRAINING,
    BORG,
    SUMMARY,
}

data class SessionSchemeStep(
    val id: SessionSchemeStepId,
    val badge: String,
    val label: String,
    val done: Boolean,
    /** false = krok pominięty w planie (np. waga wyłączona). */
    val inPlan: Boolean = true,
)

data class SessionSchemeProgress(
    val steps: List<SessionSchemeStep>,
) {
    val doneCount: Int get() = steps.count { it.inPlan && it.done }
    val plannedCount: Int get() = steps.count { it.inPlan }

    companion object {
        fun empty(includeWeight: Boolean = true): SessionSchemeProgress =
            SessionSchemeProgress(baseSteps(includeWeight) { false })

        fun fromArchive(
            archive: ArchivedRehabSession?,
            status: PlannedSessionStatus?,
            includeWeight: Boolean = true,
        ): SessionSchemeProgress {
            if (status == PlannedSessionStatus.DONE) {
                return SessionSchemeProgress(baseSteps(includeWeight) { true })
            }
            if (archive == null) {
                return empty(includeWeight)
            }
            val qual = archive.ecgs.any { RehabSessionArchive.isQualificationEcg(it.label) }
            val bp = !archive.bloodPressureSummary.isNullOrBlank()
            val weightDone = !archive.weightSummary.isNullOrBlank() ||
                archive.surveyPassed != null ||
                archive.surveyAnswers.isNotEmpty()
            val survey = archive.surveyPassed != null || archive.surveyAnswers.isNotEmpty()
            val surveyPassed = archive.surveyPassed == true
            val disqualified = status == PlannedSessionStatus.DISQUALIFIED ||
                archive.surveyPassed == false
            val hasTrainingEcg = archive.ecgs.any {
                !RehabSessionArchive.isQualificationEcg(it.label)
            }
            val training = archive.cycleHrSummaries.isNotEmpty() || hasTrainingEcg
            val admission = surveyPassed && (
                hasTrainingEcg ||
                    archive.cycleHrSummaries.isNotEmpty() ||
                    archive.borgScore != null ||
                    status == PlannedSessionStatus.DONE
                )
            val borg = archive.borgScore != null
            val summary = status == PlannedSessionStatus.DONE ||
                disqualified ||
                borg

            return SessionSchemeProgress(
                listOf(
                    step(SessionSchemeStepId.QUAL_ECG, "K", "Kwalifikacja EKG", qual),
                    step(SessionSchemeStepId.BP, "BP", "Ciśnienie", bp),
                    step(
                        SessionSchemeStepId.WEIGHT,
                        "kg",
                        "Waga",
                        done = weightDone,
                        inPlan = includeWeight,
                    ),
                    step(SessionSchemeStepId.SURVEY, "A", "Ankieta", survey),
                    step(SessionSchemeStepId.ADMISSION, "▶", "Start treningu", admission && !disqualified),
                    step(SessionSchemeStepId.TRAINING, "T", "Trening", training && !disqualified),
                    step(SessionSchemeStepId.BORG, "B", "Borg", borg && !disqualified),
                    step(
                        SessionSchemeStepId.SUMMARY,
                        "Σ",
                        "Podsumowanie",
                        summary || disqualified,
                    ),
                ),
            )
        }

        /** Pełny schemat bez stanu (do paska startu u pacjenta). */
        fun schemeTemplate(includeWeight: Boolean = true): SessionSchemeProgress =
            SessionSchemeProgress(baseSteps(includeWeight) { true }.map { it.copy(done = false) })

        private fun baseSteps(
            includeWeight: Boolean,
            done: (SessionSchemeStepId) -> Boolean,
        ): List<SessionSchemeStep> = listOf(
            step(SessionSchemeStepId.QUAL_ECG, "K", "Kwalifikacja EKG", done(SessionSchemeStepId.QUAL_ECG)),
            step(SessionSchemeStepId.BP, "BP", "Ciśnienie", done(SessionSchemeStepId.BP)),
            step(
                SessionSchemeStepId.WEIGHT,
                "kg",
                "Waga",
                done = done(SessionSchemeStepId.WEIGHT),
                inPlan = includeWeight,
            ),
            step(SessionSchemeStepId.SURVEY, "A", "Ankieta", done(SessionSchemeStepId.SURVEY)),
            step(SessionSchemeStepId.ADMISSION, "▶", "Start treningu", done(SessionSchemeStepId.ADMISSION)),
            step(SessionSchemeStepId.TRAINING, "T", "Trening", done(SessionSchemeStepId.TRAINING)),
            step(SessionSchemeStepId.BORG, "B", "Borg", done(SessionSchemeStepId.BORG)),
            step(SessionSchemeStepId.SUMMARY, "Σ", "Podsumowanie", done(SessionSchemeStepId.SUMMARY)),
        )

        private fun step(
            id: SessionSchemeStepId,
            badge: String,
            label: String,
            done: Boolean,
            inPlan: Boolean = true,
        ) = SessionSchemeStep(id, badge, label, done = done, inPlan = inPlan)
    }
}
