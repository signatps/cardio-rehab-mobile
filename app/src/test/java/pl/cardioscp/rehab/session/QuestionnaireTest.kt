package pl.cardioscp.rehab.session

import org.junit.Assert.assertEquals
import org.junit.Test

class QuestionnaireTest {
    @Test
    fun allSafeAnswers_pass() {
        val answers = DefaultRehabSurvey.questions.associate { it.id to it.safeAnswerYes }
        assertEquals(SurveyOutcome.PASS, DefaultRehabSurvey.evaluate(answers))
    }

    @Test
    fun wrongMedsAnswer_disqualifies() {
        val answers = DefaultRehabSurvey.questions.associate { q ->
            q.id to if (q.id == "meds") false else q.safeAnswerYes
        }
        assertEquals(SurveyOutcome.DISQUALIFIED, DefaultRehabSurvey.evaluate(answers))
    }

    @Test
    fun chestPainYes_disqualifies() {
        val answers = DefaultRehabSurvey.questions.associate { q ->
            q.id to if (q.id == "chest_pain") true else q.safeAnswerYes
        }
        assertEquals(SurveyOutcome.DISQUALIFIED, DefaultRehabSurvey.evaluate(answers))
    }

    @Test
    fun incomplete_whenMissing() {
        assertEquals(SurveyOutcome.INCOMPLETE, DefaultRehabSurvey.evaluate(emptyMap()))
    }
}
