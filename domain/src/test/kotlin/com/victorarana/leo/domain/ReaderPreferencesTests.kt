package com.victorarana.leo.domain

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class ReaderPreferencesTests {
    private val names = TopicNames.English

    private fun decode(json: String): ReaderPreferences = LeoJson.decodeFromString(json)

    private fun golden(name: String): String =
        checkNotNull(javaClass.getResource("/golden/$name")) { "Missing golden $name" }.readText()

    /**
     * Swift writes UUIDs in upper case, Java in lower case (a deliberate Android deviation), and both are
     * read. So the comparison with the iOS golden lowercases every `id` value on both sides.
     */
    private fun JsonElement.withLowercaseIds(): JsonElement =
        when (this) {
            is JsonObject ->
                JsonObject(
                    mapValues { (key, value) ->
                        if (key == "id" && value is JsonPrimitive && value.isString) {
                            JsonPrimitive(value.content.lowercase())
                        } else {
                            value.withLowercaseIds()
                        }
                    },
                )
            is JsonArray -> JsonArray(map { it.withLowercaseIds() })
            else -> this
        }

    @Test
    fun decodesVersion1Data() {
        val preferences = decode("""{"ageGroup":"9-11"}""")
        assertEquals(AgeGroup.NINE, preferences.ageGroup)
        assertTrue(preferences.disabledDefaultTopics.isEmpty())
        assertTrue(preferences.customTopics.isEmpty())
        assertTrue(preferences.isReadingTimerOn)
        assertEquals(6, preferences.exercisesPerRound)
        assertEquals(6, preferences.roundSettings(names).exerciseCount)
    }

    @Test
    fun decodesExercisesPerRound() {
        val preferences = decode("""{"ageGroup":"9-11","exercisesPerRound":9}""")
        assertEquals(9, preferences.exercisesPerRound)
        assertEquals(9, preferences.roundSettings(names).exerciseCount)
    }

    /** Out-of-range stored values are clamped into 3...9, not reset to the default. */
    @Test
    fun clampsExercisesPerRound() {
        val cases = listOf(1 to 3, 0 to 3, -5 to 3, 3 to 3, 9 to 9, 10 to 9, 42 to 9)
        for ((stored, expected) in cases) {
            val preferences = decode("""{"ageGroup":"9-11","exercisesPerRound":$stored}""")
            assertEquals("stored $stored", expected, preferences.exercisesPerRound)
            assertEquals("stored $stored", expected, preferences.roundSettings(names).exerciseCount)
        }
    }

    @Test
    fun decodesReadingTimerOff() {
        assertFalse(decode("""{"ageGroup":"9-11","isReadingTimerOn":false}""").isReadingTimerOn)
    }

    @Test
    fun roundTripsExercisesPerRound() {
        val preferences = ReaderPreferences(AgeGroup.TWELVE).copy(exercisesPerRound = 4)
        val decoded = decode(LeoJson.encodeToString(preferences))
        assertEquals(4, decoded.exercisesPerRound)
        assertEquals(preferences, decoded)
    }

    @Test
    fun unknownAgeGroupThrows() {
        assertThrows(SerializationException::class.java) { decode("""{"ageGroup":"99"}""") }
    }

    @Test
    fun roundTrips() {
        val preferences =
            readerPreferencesFixture(
                ageGroup = AgeGroup.TWELVE,
                disabled =
                    mapOf(
                        AgeGroup.TWELVE to setOf("mythology", "inventions"),
                        AgeGroup.SIX to setOf("dinosaurs"),
                    ),
                custom = mapOf(AgeGroup.TWELVE to listOf("Chess", "Origami"), AgeGroup.ADULT to listOf("Jazz")),
            )
        val decoded = decode(LeoJson.encodeToString(preferences))
        assertEquals(preferences, decoded)
        assertEquals(
            preferences.customTopics[AgeGroup.TWELVE]?.map { it.id },
            decoded.customTopics[AgeGroup.TWELVE]?.map { it.id },
        )
    }

    /**
     * Any change to the saved format must show up in review. One disabled id per group, to keep the
     * golden free of set-ordering questions.
     */
    @Test
    fun savedFormat() {
        fun uuid(n: Int) = UUID.fromString("00000000-0000-0000-0000-00000000000$n")
        val preferences =
            ReaderPreferences(
                ageGroup = AgeGroup.NINE,
                disabledDefaultTopics =
                    mapOf(
                        AgeGroup.NINE to setOf("volcanoes"),
                        AgeGroup.TWELVE to setOf("mythology"),
                    ),
                customTopics =
                    mapOf(
                        AgeGroup.NINE to listOf(CustomTopic(uuid(1), "Chess"), CustomTopic(uuid(2), "Origami")),
                        AgeGroup.ADULT to listOf(CustomTopic(uuid(3), "Jazz")),
                    ),
            )
        val expected = LeoJson.parseToJsonElement(golden("savedFormat.1.json"))
        val actual = LeoJson.encodeToJsonElement(preferences)
        assertEquals(expected.withLowercaseIds(), actual.withLowercaseIds())
    }

    /** The golden's ids are upper case in iOS output when they contain letters; reading must accept both. */
    @Test
    fun decodesUpperCaseUuidsWrittenByIos() {
        val id = "ABCDEF00-0000-0000-0000-000000000001"
        val json = """{"ageGroup":"9-11","customTopics":{"9-11":[{"id":"$id","name":"Chess"}]}}"""
        val topic = decode(json).customTopics.getValue(AgeGroup.NINE).single()
        assertEquals(UUID.fromString("abcdef00-0000-0000-0000-000000000001"), topic.id)
        val written = (LeoJson.encodeToJsonElement(topic) as JsonObject)["id"]?.jsonPrimitive?.contentOrNull
        assertEquals("abcdef00-0000-0000-0000-000000000001", written)
    }

    @Test
    fun isEnabled() {
        val topic = DefaultTopics.topics(AgeGroup.NINE).first()
        val preferences = ReaderPreferences(AgeGroup.NINE)
        assertTrue(preferences.isEnabled(topic, AgeGroup.NINE))

        val disabled = preferences.copy(disabledDefaultTopics = mapOf(AgeGroup.NINE to setOf(topic.id)))
        assertFalse(disabled.isEnabled(topic, AgeGroup.NINE))
        assertTrue(disabled.isEnabled(topic, AgeGroup.TWELVE))
    }

    @Test
    fun enabledTopicsKeepsOrder() {
        val preferences =
            readerPreferencesFixture(
                AgeGroup.NINE,
                custom =
                    mapOf(
                        AgeGroup.NINE to listOf("Chess", "Origami"),
                    ),
            )
        val suggested = DefaultTopics.topics(AgeGroup.NINE).map { it.prompt }
        assertEquals(
            suggested + listOf("Chess", "Origami"),
            preferences.enabledTopics(AgeGroup.NINE, names).map { it.prompt },
        )
    }

    /** Suggested topics show their localized name; the reader's own show what they typed. */
    @Test
    fun enabledTopicsNames() {
        val preferences = readerPreferencesFixture(AgeGroup.NINE, custom = mapOf(AgeGroup.NINE to listOf("Chess")))
        val topics = preferences.enabledTopics(AgeGroup.NINE, names)
        val mystery = topics.first { it.prompt == "a short story about a mystery at school" }
        assertEquals("A mystery at school", mystery.name)
        assertEquals(RoundTopic(prompt = "Chess", name = "Chess", kind = TopicKind.OPEN), topics.last())
    }

    @Test
    fun enabledTopicsUseTheGivenNames() {
        val shouting = TopicNames { topic, _ -> topic.englishName.uppercase() }
        val topics = ReaderPreferences(AgeGroup.NINE).enabledTopics(AgeGroup.NINE, shouting)
        assertEquals("VOLCANOES", topics.first { it.prompt == "volcanoes" }.name)
    }

    @Test
    fun enabledTopicsExcludesDisabled() {
        val preferences =
            readerPreferencesFixture(
                AgeGroup.NINE,
                disabled = mapOf(AgeGroup.NINE to setOf("volcanoes", "recycling")),
            )
        val expected = DefaultTopics.topics(AgeGroup.NINE).filter { it.id !in setOf("volcanoes", "recycling") }
        assertEquals(expected.map { it.prompt }, preferences.enabledTopics(AgeGroup.NINE, names).map { it.prompt })
    }

    @Test
    fun enabledTopicsExcludesOtherGroupsCustomTopics() {
        val preferences = readerPreferencesFixture(AgeGroup.NINE, custom = mapOf(AgeGroup.TWELVE to listOf("Chess")))
        assertFalse(preferences.enabledTopics(AgeGroup.NINE, names).map { it.prompt }.contains("Chess"))
        assertEquals("Chess", preferences.enabledTopics(AgeGroup.TWELVE, names).last().prompt)
    }

    @Test
    fun enabledTopicsIgnoresUnknownDisabledIds() {
        val preferences =
            readerPreferencesFixture(
                AgeGroup.NINE,
                disabled =
                    mapOf(
                        AgeGroup.NINE to setOf("no-such-topic"),
                    ),
            )
        assertEquals(
            DefaultTopics.topics(AgeGroup.NINE).map { it.prompt },
            preferences.enabledTopics(AgeGroup.NINE, names).map { it.prompt },
        )
    }

    /** Only possible with stale data, since the editor keeps a minimum enabled. */
    @Test
    fun enabledTopicsTopsUpFromNone() {
        val all = DefaultTopics.topics(AgeGroup.SIX)
        val preferences =
            readerPreferencesFixture(
                AgeGroup.SIX,
                disabled =
                    mapOf(
                        AgeGroup.SIX to all.map { it.id }.toSet(),
                    ),
            )
        assertEquals(all.take(3).map { it.prompt }, preferences.enabledTopics(AgeGroup.SIX, names).map { it.prompt })
    }

    /** Only possible with stale data, since the editor keeps a minimum enabled. */
    @Test
    fun enabledTopicsTopsUpBelowMinimum() {
        val all = DefaultTopics.topics(AgeGroup.SIX)
        val preferences =
            readerPreferencesFixture(
                AgeGroup.SIX,
                disabled = mapOf(AgeGroup.SIX to all.drop(1).map { it.id }.toSet()),
                custom = mapOf(AgeGroup.SIX to listOf("Chess")),
            )
        val expected = listOf(all[0].prompt, "Chess", all[1].prompt)
        assertEquals(expected, preferences.enabledTopics(AgeGroup.SIX, names).map { it.prompt })
    }

    @Test
    fun enabledTopicsKeepsMinimum() {
        val all = DefaultTopics.topics(AgeGroup.SIX)
        val preferences =
            readerPreferencesFixture(
                AgeGroup.SIX,
                disabled = mapOf(AgeGroup.SIX to all.map { it.id }.toSet()),
                custom = mapOf(AgeGroup.SIX to listOf("Chess", "Origami", "Knots")),
            )
        assertEquals(
            listOf("Chess", "Origami", "Knots"),
            preferences.enabledTopics(AgeGroup.SIX, names).map { it.prompt },
        )
    }

    /** `QuizViewModel.configure` relies on equal preferences giving equal settings to skip work. */
    @Test
    fun roundSettings() {
        val preferences = readerPreferencesFixture(AgeGroup.TWELVE, custom = mapOf(AgeGroup.TWELVE to listOf("Chess")))
        assertEquals(AgeGroup.TWELVE, preferences.roundSettings(names).ageGroup)
        assertEquals(preferences.enabledTopics(AgeGroup.TWELVE, names), preferences.roundSettings(names).topics)
        assertEquals(preferences.roundSettings(names), preferences.roundSettings(names))

        assertEquals(preferences.roundSettings(names), preferences.copy().roundSettings(names))
        val copy = preferences.copy(disabledDefaultTopics = mapOf(AgeGroup.TWELVE to setOf("mythology")))
        assertNotEquals(preferences.roundSettings(names), copy.roundSettings(names))
    }

    @Test
    fun roundSettingsFollowExercisesPerRound() {
        val preferences = readerPreferencesFixture(AgeGroup.TWELVE)
        assertEquals(6, preferences.roundSettings(names).exerciseCount)

        val copy = preferences.copy(exercisesPerRound = 8)
        assertEquals(8, copy.roundSettings(names).exerciseCount)
        assertNotEquals(preferences.roundSettings(names), copy.roundSettings(names))
        assertEquals(preferences.roundSettings(names).topics, copy.roundSettings(names).topics)
    }

    /** A stored value outside the range is clamped again when the settings are built. */
    @Test
    fun roundSettingsClampAnUnclampedCount() {
        assertEquals(9, ReaderPreferences(AgeGroup.SIX, exercisesPerRound = 50).roundSettings(names).exerciseCount)
        assertEquals(3, ReaderPreferences(AgeGroup.SIX, exercisesPerRound = 0).roundSettings(names).exerciseCount)
    }

    @Test
    fun limits() {
        assertEquals(3, ReaderPreferences.MINIMUM_ENABLED_TOPICS)
        assertEquals(20, ReaderPreferences.MAXIMUM_CUSTOM_TOPICS)
        assertEquals(3..9, RoundSettings.EXERCISE_COUNT_RANGE)
        assertEquals(6, RoundSettings.DEFAULT_EXERCISE_COUNT)
    }
}
