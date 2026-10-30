package com.victorarana.leo.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `comprehensionSkills` (`promptHint`), `promptHintHasThreeParts` and `generationErrorDescription` arrive
 * in phase 3 with the prompt vocabulary and `ExerciseGenerationError`.
 */
class DefaultTopicsTests {
    private val idPattern = Regex("[a-z0-9]+(-[a-z0-9]+)*")

    private fun golden(name: String): String =
        checkNotNull(javaClass.getResource("/golden/$name")) { "Missing golden $name" }.readText()

    /** One main topic plus 2 backups per exercise. */
    @Test
    fun enoughTopics() {
        for (group in AgeGroup.entries) {
            val count = DefaultTopics.topics(group).size
            assertTrue("$group", count >= ReaderPreferences.MINIMUM_ENABLED_TOPICS)
        }
    }

    @Test
    fun eightyFiveTopicsInAllGroups() {
        assertEquals(85, AgeGroup.entries.sumOf { DefaultTopics.topics(it).size })
    }

    @Test
    fun idsAreUniqueAndWellFormed() {
        for (group in AgeGroup.entries) {
            val ids = DefaultTopics.topics(group).map { it.id }
            assertEquals("$group", ids.size, ids.toSet().size)
            for (id in ids) assertTrue(id, idPattern.matches(id))
        }
    }

    @Test
    fun promptsAreUniqueAndNotEmpty() {
        for (group in AgeGroup.entries) {
            val prompts = DefaultTopics.topics(group).map { it.prompt }
            assertFalse("$group", prompts.any { it.isBlank() })
            assertEquals("$group", prompts.size, prompts.toSet().size)
        }
    }

    /**
     * Ids are saved when the reader turns a topic off. Renaming or removing one must fail
     * this test; adding a topic means updating the golden on purpose.
     */
    @Test
    fun savedIds() {
        val text =
            AgeGroup.entries.joinToString("\n") { group ->
                (listOf(group.rawValue) + DefaultTopics.topics(group).map { "  ${it.id}" }).joinToString("\n")
            }
        assertEquals(golden("savedIds.1.txt").trimEnd(), text)
    }

    @Test
    fun topicKind() {
        val cases =
            listOf(
                "a story about a kind robot" to TopicKind.STORY,
                "a short story about friendship" to TopicKind.STORY,
                "a short fictional story about a teenager facing a challenge" to TopicKind.STORY,
                "a short piece of literary fiction" to TopicKind.STORY,
                "a survival story" to TopicKind.STORY,
                "friendly dragons and fairies" to TopicKind.STORY,
                "volcanoes" to TopicKind.INFORMATIVE,
                "a day at the beach" to TopicKind.INFORMATIVE,
                "the history of language" to TopicKind.INFORMATIVE,
                "mysteries of history" to TopicKind.INFORMATIVE,
            )
        for ((prompt, expected) in cases) {
            val (group, found) =
                AgeGroup.entries.firstNotNullOf { group ->
                    DefaultTopics.topics(group).firstOrNull { it.prompt == prompt }?.let { group to it }
                }
            assertEquals(prompt, expected, found.kind)
            assertEquals(prompt, expected, found.roundTopic(TopicNames.English, group).kind)
        }
    }

    @Test
    fun everyGroupHasStoriesAndFacts() {
        for (group in AgeGroup.entries) {
            val kinds = DefaultTopics.topics(group).map { it.kind }.toSet()
            assertEquals("$group", setOf(TopicKind.STORY, TopicKind.INFORMATIVE), kinds)
        }
    }

    @Test
    fun customTopicsLetTheModelDecide() {
        assertEquals(TopicKind.OPEN, CustomTopic(name = "Chess").roundTopic.kind)
    }

    @Test
    fun customTopicRoundTrips() {
        val topic = CustomTopic(name = "Chess")
        val decoded = LeoJson.decodeFromString<CustomTopic>(LeoJson.encodeToString(topic))
        assertEquals(topic.id, decoded.id)
        assertEquals("Chess", decoded.name)
    }

    /** The same id can have a different name in two groups. */
    @Test
    fun roundTopicUsesTheGroupsName() {
        val twelve = DefaultTopics.topics(AgeGroup.TWELVE).first { it.id == "music-history" }
        val fifteen = DefaultTopics.topics(AgeGroup.FIFTEEN).first { it.id == "music-history" }
        assertEquals("Music history", twelve.roundTopic(TopicNames.English, AgeGroup.TWELVE).name)
        assertEquals("Music and its history", fifteen.roundTopic(TopicNames.English, AgeGroup.FIFTEEN).name)
        assertNotNull(twelve.prompt)
    }
}
