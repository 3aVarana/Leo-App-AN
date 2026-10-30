package com.victorarana.leo.data.genai

import com.victorarana.leo.data.repositories.ExerciseGenerationError
import com.victorarana.leo.domain.AgeGroup
import com.victorarana.leo.domain.ComprehensionSkill
import com.victorarana.leo.domain.RoundTopic
import com.victorarana.leo.domain.topic
import com.victorarana.leo.domain.words
import com.victorarana.leo.support.FakeLanguageModel
import com.victorarana.leo.support.generatedExerciseFixture
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.random.Random

/**
 * The attempt loop of `ExerciseGenerator` without the model. Android-only: iOS has no unit test for it
 * (its live-model tests cover it). Robolectric, for `Log`, `Trace` and `android.icu`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ExerciseGeneratorTests {
    private val topics = listOf("foxes", "owls", "bees", "ants", "moths").map(::topic)
    private val valid = generatedExerciseFixture()

    private fun generator(model: FakeLanguageModel) =
        ExerciseGenerator(ContentLanguage.english, AgeGroup.SIX, model, Random(1))

    @Test
    fun firstTopicThatWorksIsUsed() =
        runTest {
            val model = FakeLanguageModel(streams = listOf(FakeLanguageModel.completing(valid)))
            val attempted = mutableListOf<RoundTopic>()

            val exercise = generator(model).generate(topics, ComprehensionSkill.DETAIL) { attempted += it }

            assertEquals(topics.take(1), attempted)
            assertEquals(topics[0], exercise.topic)
            assertEquals(ComprehensionSkill.DETAIL, exercise.skill)
            assertEquals("Across a field", exercise.options[exercise.correctIndex])
            assertEquals(1, model.requests.size)
        }

    @Test
    fun requestUsesTheGeneratorsPromptsAndOptions() =
        runTest {
            val model = FakeLanguageModel(streams = listOf(FakeLanguageModel.completing(valid)))
            val generator = generator(model)

            generator.generate(topics, ComprehensionSkill.INFERENCE)

            val request = model.requests.single()
            assertEquals(generator.instructions, request.instructions)
            assertEquals(generator.prompt(topics[0], ComprehensionSkill.INFERENCE), request.prompt)
            assertEquals(0.8f, request.options.temperature)
            assertEquals(1200, request.options.maxOutputTokens)
        }

    @Test
    fun triesAtMostThreeTopicsInOrderAndThenFails() =
        runTest {
            val rejected = generatedExerciseFixture(passage = words(5))
            val model = FakeLanguageModel(streams = List(5) { FakeLanguageModel.completing(rejected) })
            val attempted = mutableListOf<RoundTopic>()

            val error = runCatching { generator(model).generate(topics, ComprehensionSkill.DETAIL) { attempted += it } }
            assertTrue(error.exceptionOrNull() is ExerciseGenerationError)

            assertEquals(topics.take(3), attempted)
            assertEquals(3, model.requests.size)
        }

    @Test
    fun onAttemptIsCalledBeforeTheRequest() =
        runTest {
            val events = mutableListOf<String>()
            val model =
                FakeLanguageModel(
                    streams =
                        listOf(
                            {
                                events += "request"
                                FakeLanguageModel.completing(valid)()
                            },
                        ),
                )

            generator(model).generate(topics, ComprehensionSkill.DETAIL) { events += "attempt ${it.prompt}" }

            assertEquals(listOf("attempt foxes", "request"), events)
        }

    @Test
    fun rejectedExerciseMovesOnToTheNextTopic() =
        runTest {
            val model =
                FakeLanguageModel(
                    streams =
                        listOf(
                            FakeLanguageModel.completing(generatedExerciseFixture(question = " ")),
                            FakeLanguageModel.completing(valid),
                        ),
                )
            val attempted = mutableListOf<RoundTopic>()

            val exercise = generator(model).generate(topics, ComprehensionSkill.DETAIL) { attempted += it }

            assertEquals(topics.take(2), attempted)
            assertEquals(topics[1], exercise.topic)
        }

    @Test
    fun modelErrorMovesOnToTheNextTopic() =
        runTest {
            val model =
                FakeLanguageModel(
                    streams =
                        listOf(
                            FakeLanguageModel.failing(IllegalStateException("guardrail")),
                            FakeLanguageModel.completing(valid),
                        ),
                )

            val exercise = generator(model).generate(topics, ComprehensionSkill.DETAIL)

            assertEquals(topics[1], exercise.topic)
        }

    @Test
    fun streamEndingWithoutAnExerciseCountsAsFailed() =
        runTest {
            val model =
                FakeLanguageModel(
                    streams =
                        listOf(
                            { flowOf(ExerciseSnapshot(passage = "Once")) },
                            FakeLanguageModel.completing(valid),
                        ),
                )

            assertEquals(topics[1], generator(model).generate(topics, ComprehensionSkill.DETAIL).topic)
        }

    @Test
    fun runawayPassageIsAbandonedAndTheNextTopicTried() =
        runTest {
            val model =
                FakeLanguageModel(streams = listOf(FakeLanguageModel.runaway(), FakeLanguageModel.completing(valid)))

            val exercise = generator(model).generate(topics, ComprehensionSkill.DETAIL)

            assertEquals(topics[1], exercise.topic)
            // AgeGroup.SIX accepts up to 120 words: the stream was dropped at the 121st, not read to its end.
            val accepted = AgeGroup.SIX.acceptedWordCount.last
            assertEquals(accepted + 1 + 3, model.emittedSnapshots)
        }

    @Test
    fun cancellationFromTheModelIsNotSwallowed() =
        runTest {
            val model = FakeLanguageModel(streams = listOf(FakeLanguageModel.failing(CancellationException("stop"))))

            val error = runCatching { generator(model).generate(topics, ComprehensionSkill.DETAIL) }
            assertTrue(error.exceptionOrNull() is CancellationException)

            assertEquals(1, model.requests.size)
        }

    @Test
    fun cancellingTheCallerStopsBeforeTheNextTopic() =
        runTest {
            val model =
                FakeLanguageModel(
                    streams = List(3) { FakeLanguageModel.completing(generatedExerciseFixture(passage = words(5))) },
                )
            val attempted = mutableListOf<RoundTopic>()
            var failure: Throwable? = null

            val job =
                launch {
                    try {
                        generator(model).generate(topics, ComprehensionSkill.DETAIL) {
                            attempted += it
                            // Cancel from inside the first attempt: the loop must notice before the second.
                            if (attempted.size == 1) coroutineContext.cancel()
                        }
                    } catch (e: CancellationException) {
                        failure = e
                        throw e
                    }
                }
            advanceUntilIdle()

            assertTrue(job.isCancelled)
            assertNotNull(failure)
            assertEquals(1, attempted.size)
        }

    @Test
    fun anExerciseFailureIsNotCancellation() =
        runTest {
            val model = FakeLanguageModel(streams = List(3) { FakeLanguageModel.failing(IllegalStateException("x")) })

            val error = runCatching { generator(model).generate(topics, ComprehensionSkill.DETAIL) }.exceptionOrNull()

            assertTrue(error is ExerciseGenerationError)
            assertFalse(error is CancellationException)
        }

    @Test
    fun cancelledCallerIsNotReportedAsAFailureWhenTheModelThrowsAnOrdinaryException() =
        runTest {
            // ML Kit may surface a cancelled collector as GenAiException(CANCELLED), not CancellationException.
            val model = FakeLanguageModel(streams = List(3) { { flow { throw IllegalStateException("CANCELLED") } } })
            var error: Throwable? = null

            val job =
                launch {
                    try {
                        generator(model).generate(topics, ComprehensionSkill.DETAIL) { coroutineContext.cancel() }
                    } catch (e: Throwable) {
                        error = e
                        throw e
                    }
                }
            advanceUntilIdle()

            assertTrue(job.isCancelled)
            assertTrue(error is CancellationException)
            assertEquals(1, model.requests.size)
        }
}
