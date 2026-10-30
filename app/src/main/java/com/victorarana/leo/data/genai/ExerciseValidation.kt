package com.victorarana.leo.data.genai

import com.victorarana.leo.domain.ComprehensionSkill
import com.victorarana.leo.domain.Exercise
import com.victorarana.leo.domain.RoundTopic
import com.victorarana.leo.domain.wordCount
import kotlin.random.Random

/**
 * Repairs and validates model output, in the order of docs/Leo-Exercise-Quality-Plan.md,
 * section 2.4 (iOS repo). The port of `Exercise.init(generated:...)`.
 *
 * @param random Shuffles the answers; tests pass a seeded one.
 * @throws ExerciseRejectedException with the rule that made the content unusable.
 */
fun GeneratedExercise.toExercise(
    topic: RoundTopic,
    skill: ComprehensionSkill,
    acceptedWordCount: IntRange,
    language: ContentLanguage,
    random: Random = Random.Default,
): Exercise {
    fun clean(text: String): String = ExerciseRepair.removingMarkdown(text.trim())

    /** Answers that differ only in case or a final period are duplicates. */
    fun key(answer: String): String = answer.lowercase().trim('.', '。')

    fun reject(rejection: ExerciseRejection): Nothing = throw ExerciseRejectedException(rejection)

    val correct = clean(correctAnswer)
    val seen = mutableSetOf(key(correct))
    val distractors = incorrectAnswers.map(::clean).filter { it.isNotEmpty() && seen.add(key(it)) }

    val cleanPassage = clean(ExerciseRepair.joiningLines(ExerciseRepair.removingLeadingTitle(passage)))
    val cleanQuestion = clean(question)
    if (correct.isEmpty() || cleanQuestion.isEmpty()) reject(ExerciseRejection.EMPTY_FIELD)
    // The model always writes 3, but losing a blank or duplicate one shouldn't cost a whole attempt.
    if (distractors.size !in MIN_DISTRACTORS..MAX_DISTRACTORS) reject(ExerciseRejection.DISTRACTORS)
    if (cleanPassage.wordCount !in acceptedWordCount) reject(ExerciseRejection.PASSAGE_LENGTH)
    if (ExerciseRepair.refersToItself(cleanPassage, language)) reject(ExerciseRejection.SELF_REFERENCE)
    if (ExerciseRepair.correctAnswerStandsOut(correct, distractors)) reject(ExerciseRejection.STANDOUT_ANSWER)

    // The correct answer goes first, then the app shuffles them all.
    val answers = ExerciseRepair.matchingPunctuation(listOf(correct) + distractors)
    val order = answers.indices.shuffled(random)
    return Exercise(
        topic = topic,
        skill = skill,
        title = clean(title),
        passage = cleanPassage,
        question = cleanQuestion,
        options = order.map { answers[it] },
        correctIndex = order.indexOf(0),
        explanation = ExerciseRepair.completeExplanation(clean(explanation)),
    )
}

private const val MIN_DISTRACTORS = 2
private const val MAX_DISTRACTORS = 3
