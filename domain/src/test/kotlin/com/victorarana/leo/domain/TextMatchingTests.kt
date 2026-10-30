package com.victorarana.leo.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextMatchingTests {
    @Test
    fun sameTopicNameIgnoresCaseAndAccents() {
        assertTrue("Música".sameTopicName("MUSICA"))
        assertTrue("MÚSICA".sameTopicName("musica"))
        assertTrue("Dinosaurs".sameTopicName("DINOSAURS"))
        assertFalse("Música".sameTopicName("Musical"))
        // Full case folding, like Foundation: ß matches SS.
        assertTrue("Straße".sameTopicName("STRASSE"))
    }

    /** A decomposed "é" (e + combining accent) is the same name as the precomposed one. */
    @Test
    fun sameTopicNameIgnoresNormalization() {
        assertTrue("Café".sameTopicName("Café"))
    }

    @Test
    fun characterCountCountsGraphemesNotUtf16Units() {
        assertEquals(0, "".characterCount)
        assertEquals(5, "Chess".characterCount)
        assertEquals(1, "😀".characterCount)
        assertEquals(1, "é".characterCount)
        assertEquals(1, "👨‍👩‍👧".characterCount)
        assertEquals(2, "😀😀".characterCount)
    }
}
