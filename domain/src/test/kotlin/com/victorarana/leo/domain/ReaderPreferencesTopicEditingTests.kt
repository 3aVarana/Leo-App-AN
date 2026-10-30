package com.victorarana.leo.domain

import com.victorarana.leo.domain.ReaderPreferences.NewTopicCheck
import com.victorarana.leo.domain.ReaderPreferences.TopicMatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The topic editing rules on `ReaderPreferences`. Suggested topic names are compared in English,
 * through [TopicNames.English].
 */
class ReaderPreferencesTopicEditingTests {
    private val names = TopicNames.English
    private val sixIds = DefaultTopics.topics(AgeGroup.SIX).map { it.id }
    private val dinosaurs = DefaultTopics.topics(AgeGroup.SIX).first { it.id == "dinosaurs" }

    /** `.SIX` with every suggested topic off except the last [enabled], plus [custom] topics. */
    private fun six(
        enabled: Int,
        custom: List<String> = emptyList(),
    ): ReaderPreferences =
        readerPreferencesFixture(
            AgeGroup.SIX,
            disabled = mapOf(AgeGroup.SIX to sixIds.dropLast(enabled).toSet()),
            custom = mapOf(AgeGroup.SIX to custom),
        )

    private fun twentyCustomTopics(): List<String> = (1..ReaderPreferences.MAXIMUM_CUSTOM_TOPICS).map { "Topic $it" }

    @Test
    fun enabledTopicCount() {
        val preferences =
            readerPreferencesFixture(
                AgeGroup.SIX,
                disabled = mapOf(AgeGroup.SIX to setOf("dinosaurs", "beach"), AgeGroup.NINE to setOf("volcanoes")),
                custom = mapOf(AgeGroup.SIX to listOf("Chess"), AgeGroup.NINE to listOf("Origami", "Jazz")),
            )
        assertEquals(sixIds.size - 2 + 1, preferences.enabledTopicCount(AgeGroup.SIX))
        assertEquals(DefaultTopics.topics(AgeGroup.NINE).size - 1 + 2, preferences.enabledTopicCount(AgeGroup.NINE))
        assertEquals(DefaultTopics.topics(AgeGroup.ADULT).size, preferences.enabledTopicCount(AgeGroup.ADULT))
    }

    @Test
    fun isAtMinimum() {
        assertTrue(six(enabled = 3).isAtMinimum(AgeGroup.SIX))
        assertFalse(six(enabled = 4).isAtMinimum(AgeGroup.SIX))
        assertTrue(six(enabled = 2, custom = listOf("Chess")).isAtMinimum(AgeGroup.SIX))
        assertFalse(six(enabled = 2, custom = listOf("Chess", "Origami")).isAtMinimum(AgeGroup.SIX))
        assertFalse(six(enabled = 3).isAtMinimum(AgeGroup.NINE))
    }

    @Test
    fun matchIgnoresCaseAndAccents() {
        val preferences = readerPreferencesFixture(AgeGroup.SIX, custom = mapOf(AgeGroup.SIX to listOf("Música")))
        assertEquals(TopicMatch.Enabled, preferences.match("DINOSAURS", AgeGroup.SIX, names))
        assertEquals(TopicMatch.Enabled, preferences.match("dinosaurs", AgeGroup.SIX, names))
        assertEquals(TopicMatch.Enabled, preferences.match("musica", AgeGroup.SIX, names))
        assertEquals(TopicMatch.Enabled, preferences.match("MÚSICA", AgeGroup.SIX, names))
        assertNull(preferences.match("Rockets", AgeGroup.SIX, names))
        // Other groups have their own lists.
        assertNull(preferences.match("Dinosaurs", AgeGroup.NINE, names))
        assertNull(preferences.match("Música", AgeGroup.NINE, names))
    }

    @Test
    fun matchReportsDisabledSuggestedTopic() {
        val preferences = readerPreferencesFixture(AgeGroup.SIX, disabled = mapOf(AgeGroup.SIX to setOf("dinosaurs")))
        assertEquals(TopicMatch.DisabledDefault("dinosaurs"), preferences.match("dinosaurs", AgeGroup.SIX, names))
        assertEquals(TopicMatch.Enabled, preferences.match("The beach", AgeGroup.SIX, names))
    }

