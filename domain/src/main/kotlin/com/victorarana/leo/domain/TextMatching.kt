package com.victorarana.leo.domain

import java.text.BreakIterator
import java.text.Normalizer
import java.util.Locale

private val combiningMarks = Regex("\\p{Mn}+")

/** Whether two topic names are the same, ignoring case and accents. */
fun String.sameTopicName(other: String): Boolean = foldedForMatching() == other.foldedForMatching()

private fun String.foldedForMatching(): String =
    Normalizer
        .normalize(
            this,
            Normalizer.Form.NFD,
        ).replace(combiningMarks, "")
        .uppercase(Locale.ROOT)
        .lowercase(Locale.ROOT)

/**
 * The number of user-perceived characters (grapheme clusters), like Swift's `String.count`.
 * Needs JDK 20+ for extended clusters such as emoji sequences (the project builds on JDK 21; Android's
 * ICU handles them). `TextMatchingTests` fails if a toolchain change breaks this.
 */
val String.characterCount: Int
    get() {
        val iterator = BreakIterator.getCharacterInstance(Locale.ROOT)
        iterator.setText(this)
        var count = 0
        while (iterator.next() != BreakIterator.DONE) count++
        return count
    }
