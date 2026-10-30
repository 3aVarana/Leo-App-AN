package com.victorarana.leo.domain

import java.text.BreakIterator
import java.util.Locale

/**
 * Counts words linguistically, so languages written without spaces (e.g. Japanese) work too.
 * Segments made only of punctuation or spaces don't count.
 */
val String.wordCount: Int
    get() {
        val iterator = BreakIterator.getWordInstance(Locale.ROOT)
        iterator.setText(this)
        var count = 0
        var start = iterator.first()
        var end = iterator.next()
        while (end != BreakIterator.DONE) {
            if (substring(start, end).codePoints().anyMatch { Character.isLetterOrDigit(it) }) count++
            start = end
            end = iterator.next()
        }
        return count
    }
