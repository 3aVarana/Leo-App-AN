package com.victorarana.leo.data.genai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Android-only: the Prompt API streams text, so the passage is read out of partial JSON.
 * Robolectric, for `org.json`.
 */
@RunWith(RobolectricTestRunner::class)
class PartialJsonTests {
    @Test
    fun passageIsNullUntilItStarts() {
        assertNull(PartialJson.stringValue("", "passage"))
        assertNull(PartialJson.stringValue("{\"pass", "passage"))
        assertNull(PartialJson.stringValue("{\"passage\": ", "passage"))
    }

    @Test
    fun readsThePassageSoFar() {
        assertEquals("", PartialJson.stringValue("{\"passage\": \"", "passage"))
        assertEquals("The fox", PartialJson.stringValue("{\"passage\": \"The fox", "passage"))
        assertEquals(
            "The fox ran.",
            PartialJson.stringValue("{\"passage\":\"The fox ran.\",\"question\":\"Wh", "passage"),
        )
    }

    @Test
    fun decodesEscapes() {
        assertEquals(
            "Say \"hi\"\nnow\\",
            PartialJson.stringValue("{\"passage\": \"Say \\\"hi\\\"\\nnow\\\\\"}", "passage"),
        )
        assertEquals("café", PartialJson.stringValue("{\"passage\": \"caf\\u00e9\"}", "passage"))
    }

    @Test
    fun leavesOutAnUnfinishedEscape() {
        assertEquals("one ", PartialJson.stringValue("{\"passage\": \"one \\", "passage"))
        assertEquals("one ", PartialJson.stringValue("{\"passage\": \"one \\u00", "passage"))
    }

    @Test
    fun readsACompleteExercise() {
        val json =
            """
            ```json
            {"passage": "The fox ran.", "question": "Who ran?", "correctAnswer": "The fox",
             "incorrectAnswers": ["The owl", "The bee", "The ant"], "explanation": "It says so.", "title": "A Fox"}
            ```
            """.trimIndent()
        val exercise = PartialJson.generatedExercise(json)
        assertNotNull(exercise)
        assertEquals("The fox ran.", exercise!!.passage)
        assertEquals(listOf("The owl", "The bee", "The ant"), exercise.incorrectAnswers)
        assertEquals("A Fox", exercise.title)
    }

    @Test
    fun incompleteOrMalformedJsonGivesNull() {
        assertNull(PartialJson.generatedExercise("{\"passage\": \"The fox ran."))
        assertNull(PartialJson.generatedExercise("not json"))
        assertNull(PartialJson.generatedExercise("{\"passage\": \"x\", \"question\": \"y\"}"))
    }

    @Test
    fun malformedUnicodeEscapeKeepsItsCharacters() {
        assertEquals("a\\uZZZZb", PartialJson.stringValue("{\"passage\": \"a\\uZZZZb\"}", "passage"))
    }

    @Test
    fun appendTreatsChunksAsNewText() {
        var text = ""
        for (chunk in listOf("{\"pass", "age\": \"Hi", " there")) text = PartialJson.append(text, chunk)
        assertEquals("{\"passage\": \"Hi there", text)
        assertEquals("Hi there", PartialJson.passage(text))
    }

    @Test
    fun appendAcceptsChunksThatRepeatEverythingSoFar() {
        var text = ""
        for (chunk in listOf("{\"pass", "{\"passage\": \"Hi", "{\"passage\": \"Hi there")) {
            text = PartialJson.append(text, chunk)
        }
        assertEquals("{\"passage\": \"Hi there", text)
    }

    @Test
    fun extraIncorrectAnswersAreCutToThree() {
        val json =
            "{\"passage\": \"p\", \"question\": \"q\", \"correctAnswer\": \"c\", " +
                "\"incorrectAnswers\": [\"a\", \"b\", \"c2\", \"d\"], \"explanation\": \"e\", \"title\": \"t\"}"
        assertEquals(listOf("a", "b", "c2"), PartialJson.generatedExercise(json)!!.incorrectAnswers)
    }
}
