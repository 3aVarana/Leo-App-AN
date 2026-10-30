package com.victorarana.leo.data.genai

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.victorarana.leo.domain.wordCount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The Unicode-sensitive cases of [ExerciseRepair] and `wordCount`, run on a device or emulator: Android's regex
 * and `BreakIterator` are ICU, the JVM's (where the unit tests run) are not (phase 1, deviation 9). Does not
 * need Gemini Nano, so an emulator is enough.
 */
@RunWith(AndroidJUnit4::class)
class RegexAndWordCountDeviceTests {
    @Test
    fun removesAccentedTitleLabelsInAnyCase() {
        assertEquals("El suelo tembló.", ExerciseRepair.removingLeadingTitle("Título: El volcán\nEl suelo tembló."))
        assertEquals("El suelo tembló.", ExerciseRepair.removingLeadingTitle("TÍTULO: El volcán\nEl suelo tembló."))
        assertEquals(
            "A student found a note.",
            ExerciseRepair.removingLeadingTitle("TITLE: Mystery\n\nA student found a note."),
        )
    }

    @Test
    fun leadingTitleRulesKeepUnicodeLettersTogether() {
        assertEquals(
            "El sol brilla fuerte sobre el mar.",
            ExerciseRepair.removingLeadingTitle("Un día en la playa\nEl sol brilla fuerte sobre el mar."),
        )
        assertEquals(
            "In a sunny garden.",
            ExerciseRepair.removingLeadingTitle("**Busy Little Bugs** In a sunny garden."),
        )
        // A bold word followed by a lowercase letter or a non-letter is not a title.
        assertEquals("**Volcanoes** are hot.", ExerciseRepair.removingLeadingTitle("**Volcanoes** are hot."))
        assertEquals("**Árboles** ñandú corre.", ExerciseRepair.removingLeadingTitle("**Árboles** ñandú corre."))
    }

    @Test
    fun markdownKeepsSymbolsBetweenUnicodeLettersAndDigits() {
        assertEquals("café_au_lait", ExerciseRepair.removingMarkdown("café_au_lait"))
        assertEquals("Ünï*cödé", ExerciseRepair.removingMarkdown("Ünï*cödé"))
        assertEquals("Una palabra énfasis.", ExerciseRepair.removingMarkdown("Una *palabra* _énfasis_."))
    }

    @Test
    fun joinsLinesAndKeepsParagraphs() {
        assertEquals(
            "First.\n\nSecond. Still second.",
            ExerciseRepair.joiningLines("First.\n \n\n  Second.\nStill second."),
        )
    }

    @Test
    fun selfReferenceIsWholeWordAndCaseInsensitiveWithAccents() {
        val spanish = ContentLanguage(android.icu.util.ULocale("es_ES"))
        assertTrue(ExerciseRepair.refersToItself("El AUTOR quiso mostrar el cambio.", spanish))
        assertTrue(ExerciseRepair.refersToItself("El PROPÓSITO DEL AUTOR es claro.", spanish))
        assertFalse(ExerciseRepair.refersToItself("La autoridad llegó.", spanish))
        assertTrue(ExerciseRepair.refersToItself("The writer’s purpose is to warn us.", ContentLanguage.english))
    }

    @Test
    fun wordCountOfLanguagesWithoutSpaces() {
        assertEquals(3, "The small fox".wordCount)
        assertEquals(0, " ... ".wordCount)
        assertTrue("日本語の文章を数えます。".wordCount > 1)
        assertEquals(4, "Hola, ¿cómo estás hoy?".wordCount)
    }
}