    /** Matching uses the names it is given, so a Spanish reader types Spanish. */
    @Test
    fun matchUsesTheGivenNames() {
        val spanish = TopicNames { topic, _ -> if (topic.id == "dinosaurs") "Dinosaurios" else topic.englishName }
        val preferences = ReaderPreferences(AgeGroup.SIX)
        assertEquals(TopicMatch.Enabled, preferences.match("dinosaurios", AgeGroup.SIX, spanish))
        assertNull(preferences.match("dinosaurs", AgeGroup.SIX, spanish))
    }

    @Test
    fun checkOrder() {
        // Length before duplicates.
        val shortCustom = readerPreferencesFixture(AgeGroup.SIX, custom = mapOf(AgeGroup.SIX to listOf("A")))
        assertEquals(NewTopicCheck.InvalidLength, shortCustom.check("A", AgeGroup.SIX, names))

        // Duplicates before the custom topic limit.
        val full = readerPreferencesFixture(AgeGroup.SIX, custom = mapOf(AgeGroup.SIX to twentyCustomTopics()))
        assertEquals(NewTopicCheck.AlreadyOnList, full.check("Dinosaurs", AgeGroup.SIX, names))
        assertEquals(NewTopicCheck.AlreadyOnList, full.check("topic 7", AgeGroup.SIX, names))

        // Re-enabling a suggested topic before the custom topic limit.
        val fullWithDisabled = full.copy(disabledDefaultTopics = mapOf(AgeGroup.SIX to setOf("dinosaurs")))
        assertEquals(
            NewTopicCheck.ReEnablesSuggested("dinosaurs"),
            fullWithDisabled.check("dinosaurs", AgeGroup.SIX, names),
        )
        assertEquals(NewTopicCheck.TooManyCustomTopics, fullWithDisabled.check("Rockets", AgeGroup.SIX, names))
    }

    @Test
    fun checkLengthBoundaries() {
        val preferences = ReaderPreferences(AgeGroup.SIX)
        assertEquals(NewTopicCheck.InvalidLength, preferences.check("a", AgeGroup.SIX, names))
        assertEquals(NewTopicCheck.InvalidLength, preferences.check("a".repeat(61), AgeGroup.SIX, names))
        assertEquals(NewTopicCheck.NeedsReview, preferences.check("ab", AgeGroup.SIX, names))
        assertEquals(NewTopicCheck.NeedsReview, preferences.check("a".repeat(60), AgeGroup.SIX, names))
        assertEquals(2..60, ReaderPreferences.TOPIC_NAME_LENGTH)
    }

    /** The length counts characters the reader sees, not UTF-16 units. */
    @Test
    fun checkLengthCountsCharacters() {
        val preferences = ReaderPreferences(AgeGroup.SIX)
        assertEquals(NewTopicCheck.InvalidLength, preferences.check("😀", AgeGroup.SIX, names))
        assertEquals(NewTopicCheck.NeedsReview, preferences.check("😀😀", AgeGroup.SIX, names))
        assertEquals(NewTopicCheck.NeedsReview, preferences.check("😀".repeat(60), AgeGroup.SIX, names))
    }

    @Test
    fun checkTooManyCustomTopics() {
        val full = readerPreferencesFixture(AgeGroup.SIX, custom = mapOf(AgeGroup.SIX to twentyCustomTopics()))
        assertEquals(NewTopicCheck.TooManyCustomTopics, full.check("Rockets", AgeGroup.SIX, names))
        assertEquals(NewTopicCheck.NeedsReview, full.check("Rockets", AgeGroup.NINE, names))

        val almostFull =
            readerPreferencesFixture(
                AgeGroup.SIX,
                custom = mapOf(AgeGroup.SIX to twentyCustomTopics().dropLast(1)),
            )
        assertEquals(NewTopicCheck.NeedsReview, almostFull.check("Rockets", AgeGroup.SIX, names))
    }

    @Test
    fun insertTopicAppendsCustom() {
        val preferences = readerPreferencesFixture(AgeGroup.SIX, custom = mapOf(AgeGroup.SIX to listOf("Chess")))
        val inserted = preferences.insertTopic("Origami", AgeGroup.SIX, names)
        assertNotNull(inserted)
        assertEquals(listOf("Chess", "Origami"), inserted!!.customTopics[AgeGroup.SIX]?.map { it.name })
        assertNull(inserted.customTopics[AgeGroup.NINE])
    }

