package com.victorarana.leo.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * The reader's age group. Controls how long the passages are and how complex their language is.
 * Raw values are persisted with the reader's preferences and a saved round. Never change them.
 * The localized labels (`displayName`, `shortName`, `summary`) live in the app module.
 */
@Serializable
enum class AgeGroup(
    val rawValue: String,
) {
    @SerialName("6-8")
    SIX("6-8"),

    @SerialName("9-11")
    NINE("9-11"),

    @SerialName("12-14")
    TWELVE("12-14"),

    @SerialName("15-17")
    FIFTEEN("15-17"),

    @SerialName("18+")
    ADULT("18+"),
    ;

    val passageWordRange: IntRange
        get() =
            when (this) {
                SIX -> 50..80
                NINE -> 80..120
                TWELVE -> 100..150
                FIFTEEN -> 120..180
                ADULT -> 160..230
            }

    /**
     * The passage lengths accepted from the model: wider than the requested range, since the
     * model doesn't count words precisely, but excluding passages it never finished or that ran on.
     */
    val acceptedWordCount: IntRange
        get() = passageWordRange.first * 6 / 10..passageWordRange.last * 3 / 2

    /** A slow silent reading pace for this age, in words per minute. */
    val readingWordsPerMinute: Int
        get() =
            when (this) {
                SIX -> 60
                NINE -> 100
                TWELVE -> 140
                FIFTEEN -> 170
                ADULT -> 200
            }

    /**
     * How long the reader gets to read a passage of [wordCount] words: their pace with half as
     * much again to spare, rounded up to 10 seconds, and never less than [MINIMUM_READING_TIME].
     */
    fun readingTime(wordCount: Int): Duration {
        // 90 seconds per `readingWordsPerMinute` words, in tens of seconds rounded up. Integer
        // math, so an exact multiple of 10 isn't rounded up past itself.
        val tens = (wordCount * SECONDS_PER_TEN_WORDS_FACTOR + readingWordsPerMinute - 1) / readingWordsPerMinute
        return maxOf((tens * SECONDS_STEP).seconds, MINIMUM_READING_TIME)
    }

    companion object {
        val MINIMUM_READING_TIME: Duration = 30.seconds

        /** 90 seconds per minute of reading, in tens of seconds. */
        private const val SECONDS_PER_TEN_WORDS_FACTOR = 9
        private const val SECONDS_STEP = 10

        fun fromRawValue(rawValue: String): AgeGroup? = entries.firstOrNull { it.rawValue == rawValue }
    }
}
