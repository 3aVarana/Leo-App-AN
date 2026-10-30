package com.victorarana.leo.domain

/**
 * Whether a topic asks for a story or for facts. Stories are written more freely than facts.
 * [OPEN] is a reader's own topic, which could be either: the model decides from the phrase.
 * Raw values are persisted with a saved round. Never change them.
 */
enum class TopicKind(
    val rawValue: String,
) {
    STORY("story"),
    INFORMATIVE("informative"),
    OPEN("open"),
    ;

    companion object {
        fun fromRawValue(rawValue: String): TopicKind? = entries.firstOrNull { it.rawValue == rawValue }
    }
}

/**
 * A topic of a round: the English phrase the model writes about, and the name the reader sees.
 *
 * @property prompt The English phrase for the model.
 * @property name Shown to the reader: the localized name of a suggested topic, or the name of the reader's own.
 */
data class RoundTopic(
    val prompt: String,
    val name: String,
    val kind: TopicKind = TopicKind.INFORMATIVE,
)
