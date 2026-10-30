package com.victorarana.leo.data.genai

import com.victorarana.leo.domain.AgeGroup
import com.victorarana.leo.domain.ComprehensionSkill
import com.victorarana.leo.domain.Exercise
import com.victorarana.leo.domain.topic
import com.victorarana.leo.domain.words
import com.victorarana.leo.support.generatedExerciseFixture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The 19 `ExerciseTests` that phase 1 deferred: they test the repair and validation of model output. */
@RunWith(RobolectricTestRunner::class)
class GeneratedExerciseTests {
    private val accepted = AgeGroup.SIX.acceptedWordCount

    private fun validated(generated: GeneratedExercise): Exercise =
        generated.toExercise(topic("foxes"), ComprehensionSkill.DETAIL, accepted, ContentLanguage.english)

    private fun exercise(generated: GeneratedExercise): Exercise? =
        try {
            validated(generated)
        } catch (_: ExerciseRejectedException) {
            null
        }

    private fun rejection(generated: GeneratedExercise): ExerciseRejection? =
        try {
            validated(generated)
            null
        } catch (e: ExerciseRejectedException) {
            e.rejection
        }

    private fun requireExercise(generated: GeneratedExercise): Exercise =
        checkNotNull(exercise(generated)) { "Rejected: ${rejection(generated)}" }

    @Test
    fun validInput() {
        for (incorrect in listOf(
            listOf("Into a cave", "Up a tree"),
            listOf("Into a cave", "Up a tree", "Under a bridge"),
        )) {
            val exercise =
                requireExercise(
                    generatedExerciseFixture(correctAnswer = "Across a field", incorrectAnswers = incorrect),
                )

            assertEquals(incorrect.size + 1, exercise.options.size)
            assertEquals((incorrect + "Across a field").toSet(), exercise.options.toSet())
            assertEquals("Across a field", exercise.options[exercise.correctIndex])
            assertEquals(1, exercise.options.count { it == "Across a field" })
            assertEquals(topic("foxes"), exercise.topic)
            assertEquals(ComprehensionSkill.DETAIL, exercise.skill)
        }
    }

    @Test
    fun trimsWhitespaceAndNewlines() {
        val exercise =
            requireExercise(
                generatedExerciseFixture(
                    title = "  The Quiet Field\n",
                    passage = "\n " + words(60) + " \n",
                    question = "\tWhere did the fox run? ",
                    correctAnswer = " Across a field\n",
                    incorrectAnswers = listOf("  Into a cave", "Up a tree\n", " Under a bridge"),
                    explanation = "\nBecause the passage says so.  ",
                ),
            )

        assertEquals("The Quiet Field", exercise.title)
        assertEquals(words(60), exercise.passage)
        assertEquals("Where did the fox run?", exercise.question)
        assertEquals(setOf("Across a field", "Into a cave", "Up a tree", "Under a bridge"), exercise.options.toSet())
        assertEquals("Across a field", exercise.options[exercise.correctIndex])
        assertEquals("Because the passage says so.", exercise.explanation)
    }

    @Test
    fun dropsEmptyDistractors() {
        val exercise =
            requireExercise(
                generatedExerciseFixture(
                    incorrectAnswers = listOf("Into a cave", "", "  \n", "Up a tree", "Under a bridge"),
                ),
            )
        assertEquals(setOf("Across a field", "Into a cave", "Up a tree", "Under a bridge"), exercise.options.toSet())
    }

    @Test
    fun dropsDuplicateDistractorsIgnoringCase() {
        val exercise =
            requireExercise(
                generatedExerciseFixture(
                    correctAnswer = "Madrid",
                    incorrectAnswers = listOf("Paris", "paris", "Rome", "Lisbon"),
                ),
            )
        assertEquals(4, exercise.options.size)
        assertEquals(setOf("Madrid", "Paris", "Rome", "Lisbon"), exercise.options.toSet())
    }

    @Test
    fun dropsDistractorEqualToCorrectAnswerIgnoringCase() {
        val exercise =
            requireExercise(
                generatedExerciseFixture(
                    correctAnswer = "Madrid",
                    incorrectAnswers = listOf("MADRID", "Paris", "Rome", "Lisbon"),
                ),
            )
        assertEquals(setOf("Madrid", "Paris", "Rome", "Lisbon"), exercise.options.toSet())
    }

    @Test
    fun keepsTwoDistractorsAfterDroppingADuplicate() {
        val exercise =
            requireExercise(
                generatedExerciseFixture(correctAnswer = "Madrid", incorrectAnswers = listOf("Paris", "paris", "Rome")),
            )
        assertEquals(setOf("Madrid", "Paris", "Rome"), exercise.options.toSet())
    }

    @Test
    fun tooFewDistractorsAfterDroppingCorrectAnswer() {
        assertNull(
            exercise(
                generatedExerciseFixture(correctAnswer = "Madrid", incorrectAnswers = listOf("madrid", "Paris", "")),
            ),
        )
    }

