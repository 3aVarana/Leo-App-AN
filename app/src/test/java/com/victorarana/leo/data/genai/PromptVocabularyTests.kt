package com.victorarana.leo.data.genai

import com.victorarana.leo.domain.AgeGroup
import com.victorarana.leo.domain.ComprehensionSkill
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.BreakIterator
import java.util.Locale

/** The prompt halves of the iOS `AgeGroupTests` and `DefaultTopicsTests` that phase 1 deferred. */
class PromptVocabularyTests {
    @Test
    fun passageSentenceRange() {
        val expected =
            listOf(
                AgeGroup.SIX to 10..13,
                AgeGroup.NINE to 10..13,
                AgeGroup.TWELVE to 10..13,
                AgeGroup.FIFTEEN to 14..17,
                AgeGroup.ADULT to 18..22,
            )
        for ((group, range) in expected) assertEquals("$group", range, group.passageSentenceRange)
    }

    @Test
    fun textsAreNotEmpty() {
        for (group in AgeGroup.entries) {
            assertFalse("$group", group.promptAudience.isEmpty())
            assertFalse("$group", group.styleGuidance.isEmpty())
        }
    }

    @Test
    fun comprehensionSkills() {
        assertEquals(5, ComprehensionSkill.entries.size)
        for (skill in ComprehensionSkill.entries) assertFalse("$skill", skill.promptHint.isEmpty())
    }

    /** What the question asks, what the passage must do, and what the wrong answers are. */
    @Test
    fun promptHintHasThreeParts() {
        for (skill in ComprehensionSkill.entries) {
            val iterator = BreakIterator.getSentenceInstance(Locale.ENGLISH)
            iterator.setText(skill.promptHint)
            var sentences = 0
            while (iterator.next() != BreakIterator.DONE) sentences++
            assertTrue("$skill has $sentences sentences", sentences >= 3)
        }
    }
}
