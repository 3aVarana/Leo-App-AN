package com.victorarana.leo.livemodel

import com.victorarana.leo.data.genai.ContentLanguage
import com.victorarana.leo.data.genai.MlKitLanguageModel
import com.victorarana.leo.data.genai.TopicValidator
import com.victorarana.leo.domain.AgeGroup
import com.victorarana.leo.domain.TopicReviewOutcome
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Results vary between runs: a failure means "look at the prompts", not a broken build. */
@LiveModel
class TopicValidatorLiveTests {
    @get:Rule
    val liveModel = LiveModelRule(timeoutMinutes = 1)

    private val validator = TopicValidator(ContentLanguage.english, MlKitLanguageModel.shared)
    private val genericRejection = "That topic isn't a good fit for your reading practice. Try another one."

    private fun review(
        text: String,
        group: AgeGroup,
    ) = runBlocking { validator.review(text, group, genericRejection) }

    @Test
    fun acceptsDinosaursForYoungReaders() {
        val outcome = review("dinosaurs", AgeGroup.SIX)
        assertTrue("Expected accepted, got $outcome", outcome is TopicReviewOutcome.Accepted)
        val topic = (outcome as TopicReviewOutcome.Accepted).name
        assertFalse(topic.isEmpty())
        assertEquals(topic, topic.trim { it.isWhitespace() || Character.getType(it).toByte() in punctuationTypes })
    }

    @Test
    fun acceptsSpaceExplorationForAdults() {
        val outcome = review("space exploration", AgeGroup.ADULT)
        assertTrue("Expected accepted, got $outcome", outcome is TopicReviewOutcome.Accepted)
    }

    @Test
    fun rejectsGoryHorrorForYoungReaders() {
        val outcome = review("gory horror movies", AgeGroup.SIX)
        assertTrue("Expected rejected, got $outcome", outcome is TopicReviewOutcome.Rejected)
        assertFalse((outcome as TopicReviewOutcome.Rejected).reason.isEmpty())
    }

    private val punctuationTypes =
        listOf(
            Character.CONNECTOR_PUNCTUATION,
            Character.DASH_PUNCTUATION,
            Character.START_PUNCTUATION,
            Character.END_PUNCTUATION,
            Character.INITIAL_QUOTE_PUNCTUATION,
            Character.FINAL_QUOTE_PUNCTUATION,
            Character.OTHER_PUNCTUATION,
        )
}
