package com.victorarana.leo.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WordCountTests {
    @Test
    fun wordCount() {
        val cases =
            listOf(
                "" to 0,
                "Hello world." to 2,
                "Don't stop, it's 3:45 p.m." to 6,
                "  a\n\nb  " to 2,
            )
        for ((text, expected) in cases) assertEquals(text, expected, text.wordCount)
    }

    /** Hyphens may split words depending on the platform's word breaking. */
    @Test
    fun hyphenatedWords() {
        assertTrue("e-mail well-known".wordCount >= 2)
    }

    /** Japanese has no spaces between words, so counting spaces would give 1. */
    @Test
    fun languagesWithoutSpaces() {
        assertTrue("猫が好きです。犬も好きです。".wordCount > 1)
    }

    /** Letters outside the BMP are surrogate pairs in UTF-16. */
    @Test
    fun lettersOutsideTheBasicPlane() {
        assertEquals(1, "𠮷".wordCount)
        assertEquals(2, "𠮷 𠮷".wordCount)
    }

    @Test
    fun wordsFixture() {
        for (n in listOf(1, 29, 60, 121, 345)) assertEquals(n, words(n).wordCount)
    }
}
