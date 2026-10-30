package com.victorarana.leo.domain

import com.victorarana.leo.domain.ReaderPreferences.NewTopicCheck
import com.victorarana.leo.domain.ReaderPreferences.TopicMatch
import java.util.UUID

// The rules for editing the topics of one age group, shared by onboarding and settings.
// They return what happened; `PreferencesEditorViewModel` turns that into messages. Unlike the
// Swift `mutating` functions they return the changed preferences, since the type is immutable.

fun ReaderPreferences.enabledTopicCount(group: AgeGroup): Int =
    enabledSuggestedTopicCount(group) + customTopics[group].orEmpty().size

fun ReaderPreferences.enabledSuggestedTopicCount(group: AgeGroup): Int =
    DefaultTopics.topics(group).count { isEnabled(it, group) }

/** Whether no more topics can be turned off or removed. */
fun ReaderPreferences.isAtMinimum(group: AgeGroup): Boolean =
    enabledTopicCount(group) <= ReaderPreferences.MINIMUM_ENABLED_TOPICS

/** Finds a topic already on the list with this name, ignoring case and accents. */
fun ReaderPreferences.match(
    name: String,
    group: AgeGroup,
    names: TopicNames,
): TopicMatch? {
    val topic = DefaultTopics.topics(group).firstOrNull { name.sameTopicName(names.name(it, group)) }
    if (topic != null) {
        return if (isEnabled(topic, group)) TopicMatch.Enabled else TopicMatch.DisabledDefault(topic.id)
    }
    return if (customTopics[group].orEmpty().any { name.sameTopicName(it.name) }) TopicMatch.Enabled else null
}

/** What adding [newTopic], already trimmed, to [group] needs. */
fun ReaderPreferences.check(
    newTopic: String,
    group: AgeGroup,
    names: TopicNames,
): NewTopicCheck =
    when {
        newTopic.characterCount !in ReaderPreferences.TOPIC_NAME_LENGTH -> NewTopicCheck.InvalidLength
        else ->
            when (val existing = match(newTopic, group, names)) {
                TopicMatch.Enabled -> NewTopicCheck.AlreadyOnList
                is TopicMatch.DisabledDefault -> NewTopicCheck.ReEnablesSuggested(existing.id)
                null ->
                    if (customTopics[group].orEmpty().size >= ReaderPreferences.MAXIMUM_CUSTOM_TOPICS) {
                        NewTopicCheck.TooManyCustomTopics
                    } else {
                        NewTopicCheck.NeedsReview
                    }
            }
    }

/** Doesn't enforce the minimum: the editor disables the toggle instead. */
fun ReaderPreferences.setEnabled(
    isOn: Boolean,
    topic: DefaultTopic,
    group: AgeGroup,
): ReaderPreferences {
    val disabled =
        if (isOn) {
            val ids = disabledDefaultTopics[group] ?: return this
            disabledDefaultTopics + (group to ids - topic.id)
        } else {
            disabledDefaultTopics + (group to disabledDefaultTopics[group].orEmpty() + topic.id)
        }
    return copy(disabledDefaultTopics = disabled)
}

fun ReaderPreferences.resetSuggestedTopics(group: AgeGroup): ReaderPreferences =
    copy(disabledDefaultTopics = disabledDefaultTopics - group)

/** Doesn't enforce the minimum: the editor hides the remove control instead. */
fun ReaderPreferences.removeCustomTopic(
    id: UUID,
    group: AgeGroup,
): ReaderPreferences {
    val topics = customTopics[group] ?: return this
    return copy(customTopics = customTopics + (group to topics.filterNot { it.id == id }))
}

/**
 * Adds a reviewed phrase, re-enabling the suggested topic it names if there is one.
 * Returns null, changing nothing, when the topic is already enabled (Swift returns `false`).
 */
fun ReaderPreferences.insertTopic(
    name: String,
    group: AgeGroup,
    names: TopicNames,
): ReaderPreferences? =
    when (val existing = match(name, group, names)) {
        TopicMatch.Enabled -> null
        is TopicMatch.DisabledDefault -> {
            val ids = disabledDefaultTopics[group] ?: emptySet()
            copy(disabledDefaultTopics = disabledDefaultTopics + (group to ids - existing.id))
        }
        null -> copy(customTopics = customTopics + (group to customTopics[group].orEmpty() + CustomTopic(name = name)))
    }
