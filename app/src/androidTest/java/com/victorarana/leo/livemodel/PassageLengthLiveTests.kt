package com.victorarana.leo.livemodel

import android.icu.text.BreakIterator
import android.icu.util.ULocale
import android.util.Log
import com.victorarana.leo.data.genai.ContentLanguage
import com.victorarana.leo.data.genai.ExerciseGenerator
import com.victorarana.leo.data.genai.ExerciseRejectedException
import com.victorarana.leo.data.genai.GeneratedExercise
import com.victorarana.leo.data.genai.MlKitLanguageModel
import com.victorarana.leo.data.genai.toExercise
import com.victorarana.leo.domain.AgeGroup
import com.victorarana.leo.domain.ComprehensionSkill
import com.victorarana.leo.domain.DefaultTopics
import com.victorarana.leo.domain.RoundTopic
import com.victorarana.leo.domain.TopicNames
import com.victorarana.leo.domain.wordCount
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import kotlin.time.measureTime

/**
 * Measures passage lengths on the device that runs it, for docs/Leo-Passage-Length-Plan.md (step 0). One
 * attempt per run, with no replacement topic and no runaway check. It records numbers instead of checking
 * them: read the `PASSAGE-LENGTH` lines in logcat (tag `PassageLength`), and the passages in `PASSAGE-TEXT`.
 */
@LiveModel
@RunWith(Parameterized::class)
class PassageLengthLiveTests(
    private val topicId: String,
    private val skill: ComprehensionSkill,
) {
    @get:Rule
    val liveModel = LiveModelRule(timeoutMinutes = 2)

    @Test
    fun passageLengths() {
        val topic = DefaultTopics.topics(group).first { it.id == topicId }.roundTopic(TopicNames.English, group)
        repeat(RUNS) { measure(topicId, topic, skill, it + 1) }
    }

    private fun measure(
        topicId: String,
        topic: RoundTopic,
        skill: ComprehensionSkill,
        run: Int,
    ) = runBlocking {
        val generator = ExerciseGenerator(ContentLanguage.english, group, MlKitLanguageModel.shared)
        var line = "PASSAGE-LENGTH | ${group.rawValue} | ${skill.rawValue} | $topicId | #$run"
        val elapsed =
            measureTime {
                try {
                    val snapshots =
                        MlKitLanguageModel.shared
                            .streamExercise(
                                generator.instructions,
                                generator.prompt(topic, skill),
                                ExerciseGenerator.generationOptions,
                            ).toList()
                    val generated = checkNotNull(snapshots.lastOrNull()?.complete) { "no complete exercise" }
                    val passage = generated.passage.trim()
                    line +=
                        " | words=${passage.wordCount} | sentences=${sentenceCount(
                            passage,
                        )} | ${outcome(generated, topic, skill)}"
                    Log.i(
                        TAG,
                        "PASSAGE-TEXT | ${skill.rawValue} | $topicId | #$run | ${generated.title} | ${passage.replace(
                            "\n",
                            " / ",
                        )}",
                    )
                } catch (
                    @Suppress("TooGenericExceptionCaught") error: Exception,
                ) {
                    line += " | error: $error"
                }
            }
        Log.i(TAG, "$line | $elapsed")
    }

    private fun outcome(
        generated: GeneratedExercise,
        topic: RoundTopic,
        skill: ComprehensionSkill,
    ): String =
        try {
            generated.toExercise(topic, skill, group.acceptedWordCount, ContentLanguage.english)
            "accepted"
        } catch (e: ExerciseRejectedException) {
            "rejected: ${e.rejection.rawValue}"
        }

    private fun sentenceCount(text: String): Int {
        val iterator = BreakIterator.getSentenceInstance(ULocale.ENGLISH)
        iterator.setText(text)
        var count = 0
        while (iterator.next() != BreakIterator.DONE) count++
        return count
    }

    companion object {
        private const val TAG = "PassageLength"
        private const val RUNS = 2

        /** 15-17, or [AgeGroup.ADULT] to measure 18+. The other groups' topics aren't listed here. */
        private val group = AgeGroup.FIFTEEN

        private val topicIds =
            if (group == AgeGroup.ADULT) {
                listOf("historic-turning-points", "science-breakthroughs", "psychology-habits")
            } else {
                listOf("ancient-civilizations", "sports-science", "animal-behavior")
            }

        @JvmStatic
        @Parameterized.Parameters(name = "{0} {1}")
        fun cases(): List<Array<Any>> =
            topicIds.flatMap { id -> ComprehensionSkill.entries.map { arrayOf<Any>(id, it) } }
    }
}
