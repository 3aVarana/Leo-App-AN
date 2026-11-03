package com.victorarana.leo.data.genai

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Debug builds only (`app/src/debug/`, never in release): a [LanguageModel] that writes canned exercises, so the
 * whole app can be walked on an emulator or a device without Gemini Nano. Not part of iOS, whose simulator has the real
 * model.
 *
 * It streams the passage a few words at a time, like the real model, and sizes it from the word range the generator
 * puts in the instructions ("between 80 and 120 words long, about 100 words"), so every age group passes
 * `toExercise`. The text is English whatever the app language. A topic review accepts everything except a topic
 * that contains "unsafe", which gives the rejection a way to be seen.
 */
class StubLanguageModel(
    private val wordDelayMillis: Long = WORD_DELAY_MILLIS,
) : LanguageModel {
    override fun streamExercise(
        instructions: String,
        prompt: String,
        options: GenerationOptions,
    ): Flow<ExerciseSnapshot> =
        flow {
            val topic = topicOf(prompt)
            val words = passageWords(targetWordCount(instructions), topic)
            var written = ""
            words.chunked(WORDS_PER_STEP).forEach { chunk ->
                written = (written + " " + chunk.joinToString(" ")).trim()
                emit(ExerciseSnapshot(passage = written))
                delay(wordDelayMillis * chunk.size)
            }
            emit(ExerciseSnapshot(passage = written, complete = exercise(written, topic)))
        }

    override suspend fun reviewTopic(
        instructions: String,
        prompt: String,
        options: GenerationOptions,
    ): TopicReview {
        delay(REVIEW_DELAY_MILLIS)
        val text =
            prompt
                .removePrefix("Topic: ")
                .lineSequence()
                .first()
                .trim()
        return if (text.contains("unsafe", ignoreCase = true)) {
            TopicReview(isSuitable = false, topic = "", reason = "The stub model rejects topics that say \"unsafe\".")
        } else {
            TopicReview(isSuitable = true, topic = text.replaceFirstChar { it.titlecase() }, reason = "")
        }
    }

    private fun topicOf(prompt: String): String =
        Regex("""exercise in .+? about (.+?)\.\n""")
            .find(prompt)
            ?.groupValues
            ?.get(1)
            ?.trim() ?: "the world"

    private fun targetWordCount(instructions: String): Int =
        Regex("""about (\d+) words""")
            .find(instructions)
            ?.groupValues
            ?.get(1)
            ?.toInt() ?: DEFAULT_WORDS

    /** [count] words of plain sentences about [topic], the last one ending in a full stop. */
    private fun passageWords(
        count: Int,
        topic: String,
    ): List<String> {
        val words =
            generateSequence(0) { it + 1 }
                .flatMap { round -> sentences(topic, round).asSequence() }
                .flatMap { it.split(' ').asSequence() }
                .take(count.coerceAtLeast(1))
                .toList()
        return words.dropLast(1) + words.last().trimEnd('.', ',') + "."
    }

    private fun sentences(
        topic: String,
        round: Int,
    ): List<String> =
        listOf(
            "People have been curious about $topic for a very long time.",
            "Some of what they found was a surprise, and some of it was exactly what they had hoped to see.",
            "Every new discovery asked a new question, and each question led to another place worth visiting.",
            "Children who read about $topic often remember the small details first, like a colour or a sound.",
            "Later they put the details together and see the whole picture, which is the best part (round $round).",
        )

    private fun exercise(
        passage: String,
        topic: String,
    ) = GeneratedExercise(
        passage = passage,
        question = "What were people curious about for a very long time?",
        correctAnswer = "They were curious about $topic",
        incorrectAnswers =
            listOf(
                "They were curious about the weather",
                "They were curious about old maps",
                "They were curious about the moon",
            ),
        explanation = "Early on, it says people have been curious about $topic for a very long time.",
        title = topic.replaceFirstChar { it.titlecase() },
    )

    private companion object {
        const val WORD_DELAY_MILLIS = 25L
        const val WORDS_PER_STEP = 4
        const val REVIEW_DELAY_MILLIS = 600L
        const val DEFAULT_WORDS = 100
    }
}
