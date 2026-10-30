package com.victorarana.leo.data.genai

import com.victorarana.leo.R
import com.victorarana.leo.domain.AgeGroup
import com.victorarana.leo.domain.TopicReviewOutcome
import com.victorarana.leo.support.FakeLanguageModel
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class TopicValidatorTests {
    /** Looked up rather than written in English, so the test keeps passing in other languages. */
    private val genericRejection: String
        get() = RuntimeEnvironment.getApplication().getString(R.string.topic_review_generic_rejection)

    private fun outcome(
        isSuitable: Boolean,
        topic: String = "",
        reason: String = "",
    ): TopicReviewOutcome =
        TopicValidator.outcome(TopicReview(isSuitable = isSuitable, topic = topic, reason = reason), genericRejection)

    @Test
    fun accepted() {
        val cases =
            listOf(
                "Dinos!" to "Dinos",
                "  ¿Planetas?  " to "Planetas",
                "Space exploration" to "Space exploration",
                "\n\"Rock music\".\n" to "Rock music",
            )
        for ((topic, expected) in cases) {
            assertEquals(topic, TopicReviewOutcome.Accepted(expected), outcome(isSuitable = true, topic = topic))
        }
    }

    @Test
    fun suitableWithoutTopic() {
        for (topic in listOf("", "  ", "?!...", " ¡¿? ")) {
            assertEquals(
                "'$topic'",
                TopicReviewOutcome.Rejected(genericRejection),
                outcome(isSuitable = true, topic = topic),
            )
        }
    }

    @Test
    fun rejectedWithReason() {
        val outcome =
            outcome(isSuitable = false, topic = "Something", reason = "  That's not suitable for young readers.\n")
        assertEquals(TopicReviewOutcome.Rejected("That's not suitable for young readers."), outcome)
    }

    @Test
    fun rejectedWithoutReason() {
        for (reason in listOf("", "  \n")) {
            assertEquals(TopicReviewOutcome.Rejected(genericRejection), outcome(isSuitable = false, reason = reason))
        }
    }

    // Android-only: the review call itself, through the fake model.

    @Test
    fun reviewSendsTheIosInstructionsAtLowTemperature() =
        runTest {
            val model = FakeLanguageModel(reviews = listOf({ TopicReview(true, "Dinos", "") }))

            val outcome =
                TopicValidator(
                    ContentLanguage.english,
                    model,
                ).review("dinos!", AgeGroup.NINE, genericRejection)

            assertEquals(TopicReviewOutcome.Accepted("Dinos"), outcome)
            val request = model.requests.single()
            assertEquals("Topic: dinos!\nWrite the reason in English.", request.prompt)
            assertEquals(0.2f, request.options.temperature)
            assertTrue(request.instructions.startsWith("You review topics a reader wants to practice reading about. "))
            assertTrue(request.instructions.contains("written for children aged 9 to 11, at their level"))
            assertTrue(request.instructions.endsWith("Write the topic phrase and the reason in English."))
        }

    @Test
    fun refusalBecomesTheGenericRejection() =
        runTest {
            val model = FakeLanguageModel(reviews = listOf({ throw ModelRefusedException() }))

            val outcome = TopicValidator(ContentLanguage.english, model).review("x", AgeGroup.SIX, genericRejection)

            assertEquals(TopicReviewOutcome.Rejected(genericRejection), outcome)
        }

    @Test
    fun cancelledCallerIsNotReportedAsARefusal() =
        runTest {
            // ML Kit may surface a cancelled call with the error code that maps to ModelRefusedException.
            var outcome: TopicReviewOutcome? = null

            val job =
                launch {
                    val scope = coroutineContext
                    val model =
                        FakeLanguageModel(
                            reviews =
                                listOf({
                                    scope.cancel()
                                    throw ModelRefusedException()
                                }),
                        )
                    outcome = TopicValidator(ContentLanguage.english, model).review("x", AgeGroup.SIX, genericRejection)
                }
            advanceUntilIdle()

            assertTrue(job.isCancelled)
            assertNull(outcome)
        }

    @Test
    fun otherErrorsAreRethrown() =
        runTest {
            val model = FakeLanguageModel(reviews = listOf({ throw IllegalStateException("boom") }))

            val error =
                runCatching {
                    TopicValidator(
                        ContentLanguage.english,
                        model,
                    ).review("x", AgeGroup.SIX, genericRejection)
                }

            assertTrue(error.exceptionOrNull() is IllegalStateException)
        }
}
