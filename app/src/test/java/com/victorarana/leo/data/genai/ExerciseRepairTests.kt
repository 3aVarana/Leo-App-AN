package com.victorarana.leo.data.genai

import android.icu.util.ULocale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The rules of docs/Leo-Exercise-Quality-Plan.md, section 2.4, without the model.
 * Parameterized Swift tests are loops here; every assertion names its case.
 * Robolectric, because [ContentLanguage] uses `android.icu`.
 */
@RunWith(RobolectricTestRunner::class)
class ExerciseRepairTests {
    private val english = ContentLanguage.english
    private val spanish = ContentLanguage(ULocale("es_ES"))
    private val portuguese = ContentLanguage(ULocale("pt_BR"))
    private val japanese = ContentLanguage(ULocale("ja_JP"))

    // region Markdown

    @Test
    fun removesMarkdown() {
        val cases =
            listOf(
                "A *space shuttle* is a vehicle." to "A space shuttle is a vehicle.",
                "A **bold** move." to "A bold move.",
                "An _emphasized_ word." to "An emphasized word.",
                "# Volcanoes\nHot rock rises." to "Volcanoes\nHot rock rises.",
                "## Title" to "Title",
                "Type `ls` to list." to "Type ls to list.",
                "* A list item" to "A list item",
            )
        for ((text, expected) in cases) assertEquals(text, expected, ExerciseRepair.removingMarkdown(text))
    }

    @Test
    fun leavesSymbolsInsideWordsAlone() {
        for (text in listOf("Two times three is 2*3.", "A snake_case name.", "Rain fell #1 in the ranking.")) {
            assertEquals(text, text, ExerciseRepair.removingMarkdown(text))
        }
    }

    // endregion

    // region Leading title

    @Test
    fun removesLeadingTitle() {
        val cases =
            listOf(
                "**Busy Little Bugs**\nInsects live all around us." to "Insects live all around us.",
                "**Busy Little Bugs** In a sunny garden, a ladybug rests." to "In a sunny garden, a ladybug rests.",
                "**The Missing Pencil**  Liam found a pencil." to "Liam found a pencil.",
                "**Volcanoes**\nHot rock rises." to "Hot rock rises.",
                "# Volcanoes\n\nHot rock rises." to "Hot rock rises.",
                "Title: Mystery in the Locker\n\nA student found a note." to "A student found a note.",
                "Título: El volcán\nEl suelo tembló." to "El suelo tembló.",
                "Mystery in the Locker\n\nA student found a note." to "A student found a note.",
                "Un día en la playa\nEl sol brilla fuerte sobre el mar." to "El sol brilla fuerte sobre el mar.",
            )
        for ((passage, expected) in cases) assertEquals(passage, expected, ExerciseRepair.removingLeadingTitle(passage))
    }

    @Test
    fun keepsPassagesWithoutATitle() {
        val passages =
            listOf(
                "**Volcanoes** are mountains that erupt.",
                "Insects live all around us. **Bees** make honey.",
                "**Stop!** Mia shouted.",
                "**Mount** Etna erupted in 2021.",
                "**Volcanoes** Hot rock rises.",
                "A plain passage.",
                "The bell rang.\n\nEveryone ran outside.",
                "Mia waited by the door, the rain still falling\nThen the bus came.",
            )
        for (passage in passages) assertEquals(passage, passage, ExerciseRepair.removingLeadingTitle(passage))
    }

    // endregion

    // region Line breaks

    @Test
    fun joinsLinesButKeepsParagraphs() {
        val cases =
            listOf(
                "El sol brilla.\nLas olas son suaves.\nHay niños." to "El sol brilla. Las olas son suaves. Hay niños.",
                "One line.  \n  Two line." to "One line. Two line.",
                "First paragraph.\n\nSecond paragraph." to "First paragraph.\n\nSecond paragraph.",
                "First.\n \n\n  Second.\nStill second." to "First.\n\nSecond. Still second.",
                "No breaks at all." to "No breaks at all.",
            )
        for ((passage, expected) in cases) assertEquals(passage, expected, ExerciseRepair.joiningLines(passage))
    }

    // endregion

    // region Punctuation

    @Test
    fun addsPeriodsWhenMostEndInOne() {
        assertEquals(
            listOf("A cave.", "A tree.", "A field.", "A bridge."),
            ExerciseRepair.matchingPunctuation(listOf("A cave.", "A tree.", "A field.", "A bridge")),
        )
    }

    @Test
    fun addsPeriodsWhenHalfEndInOne() {
        assertEquals(
            listOf("A cave.", "A tree.", "A field.", "A bridge."),
            ExerciseRepair.matchingPunctuation(listOf("A cave.", "A tree.", "A field", "A bridge")),
        )
    }

    @Test
    fun stripsPeriodsWhenFewEndInOne() {
        assertEquals(
            listOf("A cave", "A tree", "A field", "A bridge"),
            ExerciseRepair.matchingPunctuation(listOf("A cave.", "A tree", "A field", "A bridge")),
        )
    }

