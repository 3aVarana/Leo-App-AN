package com.victorarana.leo.data.genai

import com.victorarana.leo.domain.AgeGroup
import com.victorarana.leo.domain.ComprehensionSkill
import com.victorarana.leo.domain.RoundTopic
import com.victorarana.leo.domain.TopicReviewOutcome
import com.victorarana.leo.domain.wordCount
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.random.Random

/**
 * Android only, debug variant only (`src/testDebug`): the stub model that lets an emulator walk the app writes
 * exercises that `ExerciseRepair` accepts for every age group, so a round with it never lands on "didn't print".
 */
@RunWith(RobolectricTestRunner::class)
class StubLanguageModelTests {
    private companion object {
        const val OPTION_COUNT = 4
    }

    private val topic = RoundTopic(prompt = "volcanoes", name = "Volcanoes")

    @Test
    fun everyAgeGroupGetsAnAcceptedExercise() =
        runTest {
            for (group in AgeGroup.entries) {
                val generator = ExerciseGenerator(ContentLanguage.english, group, StubLanguageModel(0), Random(1))
                for (skill in ComprehensionSkill.entries) {
                    val exercise = generator.generate(listOf(topic), skill)
                    assertTrue("$group $skill", exercise.passage.wordCount in group.acceptedWordCount)
                    assertEquals("$group $skill", OPTION_COUNT, exercise.options.size)
                    assertEquals("Volcanoes", exercise.title)
                }
            }
        }

    @Test
    fun theStubReviewsATopicLikeTheRealOne() =
        runTest {
            val validator = TopicValidator(ContentLanguage.english, StubLanguageModel(0))
            val accepted = validator.review("space cats", AgeGroup.NINE, genericRejection = "no")
            assertEquals(TopicReviewOutcome.Accepted("Space cats"), accepted)
            val rejected = validator.review("something unsafe", AgeGroup.NINE, genericRejection = "no")
            assertTrue(rejected is TopicReviewOutcome.Rejected)
        }
}
