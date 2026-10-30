package com.victorarana.leo.data.genai

import android.icu.util.ULocale
import com.victorarana.leo.domain.AgeGroup
import com.victorarana.leo.domain.ComprehensionSkill
import com.victorarana.leo.domain.CustomTopic
import com.victorarana.leo.domain.RoundTopic
import com.victorarana.leo.domain.TopicKind
import com.victorarana.leo.domain.topic
import com.victorarana.leo.support.FakeLanguageModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ExerciseGeneratorPromptTests {
    private val spanish = ContentLanguage(ULocale("es_ES"))

    private fun generator(
        group: AgeGroup,
        language: ContentLanguage = ContentLanguage.english,
    ) = ExerciseGenerator(language, group, FakeLanguageModel())

    @Test
    fun wordRangeWithSentences() {
        val cases =
            listOf(
                AgeGroup.SIX to "in 10 to 13 sentences",
                AgeGroup.NINE to "in 10 to 13 sentences",
                AgeGroup.TWELVE to "in 10 to 13 sentences",
                AgeGroup.FIFTEEN to "in 14 to 17 sentences",
                AgeGroup.ADULT to "in 18 to 22 sentences",
            )
        for ((group, sentences) in cases) assertTrue("$group", generator(group).wordRange.contains(sentences))
    }

    @Test
    fun wordRangeBoundsAndMidpoint() {
        for (group in AgeGroup.entries) {
            val range = group.passageWordRange
            val midpoint = (range.first + range.last) / 2
            assertTrue(
                "$group",
                generator(group)
                    .wordRange
                    .contains("between ${range.first} and ${range.last} words long, about $midpoint words"),
            )
        }
    }

    @Test
    fun wordRangeExample() {
        assertTrue(generator(AgeGroup.SIX).wordRange.startsWith("between 50 and 80 words long, about 65 words"))
    }

    @Test
    fun instructions() {
        for (group in AgeGroup.entries) {
            for (language in listOf(ContentLanguage.english, spanish)) {
                val instructions = generator(group, language).instructions
                val label = "$group ${language.locale}"
                assertTrue(label, instructions.startsWith(language.localeInstruction))
                assertTrue(label, instructions.contains("You are an expert reading teacher"))
                assertTrue(
                    label,
                    instructions.contains(
                        "You MUST write the passage, title, question, answers and explanation in ${language.name}.",
                    ),
                )
                assertTrue(label, instructions.contains(group.promptAudience))
                assertTrue(label, instructions.contains(group.styleGuidance))
                assertTrue(label, instructions.contains(language.name))
            }
        }
    }

    @Test
    fun prompt() {
        for (language in listOf(ContentLanguage.english, spanish)) {
            for (skill in ComprehensionSkill.entries) {
                val generator = generator(AgeGroup.NINE, language)
                val prompt = generator.prompt(topic("volcanoes"), skill)
                val label = "${language.locale} $skill"
                assertTrue(label, prompt.contains("volcanoes"))
                assertTrue(label, prompt.contains(language.name))
                assertTrue(label, prompt.contains(generator.wordRange))
                assertTrue(label, prompt.contains("Skill to test: ${skill.promptHint}"))
            }
        }
    }

    @Test
    fun promptStatesTheKindOfPassage() {
        val generator = generator(AgeGroup.NINE)
        val informative =
            generator.prompt(
                RoundTopic(prompt = "volcanoes", name = "Volcanoes"),
                ComprehensionSkill.DETAIL,
            )
        val story =
            generator.prompt(
                RoundTopic(prompt = "a story about a kind robot", name = "A kind robot", kind = TopicKind.STORY),
                ComprehensionSkill.DETAIL,
            )
        assertTrue(informative.contains("informative text that explains real, well-known facts about volcanoes"))
        assertFalse(informative.contains("The passage is a story"))
        assertTrue(story.contains("The passage is a story."))
        assertFalse(story.contains("informative text"))
    }

    @Test
    fun promptLetsTheModelDecideForCustomTopics() {
        val prompt =
            generator(
                AgeGroup.NINE,
            ).prompt(CustomTopic(name = "a fairy tale").roundTopic, ComprehensionSkill.DETAIL)
        assertTrue(prompt.contains("If the topic asks for a story, the passage is a story."))
        assertTrue(prompt.contains("informative text that explains real, well-known facts about a fairy tale"))
    }

    @Test
    fun spanishInstructionsStartWithTheLocale() {
        val instructions = generator(AgeGroup.NINE, spanish).instructions
        assertTrue(instructions.startsWith("The person's locale is es_ES.\nYou are an expert reading teacher"))
    }

    /**
     * Prompt changes show up in review. Re-record when tuning prompts on purpose. The goldens are the iOS
     * files, byte for byte.
     */
    @Test
    fun promptSnapshot() {
        for (group in AgeGroup.entries) {
            val generator = generator(group)
            val text =
                generator.instructions + "\n\n---\n\n" +
                    generator.prompt(topic("volcanoes"), ComprehensionSkill.INFERENCE)
            val name = group.name.lowercase()
            val golden =
                checkNotNull(javaClass.getResource("/golden/ExerciseGeneratorPromptTests/promptSnapshot-$name.txt")) {
                    "Missing golden for $name"
                }.readText()
            assertEquals(name, golden, text)
        }
    }

    /**
     * Android-only: the text the model really receives for an exercise is the prompt plus the JSON format
     * (plan D3). Its golden is ours, not an iOS file; a change here is a prompt change.
     */
    @Test
    fun exerciseFormatSnapshot() {
        val golden =
            checkNotNull(javaClass.getResource("/golden/ExerciseGeneratorPromptTests/exerciseFormat.txt")) {
                "Missing golden exerciseFormat"
            }.readText()
        assertEquals(golden, exerciseRequestPrompt("PROMPT"))
    }
}