    @Test
    fun questionsAndExclamationsCountAsTerminal() {
        assertEquals(
            listOf("Why?", "Run!", "Stop.", "Wait."),
            ExerciseRepair.matchingPunctuation(listOf("Why?", "Run!", "Stop.", "Wait")),
        )
    }

    @Test
    fun stripKeepsEllipses() {
        assertEquals(
            listOf("Wait...", "A tree", "A field", "A bridge", "A cave"),
            ExerciseRepair.matchingPunctuation(listOf("Wait...", "A tree.", "A field", "A bridge", "A cave")),
        )
        assertEquals(
            listOf("待って。。。", "森", "海", "山"),
            ExerciseRepair.matchingPunctuation(listOf("待って。。。", "森", "海", "山")),
        )
    }

    @Test
    fun stripKeepsQuestionMarks() {
        assertEquals(
            listOf("Why?", "A tree", "A field", "A bridge"),
            ExerciseRepair.matchingPunctuation(listOf("Why?", "A tree", "A field", "A bridge")),
        )
    }

    @Test
    fun usesTheAnswersOwnFullStop() {
        assertEquals(
            listOf("森へ行った。", "海へ行った。", "山へ行った。", "川へ行った。"),
            ExerciseRepair.matchingPunctuation(listOf("森へ行った。", "海へ行った。", "山へ行った。", "川へ行った")),
        )
    }

    // endregion

    // region Explanation

    @Test
    fun truncatedExplanationBecomesEmpty() {
        for (explanation in listOf("The mention of", "The passage says the fox ran (across the field")) {
            assertEquals(explanation, "", ExerciseRepair.completeExplanation(explanation))
        }
    }

    @Test
    fun completeExplanationIsKept() {
        val explanations =
            listOf(
                "The passage says so.",
                "Is it not clear?",
                "It says “the ground shakes.”",
                "It calls this “a warning”",
                "El texto lo dice.",
                "文章にそう書いてある。",
                "",
            )
        for (explanation in explanations) {
            assertEquals(explanation, explanation, ExerciseRepair.completeExplanation(explanation))
        }
    }

    // endregion

    // region Self-reference

    @Test
    fun rejectsEnglishSelfReference() {
        assertTrue(ExerciseRepair.refersToItself("Rivers run. The author wanted to show change.", english))
        assertTrue(ExerciseRepair.refersToItself("In this passage, we learn about rivers.", english))
        assertTrue(ExerciseRepair.refersToItself("THE MAIN IDEA is simple.", english))
        assertTrue(ExerciseRepair.refersToItself("The writer’s purpose is to warn us.", english))
        // Found by the harness after the review's narrower list let them through.
        assertTrue(ExerciseRepair.refersToItself("The writer shows how the robot helps.", english))
        assertTrue(ExerciseRepair.refersToItself("The writer includes this to show their power.", english))
    }

    @Test
    fun rejectsSpanishAndPortugueseSelfReference() {
        assertTrue(ExerciseRepair.refersToItself("El autor quiso mostrar el cambio.", spanish))
        assertTrue(ExerciseRepair.refersToItself("Este texto habla del agua.", spanish))
        assertTrue(ExerciseRepair.refersToItself("A autora explica o ciclo.", portuguese))
    }

    @Test
    fun matchesWholeWordsOnly() {
        val passages =
            listOf(
                "She lived on Author Street.",
                "The authors of the law met in Rome.",
                "They read the textbook together.",
                "A coauthor joined them.",
            )
        for (passage in passages) assertFalse(passage, ExerciseRepair.refersToItself(passage, english))
    }

    /** Words that are only self-reference with a verb about the passage, from the PR #31 review. */
    @Test
    fun ordinaryUseIsNotSelfReference() {
        val passages =
            listOf(
                "She read the text on her phone.",
                "The passage of time made the old writer sad.",
                "This shows that bees need flowers.",
                "The writer sat at her desk and smiled.",
            )
        for (passage in passages) assertFalse(passage, ExerciseRepair.refersToItself(passage, english))
    }

    @Test
    fun otherLanguagesHaveNoList() {
        assertFalse(ExerciseRepair.refersToItself("The author wanted to show change.", japanese))
        assertFalse(ExerciseRepair.refersToItself("作者は変化を示したかった。", japanese))
    }

    @Test
    fun listIsPerLanguage() {
        assertFalse(ExerciseRepair.refersToItself("The author wanted to show change.", spanish))
    }

    // endregion

    // region Standout correct answer

    private val fourWords = listOf("one two three four", "one two three", "one two")

    @Test
    fun longCorrectAnswerStandsOut() {
        assertTrue(
            ExerciseRepair.correctAnswerStandsOut("one two three four five six seven eight nine", fourWords),
        )
    }

    @Test
    fun slightlyLongerCorrectAnswerIsAccepted() {
        assertFalse(ExerciseRepair.correctAnswerStandsOut("one two three four five six", fourWords))
    }

    /** Twice as long by ratio, but only 2 words longer. */
    @Test
    fun shortAnswersAreNotRejectedByRatioAlone() {
        assertFalse(ExerciseRepair.correctAnswerStandsOut("one two three four", listOf("one two", "three", "four")))
    }

    // endregion
}
