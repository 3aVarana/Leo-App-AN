package com.victorarana.leo.data.genai

import android.os.Trace
import android.util.Log
import com.victorarana.leo.BuildConfig
import com.victorarana.leo.data.repositories.ExerciseGenerationError
import com.victorarana.leo.domain.AgeGroup
import com.victorarana.leo.domain.ComprehensionSkill
import com.victorarana.leo.domain.Exercise
import com.victorarana.leo.domain.RoundTopic
import com.victorarana.leo.domain.TopicKind
import com.victorarana.leo.domain.wordCount
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

/**
 * Generates reading comprehension exercises with the on-device model.
 * `GenAiExerciseRepository` wraps it for the rest of the app.
 *
 * The prompts are the iOS ones word for word (Gemini Nano is tuned in phase 9, with the evaluation harness).
 * Swift's `\` line continuations join lines with no newline; here they are `+` on one line.
 */
class ExerciseGenerator internal constructor(
    val language: ContentLanguage,
    val ageGroup: AgeGroup,
    private val model: LanguageModel,
    private val random: Random = Random.Default,
) {
    val wordRange: String
        get() {
            val range = ageGroup.passageWordRange
            val midpoint = (range.first + range.last) / 2
            val sentences = ageGroup.passageSentenceRange
            return "between ${range.first} and ${range.last} words long, about $midpoint words " +
                "in ${sentences.first} to ${sentences.last} sentences"
        }

    /**
     * Apple's prompting guide: a role, MUST for the rules the model keeps breaking, and nothing
     * that only applies to some skills (those go in `ComprehensionSkill.promptHint`).
     */
    val instructions: String
        get() =
            language.withLocaleInstruction(
                "You are an expert reading teacher who writes reading comprehension exercises for " +
                    "${ageGroup.promptAudience}.\n" +
                    "You MUST write the passage, title, question, answers and explanation in ${language.name}.\n" +
                    "The passage is a story or an informative text $wordRange. ${ageGroup.styleGuidance}\n" +
                    "Write it as it would appear in a book for these readers: original, accurate, and about the " +
                    "topic only. It must never talk about itself: no \"the author\", \"the passage\", \"the text\", " +
                    "\"the main idea\", \"this shows\", and no lesson spelled out at the end. Plain text only, " +
                    "with no markdown, asterisks or headings.\n" +
                    "Then write one question that tests the skill the prompt asks for, one correct answer and " +
                    "three wrong answers. Only the correct answer is supported by the passage; each wrong answer " +
                    "is believable but wrong according to the passage. All four answers MUST have the same " +
                    "length, form and punctuation, so the correct one does not stand out. Do not reuse the " +
                    "question's wording in the correct answer only. Never give away the answer in the question.",
            )

    /** The word range is repeated from the instructions on purpose: the model keeps to it better. */
    fun prompt(
        topic: RoundTopic,
        skill: ComprehensionSkill,
    ): String =
        "Write a reading comprehension exercise in ${language.name} about ${topic.prompt}.\n" +
            "${passageKind(topic)}\n" +
            "The passage must be $wordRange.\n" +
            "Skill to test: ${skill.promptHint}"

    /**
     * Without it, the model turns informative topics into stories about a child, sometimes with
     * nothing left of the topic. Describing a story's plot here made stories 15 to 30 words shorter.
     */
    private fun passageKind(topic: RoundTopic): String =
        when (topic.kind) {
            TopicKind.STORY -> "The passage is a story."
            TopicKind.INFORMATIVE ->
                "The passage is an informative text that explains real, well-known facts about ${topic.prompt}. " +
                    "It is not a story and has no main character."
            // Facts stay the default, so a plain subject like "chess" doesn't turn into a story.
            TopicKind.OPEN ->
                "If the topic asks for a story, the passage is a story. Otherwise it is an informative text " +
                    "that explains real, well-known facts about ${topic.prompt}, with no main character."
        }

    /** The model kept writing the passage past the accepted length. */
    private class RunawayPassage : Exception()

    /**
     * Tries each topic in order, so a topic that trips the model's guardrails
     * or produces unusable content is replaced by the next one. [onAttempt] is called
     * before each topic is tried, so the reader sees the one being written.
     *
     * @throws ExerciseGenerationError when no topic gave a usable exercise.
     */
    suspend fun generate(
        topics: List<RoundTopic>,
        skill: ComprehensionSkill,
        onAttempt: (RoundTopic) -> Unit = {},
    ): Exercise {
        val cookie = nextCookie()
        Trace.beginAsyncSection(EXERCISE_SECTION, cookie)
        try {
            for (topic in topics.take(MAX_ATTEMPTS)) {
                currentCoroutineContext().ensureActive()
                onAttempt(topic)
                attempt(topic, skill)?.let { return it }
            }
            // A cancellation that ML Kit reported as an ordinary exception must not end as a failure.
            currentCoroutineContext().ensureActive()
            throw ExerciseGenerationError()
        } finally {
            Trace.endAsyncSection(EXERCISE_SECTION, cookie)
        }
    }

    /**
     * Writes one exercise about [topic], or returns null when it's rejected or generation fails,
     * so the next topic is tried.
     */
    private suspend fun attempt(
        topic: RoundTopic,
        skill: ComprehensionSkill,
    ): Exercise? {
        val cookie = nextCookie()
        Trace.beginAsyncSection(ATTEMPT_SECTION, cookie)
        try {
            // A fresh request per exercise keeps each one well inside the context window.
            val generated = respond(prompt(topic, skill))
            return generated.toExercise(topic, skill, ageGroup.acceptedWordCount, language, random)
        } catch (rejected: ExerciseRejectedException) {
            Log.e(TAG, "Rejected ${skill.rawValue} exercise (${rejected.rejection.rawValue}) for ${loggable(topic)}")
        } catch (_: RunawayPassage) {
            Log.e(TAG, "Runaway passage for ${loggable(topic)}")
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (
            @Suppress("TooGenericExceptionCaught") error: Exception,
        ) {
            // ML Kit may report a cancelled collector as GenAiException(CANCELLED): cancellation wins (iOS does too).
            currentCoroutineContext().ensureActive()
            Log.e(TAG, "Generation failed for ${loggable(topic)}: $error")
        } finally {
            Trace.endAsyncSection(ATTEMPT_SECTION, cookie)
        }
        return null
    }

    /**
     * Streams the response so a passage that never ends is abandoned as soon as it runs past
     * the accepted length, after seconds rather than when the token cap is reached.
     */
    private suspend fun respond(prompt: String): GeneratedExercise {
        var complete: GeneratedExercise? = null
        model.streamExercise(instructions, prompt, generationOptions).collect { snapshot ->
            val passage = snapshot.passage
            if (passage != null && passage.wordCount > ageGroup.acceptedWordCount.last) throw RunawayPassage()
            snapshot.complete?.let { complete = it }
        }
        return complete ?: throw ExerciseGenerationError()
    }

    companion object {
        private const val TAG = "ExerciseGenerator"
        private const val MAX_ATTEMPTS = 3
        private const val EXERCISE_SECTION = "Exercise"
        private const val ATTEMPT_SECTION = "Exercise attempt"

        /**
         * A complete exercise takes well under 800 tokens even for long passages in wordier languages.
         * Sometimes the model never stops writing; without a cap it runs until the context window
         * is full, which takes minutes, before the next topic is tried.
         */
        private const val MAX_RESPONSE_TOKENS = 1200

        private const val TEMPERATURE = 0.8f

        /** Public so the live passage-length test generates exactly as the app does. */
        val generationOptions = GenerationOptions(temperature = TEMPERATURE, maxOutputTokens = MAX_RESPONSE_TOKENS)

        private val cookies = AtomicInteger()

        private fun nextCookie(): Int = cookies.incrementAndGet()

        /** iOS logs topics privately by default; `Log` doesn't redact, so they appear in debug builds only. */
        private fun loggable(topic: RoundTopic): String =
            "topic '${if (BuildConfig.DEBUG) topic.prompt else "<private>"}'"
    }
}
