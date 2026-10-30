package com.victorarana.leo.data.room

import com.victorarana.leo.domain.AgeGroup
import com.victorarana.leo.domain.ComprehensionSkill
import com.victorarana.leo.domain.Exercise
import com.victorarana.leo.domain.RoundAnswer
import com.victorarana.leo.domain.RoundRecord
import com.victorarana.leo.domain.RoundSummary
import com.victorarana.leo.domain.RoundTopic
import com.victorarana.leo.domain.TopicKind
import java.util.UUID

internal fun RoundRecord.toStored(): StoredRoundWithAnswers =
    StoredRoundWithAnswers(
        round =
            StoredRound(
                id = id,
                finishedAt = finishedAt,
                ageGroup = ageGroup.rawValue,
                results = answers.map { it.isCorrect },
            ),
        answers = answers.map { it.toStored(roundId = id) },
    )

private fun RoundAnswer.toStored(roundId: UUID): StoredAnswer =
    StoredAnswer(
        roundId = roundId,
        index = index,
        exerciseID = exercise.id,
        topicPrompt = exercise.topic.prompt,
        topicName = exercise.topic.name,
        topicKind = exercise.topic.kind.rawValue,
        skill = exercise.skill.rawValue,
        title = exercise.title,
        passage = exercise.passage,
        question = exercise.question,
        options = exercise.options,
        correctIndex = exercise.correctIndex,
        selectedOption = selectedOption,
        explanation = exercise.explanation,
    )

/** `null` when the id isn't a UUID or `results` isn't a JSON list of booleans. */
internal fun StoredRoundSummary.toSummaryOrNull(): RoundSummary? {
    val converters = RoundHistoryConverters()
    return try {
        RoundSummary(
            id = converters.stringToUuid(id),
            finishedAt = finishedAt,
            results = converters.jsonToBooleans(results),
        )
    } catch (_: IllegalArgumentException) {
        // Covers SerializationException too, which extends it.
        null
    }
}

/** `null` when the stored round has become unreadable: an unknown raw value, or an index out of range. */
internal fun StoredRoundWithAnswers.toRecordOrNull(): RoundRecord? {
    val ageGroup = AgeGroup.fromRawValue(round.ageGroup)
    val sorted = answers.sortedBy { it.index }
    val restored = sorted.mapNotNull { it.toAnswerOrNull() }
    return if (ageGroup == null || restored.size != sorted.size) {
        null
    } else {
        RoundRecord(id = round.id, finishedAt = round.finishedAt, ageGroup = ageGroup, answers = restored)
    }
}

/** Checks the indices before building the [Exercise], whose `require` would throw. */
internal fun StoredAnswer.toAnswerOrNull(): RoundAnswer? {
    val kind = TopicKind.fromRawValue(topicKind)
    val comprehensionSkill = ComprehensionSkill.fromRawValue(skill)
    val indicesInRange = correctIndex in options.indices && selectedOption in options.indices
    return if (kind == null || comprehensionSkill == null || !indicesInRange) {
        null
    } else {
        val exercise =
            Exercise(
                id = exerciseID,
                topic = RoundTopic(prompt = topicPrompt, name = topicName, kind = kind),
                skill = comprehensionSkill,
                title = title,
                passage = passage,
                question = question,
                options = options,
                correctIndex = correctIndex,
                explanation = explanation,
            )
        RoundAnswer(index = index, exercise = exercise, selectedOption = selectedOption)
    }
}