    @Test
    fun wrongDistractorCount() {
        val counts =
            listOf(
                listOf("Into a cave"),
                listOf("Into a cave", "Up a tree", "Under a bridge", "Over the hill"),
            )
        for (incorrect in counts) {
            assertNull(
                "$incorrect",
                exercise(generatedExerciseFixture(incorrectAnswers = incorrect)),
            )
        }
    }

    @Test
    fun emptyCorrectAnswer() {
        for (answer in listOf(
            "",
            "  \n",
        )) {
            assertNull("'$answer'", exercise(generatedExerciseFixture(correctAnswer = answer)))
        }
    }

    @Test
    fun emptyQuestion() {
        for (question in listOf(
            "",
            "  \n",
        )) {
            assertNull("'$question'", exercise(generatedExerciseFixture(question = question)))
        }
    }

    @Test
    fun passageWordCount() {
        val cases =
            listOf(
                accepted.first to true,
                accepted.last to true,
                accepted.first - 1 to false,
                accepted.last + 1 to false,
            )
        for ((count, isAccepted) in cases) {
            assertEquals("$count words", isAccepted, exercise(generatedExerciseFixture(passage = words(count))) != null)
        }
    }

    @Test
    fun emptyExplanationIsAccepted() {
        assertEquals("", requireExercise(generatedExerciseFixture(explanation = "  ")).explanation)
    }

    /** Locks current behavior: a missing title doesn't make the exercise unusable. */
    @Test
    fun emptyTitleIsAccepted() {
        for (title in listOf(
            "",
            "  \n",
        )) {
            assertEquals("", requireExercise(generatedExerciseFixture(title = title)).title)
        }
    }

    // region Repairs and rejections (docs/Leo-Exercise-Quality-Plan.md, section 2.4)

    @Test
    fun rejectionReasons() {
        assertEquals(ExerciseRejection.EMPTY_FIELD, rejection(generatedExerciseFixture(question = " ")))
        assertEquals(ExerciseRejection.DISTRACTORS, rejection(generatedExerciseFixture(incorrectAnswers = listOf("A"))))
        assertEquals(ExerciseRejection.PASSAGE_LENGTH, rejection(generatedExerciseFixture(passage = words(10))))
        assertEquals(
            ExerciseRejection.SELF_REFERENCE,
            rejection(generatedExerciseFixture(passage = words(50) + " The author wanted to show how foxes live.")),
        )
        assertEquals(
            ExerciseRejection.STANDOUT_ANSWER,
            rejection(generatedExerciseFixture(correctAnswer = "The fox ran across a quiet green field at dawn")),
        )
    }

    @Test
    fun distractorDifferingOnlyByFinalPeriodIsADuplicate() {
        val exercise =
            requireExercise(
                generatedExerciseFixture(
                    correctAnswer = "Across a field",
                    incorrectAnswers = listOf("Across a field.", "Up a tree", "Under a bridge"),
                ),
            )
        assertEquals(setOf("Across a field", "Up a tree", "Under a bridge"), exercise.options.toSet())
    }

    @Test
    fun matchesPunctuationAndKeepsTheCorrectIndex() {
        val exercise =
            requireExercise(
                generatedExerciseFixture(
                    correctAnswer = "Across a field",
                    incorrectAnswers = listOf("Into a cave.", "Up a tree.", "Under a bridge"),
                ),
            )
        assertEquals(
            setOf("Across a field.", "Into a cave.", "Up a tree.", "Under a bridge."),
            exercise.options.toSet(),
        )
        assertEquals("Across a field.", exercise.options[exercise.correctIndex])
    }

    @Test
    fun removesMarkdown() {
        val exercise =
            requireExercise(
                generatedExerciseFixture(
                    title = "# The *Quiet* Field",
                    passage = "A **fox** " + words(58),
                    question = "Where did the `fox` run?",
                    correctAnswer = "*Across* a field",
                ),
            )
        assertEquals("The Quiet Field", exercise.title)
        assertTrue(exercise.passage.startsWith("A fox the small"))
        assertEquals("Where did the fox run?", exercise.question)
        assertEquals("Across a field", exercise.options[exercise.correctIndex])
    }

    @Test
    fun dropsTruncatedExplanation() {
        assertEquals("", requireExercise(generatedExerciseFixture(explanation = "The mention of")).explanation)
    }

    /**
     * The app, not the model, decides where the correct answer lands. The chance of a false
     * failure is about 4 x (3/4)^200.
     */
    @Test
    fun correctAnswerLandsInEveryPosition() {
        val positions = mutableSetOf<Int>()
        repeat(200) { positions += requireExercise(generatedExerciseFixture()).correctIndex }
        assertEquals(setOf(0, 1, 2, 3), positions)
    }

    // endregion
}
