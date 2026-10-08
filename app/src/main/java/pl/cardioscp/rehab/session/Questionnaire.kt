package pl.cardioscp.rehab.session

/**
 * Ankieta kwalifikacyjna przed treningiem.
 * [safeAnswerYes] = true → bezpieczna odpowiedź to TAK; false → bezpieczna to NIE.
 * Odpowiedź inna niż bezpieczna dyskwalifikuje pacjenta.
 */
data class SurveyQuestion(
    val id: String,
    val text: String,
    val safeAnswerYes: Boolean,
)

enum class SurveyOutcome { PASS, DISQUALIFIED, INCOMPLETE }

object DefaultRehabSurvey {
    val questions: List<SurveyQuestion> = listOf(
        SurveyQuestion(
            id = "health_change",
            text = "Czy twój stan zdrowia zmienił się w stosunku do dnia wczorajszego?",
            safeAnswerYes = false,
        ),
        SurveyQuestion(
            id = "tired",
            text = "Czy czujesz się bardzo zmęczony?",
            safeAnswerYes = false,
        ),
        SurveyQuestion(
            id = "chest_pain",
            text = "Czy odczuwasz bóle w klatce piersiowej?",
            safeAnswerYes = false,
        ),
        SurveyQuestion(
            id = "arrhythmia",
            text = "Czy czujesz arytmię serca?",
            safeAnswerYes = false,
        ),
        SurveyQuestion(
            id = "dyspnea",
            text = "Czy jest Ci duszno?",
            safeAnswerYes = false,
        ),
        SurveyQuestion(
            id = "infection",
            text = "Czy czujesz objawy infekcji?",
            safeAnswerYes = false,
        ),
        SurveyQuestion(
            id = "meds",
            text = "Czy przyjąłeś leki?",
            safeAnswerYes = true,
        ),
    )

    fun evaluate(answers: Map<String, Boolean>): SurveyOutcome {
        if (questions.any { it.id !in answers }) return SurveyOutcome.INCOMPLETE
        val ok = questions.all { q ->
            answers[q.id] == q.safeAnswerYes
        }
        return if (ok) SurveyOutcome.PASS else SurveyOutcome.DISQUALIFIED
    }
}
