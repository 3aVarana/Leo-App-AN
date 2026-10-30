package com.victorarana.leo.domain

import kotlinx.serialization.Serializable

/**
 * The reader's choices, saved so later launches skip onboarding.
 *
 * Immutable: the editing functions in `ReaderPreferencesTopicEditing.kt` return a changed copy.
 * JSON shape and defaults are in [ReaderPreferencesSerializer].
 *
 * @property disabledDefaultTopics Ids of the suggested topics the reader turned off. Stored instead of the
 * enabled ones, so suggested topics added in later versions start out enabled.
 * @property isReadingTimerOn Whether the question replaces the passage when its reading time runs out.
 * Without it, the passage stays until the reader moves on.
 * @property exercisesPerRound How many texts each round has, within [RoundSettings.EXERCISE_COUNT_RANGE].
 */
@Serializable(with = ReaderPreferencesSerializer::class)
data class ReaderPreferences(
    val ageGroup: AgeGroup,
    val disabledDefaultTopics: Map<AgeGroup, Set<String>> = emptyMap(),
    val customTopics: Map<AgeGroup, List<CustomTopic>> = emptyMap(),
    val isReadingTimerOn: Boolean = DEFAULT_IS_READING_TIMER_ON,
    val exercisesPerRound: Int = RoundSettings.DEFAULT_EXERCISE_COUNT,
) {
    /** A topic already on the list with a given name. */
    sealed interface TopicMatch {
        data object Enabled : TopicMatch

        data class DisabledDefault(
            val id: String,
        ) : TopicMatch
    }

    /**
     * What adding a topic needs, checked in this order: length, enabled duplicate,
     * disabled suggested topic, custom topic limit.
     */
    sealed interface NewTopicCheck {
        data object InvalidLength : NewTopicCheck

        data object AlreadyOnList : NewTopicCheck

        data class ReEnablesSuggested(
            val id: String,
        ) : NewTopicCheck

        data object TooManyCustomTopics : NewTopicCheck

        data object NeedsReview : NewTopicCheck
    }

    fun isEnabled(
        topic: DefaultTopic,
        group: AgeGroup,
    ): Boolean = disabledDefaultTopics[group]?.contains(topic.id) != true

    /**
     * The topics to write about, in a stable order (suggested topics, then the reader's own),
     * so equal preferences always produce equal round settings.
     */
    fun enabledTopics(
        group: AgeGroup,
        names: TopicNames,
    ): List<RoundTopic> {
        val defaults = DefaultTopics.topics(group)
        val topics =
            defaults.filter { isEnabled(it, group) }.map { it.roundTopic(names, group) } +
                customTopics[group].orEmpty().map { it.roundTopic }
        // Fewer than the minimum only happens with stale data. makePlan needs that many topics,
        // so top up with suggested topics the reader turned off, keeping the ones they chose.
        if (topics.size >= MINIMUM_ENABLED_TOPICS) return topics
        val disabled = defaults.filterNot { isEnabled(it, group) }.map { it.roundTopic(names, group) }
        return topics + disabled.take(MINIMUM_ENABLED_TOPICS - topics.size)
    }

    fun roundSettings(names: TopicNames): RoundSettings =
        RoundSettings(
            ageGroup = ageGroup,
            topics = enabledTopics(ageGroup, names),
            exerciseCount = clampedExerciseCount(exercisesPerRound),
        )

    companion object {
        const val DEFAULT_IS_READING_TIMER_ON = true
        const val MINIMUM_ENABLED_TOPICS = 3
        const val MAXIMUM_CUSTOM_TOPICS = 20

        /** How long a topic the reader types can be. */
        val TOPIC_NAME_LENGTH = 2..60

        fun clampedExerciseCount(count: Int): Int = count.coerceIn(RoundSettings.EXERCISE_COUNT_RANGE)
    }
}