    @Test
    fun insertTopicReEnablesSuggested() {
        val preferences = readerPreferencesFixture(AgeGroup.SIX, disabled = mapOf(AgeGroup.SIX to setOf("dinosaurs")))
        val inserted = preferences.insertTopic("dinosaurs", AgeGroup.SIX, names)
        assertNotNull(inserted)
        assertTrue(inserted!!.isEnabled(dinosaurs, AgeGroup.SIX))
        assertTrue(inserted.customTopics.isEmpty())
    }

    @Test
    fun insertTopicRejectsEnabledDuplicate() {
        val preferences = readerPreferencesFixture(AgeGroup.SIX, custom = mapOf(AgeGroup.SIX to listOf("Chess")))
        assertNull(preferences.insertTopic("DINOSAURS", AgeGroup.SIX, names))
        assertNull(preferences.insertTopic("chess", AgeGroup.SIX, names))
    }

    @Test
    fun resetSuggestedTopicsOnlyAffectsGroup() {
        val preferences =
            readerPreferencesFixture(
                AgeGroup.SIX,
                disabled = mapOf(AgeGroup.SIX to setOf("dinosaurs", "beach"), AgeGroup.NINE to setOf("volcanoes")),
            )
        val reset = preferences.resetSuggestedTopics(AgeGroup.SIX)
        assertEquals(mapOf(AgeGroup.NINE to setOf("volcanoes")), reset.disabledDefaultTopics)
    }

    @Test
    fun removeCustomTopicById() {
        val preferences =
            readerPreferencesFixture(
                AgeGroup.SIX,
                custom = mapOf(AgeGroup.SIX to listOf("Chess", "Origami", "Jazz"), AgeGroup.NINE to listOf("Chess")),
            )
        val chess = preferences.customTopics.getValue(AgeGroup.SIX).first()
        val removed = preferences.removeCustomTopic(chess.id, AgeGroup.SIX)
        assertEquals(listOf("Origami", "Jazz"), removed.customTopics[AgeGroup.SIX]?.map { it.name })
        assertEquals(listOf("Chess"), removed.customTopics[AgeGroup.NINE]?.map { it.name })
    }

    @Test
    fun removeCustomTopicFromOtherGroupDoesNothing() {
        val preferences =
            readerPreferencesFixture(
                AgeGroup.SIX,
                custom = mapOf(AgeGroup.SIX to listOf("Chess"), AgeGroup.NINE to listOf("Jazz")),
            )
        val jazz = preferences.customTopics.getValue(AgeGroup.NINE).first()
        val removed = preferences.removeCustomTopic(jazz.id, AgeGroup.SIX)
        assertEquals(listOf("Chess"), removed.customTopics[AgeGroup.SIX]?.map { it.name })
        assertEquals(listOf("Jazz"), removed.customTopics[AgeGroup.NINE]?.map { it.name })
        assertEquals(preferences, removed)
    }

    @Test
    fun removingTheLastIdLeavesAnEmptySetInPlace() {
        val preferences = readerPreferencesFixture(AgeGroup.SIX, disabled = mapOf(AgeGroup.SIX to setOf("dinosaurs")))
        val enabled = preferences.setEnabled(true, dinosaurs, AgeGroup.SIX)
        assertEquals(mapOf(AgeGroup.SIX to emptySet<String>()), enabled.disabledDefaultTopics)
    }

    @Test
    fun enabledSuggestedTopicCount() {
        val preferences =
            readerPreferencesFixture(
                AgeGroup.NINE,
                disabled = mapOf(AgeGroup.NINE to setOf("volcanoes", "recycling")),
                custom = mapOf(AgeGroup.NINE to listOf("Chess")),
            )
        val suggested = DefaultTopics.topics(AgeGroup.NINE).size
        assertEquals(suggested - 2, preferences.enabledSuggestedTopicCount(AgeGroup.NINE))
        assertEquals(suggested - 1, preferences.enabledTopicCount(AgeGroup.NINE))
    }

    @Test
    fun setEnabled() {
        val fresh = ReaderPreferences(AgeGroup.SIX)
        val off = fresh.setEnabled(false, dinosaurs, AgeGroup.SIX)
        assertFalse(off.isEnabled(dinosaurs, AgeGroup.SIX))
        assertTrue(off.isEnabled(dinosaurs, AgeGroup.NINE))

        val on = off.setEnabled(true, dinosaurs, AgeGroup.SIX)
        assertTrue(on.isEnabled(dinosaurs, AgeGroup.SIX))

        // Turning on a topic in a group with no entry changes nothing.
        assertEquals(fresh, fresh.setEnabled(true, dinosaurs, AgeGroup.SIX))
    }
}
