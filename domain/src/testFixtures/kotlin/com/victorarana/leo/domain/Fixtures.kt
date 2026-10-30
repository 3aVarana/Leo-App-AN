package com.victorarana.leo.domain

import java.time.Instant
import java.util.UUID

// Test data shared by this module's tests and, through the `java-test-fixtures` plugin, by `:app`.
// Swift adds `fixture(...)` factories as static extensions; these are top-level functions instead,
// since Kotlin extensions on a companion need a companion object the production types don't have.

/** A passage of exactly [n] words. */
fun words(n: Int): String {
    val vocabulary = listOf("the", "small", "fox", "ran", "across", "a", "quiet", "green", "field", "today")
    return (0 until n).joinToString(" ") { vocabulary[it % vocabulary.size] }
}

/** Lets tests write a topic as its prompt. The reader-facing name is the prompt, capitalized. */
fun topic(prompt: String): RoundTopic = RoundTopic(prompt = prompt, name = prompt.replaceFirstChar { it.uppercase() })

/** Preferences with some suggested topics turned off and custom topics added, per group. */
fun readerPreferencesFixture(
    ageGroup: AgeGroup,
    disabled: Map<AgeGroup, Set<String>> = emptyMap(),
    custom: Map<AgeGroup, List<String>> = emptyMap(),
): ReaderPreferences =
    ReaderPreferences(
        ageGroup = ageGroup,
        disabledDefaultTopics = disabled,
        customTopics = custom.mapValues { (_, names) -> names.map { CustomTopic(name = it) } },
    )

private val defaultPassage =
    "Mount Rainier looks calm, but it is an active volcano. Deep under the snow, hot rock " +
        "called magma still moves. Scientists place sensors on its slopes to feel tiny shakes in " +
        "the ground. If the shakes grow stronger, they can warn the towns nearby long before " +
        "anything happens."

private val defaultOptions =
    listOf(
        "To measure how much snow falls",
        "To feel small shakes that could warn of danger",
        "To find the best path to the top",
        "To keep the magma from moving",
    )

/** An exercise with fixed options, built without validation or shuffling. */
@Suppress("LongParameterList")
fun exerciseFixture(
    topic: RoundTopic = topic("volcanoes"),
    skill: ComprehensionSkill = ComprehensionSkill.DETAIL,
    title: String = "The Sleeping Mountain",
    passage: String = defaultPassage,
    question: String = "Why do scientists place sensors on the volcano?",
    options: List<String> = defaultOptions,
    correctIndex: Int = 1,
    explanation: String = "The passage says the sensors feel tiny shakes so scientists can warn nearby towns.",
): Exercise =
    Exercise(
        topic = topic,
        skill = skill,
        title = title,
        passage = passage,
        question = question,
        options = options,
        correctIndex = correctIndex,
        explanation = explanation,
    )

val roundAnswerTitles =
    listOf(
        "The Sleeping Mountain",
        "Pirates of the Silver Bay",
        "Exploring the Sun and Planets",
        "The Inventor's Notebook",
        "A Walk Through the Rainforest Canopy at Dawn",
        "Ancient Egypt's Lost City",
    )

/** A round's answers, `true` for each text answered correctly. Titles are taken from [titles]. */
fun roundAnswers(
    results: List<Boolean>,
    titles: List<String> = roundAnswerTitles,
): List<RoundAnswer> =
    results.mapIndexed { index, isCorrect ->
        val exercise = exerciseFixture(title = titles[index % titles.size])
        val wrong = (exercise.correctIndex + 1) % exercise.options.size
        RoundAnswer(
            index = index,
            exercise = exercise,
            selectedOption = if (isCorrect) exercise.correctIndex else wrong,
        )
    }

/** A finished round with one exercise per entry in [results], correct or not as it says, in round order. */
fun roundRecordFixture(
    ageGroup: AgeGroup = AgeGroup.NINE,
    finishedAt: Instant = Instant.parse("2001-01-01T00:00:00Z"),
    results: List<Boolean> = listOf(true, false, true, true, false, true),
): RoundRecord =
    RoundRecord(
        id = UUID.randomUUID(),
        finishedAt = finishedAt,
        ageGroup = ageGroup,
        answers = roundAnswers(results),
    )
