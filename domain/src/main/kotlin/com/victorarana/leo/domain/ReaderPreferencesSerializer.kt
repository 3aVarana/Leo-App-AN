package com.victorarana.leo.domain

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json

/**
 * The JSON configuration for the saved [ReaderPreferences]: defaults are written (the saved format
 * always has all five keys, like iOS) and unknown keys are ignored (like `JSONDecoder`).
 *
 * `coerceInputValues` also turns an unknown enum value on a property with a default into that
 * default, without an error. Don't reuse this instance for a new saved type unless that is wanted.
 */
val LeoJson =
    Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
        // An explicit null falls back to the default, like Swift's decodeIfPresent. `ageGroup` has none, so it
        // still throws.
        coerceInputValues = true
    }

/**
 * Reads and writes [ReaderPreferences] in the same JSON as iOS: `ageGroup` is required (an unknown
 * value throws), every other key is optional with its default, and a stored `exercisesPerRound`
 * is clamped into [RoundSettings.EXERCISE_COUNT_RANGE] rather than reset.
 */
object ReaderPreferencesSerializer : KSerializer<ReaderPreferences> {
    @Serializable
    private class Surrogate(
        val ageGroup: AgeGroup,
        val disabledDefaultTopics: Map<AgeGroup, Set<String>> = emptyMap(),
        val customTopics: Map<AgeGroup, List<CustomTopic>> = emptyMap(),
        val isReadingTimerOn: Boolean = ReaderPreferences.DEFAULT_IS_READING_TIMER_ON,
        val exercisesPerRound: Int = RoundSettings.DEFAULT_EXERCISE_COUNT,
    )

    override val descriptor: SerialDescriptor = Surrogate.serializer().descriptor

    override fun serialize(
        encoder: Encoder,
        value: ReaderPreferences,
    ) {
        val surrogate =
            Surrogate(
                ageGroup = value.ageGroup,
                disabledDefaultTopics = value.disabledDefaultTopics,
                customTopics = value.customTopics,
                isReadingTimerOn = value.isReadingTimerOn,
                exercisesPerRound = value.exercisesPerRound,
            )
        encoder.encodeSerializableValue(Surrogate.serializer(), surrogate)
    }

    override fun deserialize(decoder: Decoder): ReaderPreferences {
        val surrogate = decoder.decodeSerializableValue(Surrogate.serializer())
        return ReaderPreferences(
            ageGroup = surrogate.ageGroup,
            disabledDefaultTopics = surrogate.disabledDefaultTopics,
            customTopics = surrogate.customTopics,
            isReadingTimerOn = surrogate.isReadingTimerOn,
            exercisesPerRound = ReaderPreferences.clampedExerciseCount(surrogate.exercisesPerRound),
        )
    }
}
