package com.victorarana.leo.livemodel

import android.icu.util.ULocale
import com.victorarana.leo.data.genai.ContentLanguage
import com.victorarana.leo.data.genai.ExerciseGenerator
import com.victorarana.leo.data.genai.ExerciseRepair
import com.victorarana.leo.domain.AgeGroup
import com.victorarana.leo.domain.ComprehensionSkill
import com.victorarana.leo.domain.DefaultTopics
import com.victorarana.leo.domain.TopicNames
import com.victorarana.leo.domain.wordCount
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Results vary between runs: a failure means "look at the prompts", not a broken build.
 * One row per case, so each gets its own time limit like Swift's `@Test(arguments:)`.
 */
@LiveModel
@RunWith(Parameterized::class)
class ExerciseGeneratorLiveTests(
    private val group: AgeGroup,
    private val skill: ComprehensionSkill,
    private val language: ContentLanguage,
) {
    @get:Rule
    val liveModel = LiveModelRule(timeoutMinutes = 1)

    /**
     * The generator already repairs and validates its output, so this confirms the prompts
     * produce usable exercises within its 3 attempts, and that the rules held.
     */
    @Test
    fun generatesUsableExercise() =
        runBlocking {
            val topics = DefaultTopics.topics(group).take(3).map { it.roundTopic(TopicNames.English, group) }
            val generator = ExerciseGenerator(language, group, liveLanguageModel)

            val exercise = generator.generate(topics, skill)

            val label = "$group $skill ${language.locale}"
            assertTrue(label, exercise.passage.wordCount in group.acceptedWordCount)
            assertTrue(label, exercise.topic in topics)
            assertFalse(label, exercise.question.isEmpty())
            assertEquals(label, 4, exercise.options.size)
            assertFalse(label, ExerciseRepair.refersToItself(exercise.passage, language))
            assertEquals(label, exercise.passage, ExerciseRepair.removingMarkdown(exercise.passage))
            assertEquals(label, exercise.options, ExerciseRepair.matchingPunctuation(exercise.options))
            assertEquals(label, exercise.explanation, ExerciseRepair.completeExplanation(exercise.explanation))
            val distractors = exercise.options.toMutableList()
            val correct = distractors.removeAt(exercise.correctIndex)
            assertFalse(label, ExerciseRepair.correctAnswerStandsOut(correct, distractors))
        }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{index}: {0} {1} {2}")
        fun cases(): List<Array<Any>> {
            val english = ContentLanguage.english
            val spanish = ContentLanguage(ULocale("es_ES"))
            return AgeGroup.entries.map { arrayOf<Any>(it, ComprehensionSkill.MAIN_IDEA, english) } +
                listOf(AgeGroup.NINE, AgeGroup.ADULT).flatMap { group ->
                    ComprehensionSkill.entries.map { arrayOf<Any>(group, it, english) }
                } +
                listOf(arrayOf<Any>(AgeGroup.NINE, ComprehensionSkill.INFERENCE, spanish))
        }
    }
}
