package com.victorarana.leo.domain

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.util.UUID

/**
 * A suggested topic for an age group.
 *
 * @property id Stable key, persisted when the reader turns the topic off. Never change it.
 * @property englishName The name in English, which is also the key of the localized name in the app
 * module (see [TopicNames]).
 * @property prompt The English phrase used in prompts.
 * @property kind Set by hand: a story topic's prompt doesn't always say "story" ("friendly dragons and fairies").
 */
data class DefaultTopic(
    val id: String,
    val englishName: String,
    val prompt: String,
    val kind: TopicKind = TopicKind.INFORMATIVE,
) {
    /** The topic as a round uses it, named in the reader's language by [names]. */
    fun roundTopic(
        names: TopicNames,
        group: AgeGroup,
    ): RoundTopic = RoundTopic(prompt = prompt, name = names.name(this, group), kind = kind)
}

/**
 * The reader-facing name of a suggested topic, in the reader's language. Passed explicitly, since the
 * domain has no resources. The app module supplies a resource-backed one.
 */
fun interface TopicNames {
    fun name(
        topic: DefaultTopic,
        group: AgeGroup,
    ): String

    companion object {
        /** Names in English, for tests and previews. */
        val English = TopicNames { topic, _ -> topic.englishName }
    }
}

/** Writes a [UUID] as a string and reads it back, accepting upper and lower case. */
object UuidSerializer : KSerializer<UUID> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("java.util.UUID", PrimitiveKind.STRING)

    override fun serialize(
        encoder: Encoder,
        value: UUID,
    ) = encoder.encodeString(value.toString())

    override fun deserialize(decoder: Decoder): UUID {
        val text = decoder.decodeString()
        return try {
            UUID.fromString(text)
        } catch (error: IllegalArgumentException) {
            throw SerializationException("Invalid UUID: $text", error)
        }
    }
}

/**
 * A topic the reader added, after the model reviewed it.
 *
 * @property name The model's cleaned-up phrase, in the language the reader typed it in. Used for display and prompts.
 */
@Serializable
data class CustomTopic(
    @Serializable(with = UuidSerializer::class) val id: UUID = UUID.randomUUID(),
    val name: String,
) {
    /**
     * The reader's phrase is both what the model writes about and what the reader sees.
     * The reader may have typed a story ("a fairy tale") or a subject ("chess"), so the model decides.
     */
    val roundTopic: RoundTopic
        get() = RoundTopic(prompt = name, name = name, kind = TopicKind.OPEN)
}

/** Suggested topics per age group. One is picked per exercise so a round stays varied. */
object DefaultTopics {
    fun topics(group: AgeGroup): List<DefaultTopic> =
        when (group) {
            AgeGroup.SIX -> sixToEight
            AgeGroup.NINE -> nineToEleven
            AgeGroup.TWELVE -> twelveToFourteen
            AgeGroup.FIFTEEN -> fifteenToSeventeen
            AgeGroup.ADULT -> adult
        }

    private val sixToEight =
        listOf(
            DefaultTopic("pets-farm-animals", "Pets and farm animals", "pets and farm animals"),
            DefaultTopic("dinosaurs", "Dinosaurs", "dinosaurs"),
            DefaultTopic("seasons", "The seasons", "the seasons of the year"),
            DefaultTopic("day-at-school", "A day at school", "a day at school"),
            DefaultTopic(
                "dragons-fairies",
                "Friendly dragons and fairies",
                "friendly dragons and fairies",
                TopicKind.STORY,
            ),
            DefaultTopic("beach", "The beach", "a day at the beach"),
            DefaultTopic("insects-bugs", "Insects and bugs", "insects and bugs"),
            DefaultTopic("helping-at-home", "Helping at home", "helping at home"),
            DefaultTopic("birthday-parties", "Birthday parties", "birthday parties"),
            DefaultTopic(
                "lost-toy",
                "A lost toy finds its way home",
                "a story about a lost toy that finds its way home",
                TopicKind.STORY,
            ),
            DefaultTopic("trains-trucks", "Trains and trucks", "trains and trucks"),
            DefaultTopic("moon-stars", "The moon and stars", "the moon and stars"),
            DefaultTopic("baking-cookies", "Baking cookies", "baking cookies"),
            DefaultTopic("playground-games", "Playground games", "playground games"),
            DefaultTopic("kind-robot", "A kind robot", "a story about a kind robot", TopicKind.STORY),
        )

    private val nineToEleven =
        listOf(
            DefaultTopic("wild-animals", "Wild animals", "wild animals"),
            DefaultTopic("volcanoes", "Volcanoes", "volcanoes"),
            DefaultTopic("solar-system", "The solar system", "the solar system"),
            DefaultTopic("pirates-treasure", "Pirates and treasure", "pirates and treasure"),
            DefaultTopic("inventors", "Inventors", "famous inventors"),
            DefaultTopic("rainforests", "Rainforests", "rainforests"),
            DefaultTopic("dinosaurs-fossils", "Dinosaurs and fossils", "dinosaurs and fossils"),
            DefaultTopic("sports-heroes", "Sports heroes", "sports heroes"),
            DefaultTopic(
                "school-mystery",
                "A mystery at school",
                "a short story about a mystery at school",
                TopicKind.STORY,
            ),
            DefaultTopic("ocean-floor", "The ocean floor", "the ocean floor"),
            DefaultTopic("ancient-egypt", "Ancient Egypt", "ancient Egypt"),
            DefaultTopic("how-machines-work", "How machines work", "how machines work"),
            DefaultTopic("video-games", "Video games", "video games"),
            DefaultTopic("recycling", "Recycling", "recycling"),
            DefaultTopic(
                "magical-adventure",
                "A magical adventure",
                "a short story about a magical adventure",
                TopicKind.STORY,
            ),
        )

    private val twelveToFourteen =
        listOf(
            DefaultTopic("space-exploration", "Space exploration", "space exploration"),
            DefaultTopic("climate-weather", "Climate and weather", "climate and weather"),
            DefaultTopic("ancient-civilizations", "Ancient civilizations", "ancient civilizations"),
            DefaultTopic("human-body", "The human body", "the human body"),
            DefaultTopic("famous-explorers", "Famous explorers", "famous explorers"),
            DefaultTopic("inventions", "Inventions", "inventions"),
            DefaultTopic("animal-behavior", "Animal behavior", "animal behavior"),
            DefaultTopic("mythology", "Mythology", "mythology"),
            DefaultTopic(
                "friendship-story",
                "A story about friendship",
                "a short story about friendship",
                TopicKind.STORY,
            ),
            DefaultTopic("coding-robots", "Coding and robots", "coding and robots"),
            DefaultTopic("music-history", "Music history", "music history"),
            DefaultTopic("survival-stories", "Survival stories", "a survival story", TopicKind.STORY),
            DefaultTopic("natural-disasters", "Natural disasters", "natural disasters"),
            DefaultTopic("world-cultures", "World cultures", "world cultures"),
            DefaultTopic("history-mysteries", "Mysteries of history", "mysteries of history"),
        )

    private val fifteenToSeventeen =
        listOf(
            DefaultTopic("space-exploration", "Space exploration", "space exploration"),
            DefaultTopic("ocean-life", "Ocean life", "ocean life"),
            DefaultTopic("climate-weather", "Climate and weather", "climate and weather"),
            DefaultTopic("ancient-civilizations", "Ancient civilizations", "ancient civilizations"),
            DefaultTopic(
                "world-changing-inventions",
                "Inventions that changed the world",
                "inventions that changed the world",
            ),
            DefaultTopic("music-history", "Music and its history", "music and its history"),
            DefaultTopic("sports-science", "Sports science", "sports science"),
            DefaultTopic("social-media-friendship", "Social media and friendship", "social media and friendship"),
            DefaultTopic("animal-behavior", "Animal behavior", "animal behavior"),
            DefaultTopic("volcanoes-earthquakes", "Volcanoes and earthquakes", "volcanoes and earthquakes"),
            DefaultTopic("famous-explorers", "Famous explorers", "famous explorers"),
            DefaultTopic("human-brain", "The human brain", "the human brain"),
            DefaultTopic("video-game-design", "Video game design", "video game design"),
            DefaultTopic("food-culture", "Food and culture around the world", "food and culture around the world"),
            DefaultTopic("renewable-energy", "Renewable energy", "renewable energy"),
            DefaultTopic("street-art", "Street art and graffiti", "street art and graffiti"),
            DefaultTopic("robots-ai", "Robots and artificial intelligence", "robots and artificial intelligence"),
            DefaultTopic("history-mysteries", "Mysteries of history", "mysteries of history"),
            DefaultTopic("rainforests", "Rainforests", "rainforests"),
            DefaultTopic("science-of-sleep", "The science of sleep", "the science of sleep"),
            DefaultTopic("photography", "Photography", "photography"),
            DefaultTopic(
                "teen-challenge-story",
                "A teenager facing a challenge",
                "a short fictional story about a teenager facing a challenge",
                TopicKind.STORY,
            ),
            DefaultTopic("volunteering", "Volunteering and community", "volunteering and community"),
            DefaultTopic("mythology", "Mythology", "mythology"),
            DefaultTopic("famous-buildings", "Architecture of famous buildings", "architecture of famous buildings"),
        )

    private val adult =
        listOf(
            DefaultTopic("historic-turning-points", "History's turning points", "turning points in history"),
            DefaultTopic("psychology-habits", "Psychology and habits", "psychology and habits"),
            DefaultTopic("everyday-economics", "Economics in daily life", "economics in daily life"),
            DefaultTopic("science-breakthroughs", "Science breakthroughs", "scientific breakthroughs"),
            DefaultTopic("philosophy-ethics", "Philosophy and ethics", "philosophy and ethics"),
            DefaultTopic("architecture", "Architecture", "architecture"),
            DefaultTopic("world-literature", "World literature", "world literature"),
            DefaultTopic("nutrition-health", "Nutrition and health", "nutrition and health"),
            DefaultTopic("personal-finance", "Personal finance", "personal finance"),
            DefaultTopic("technology-society", "Technology and society", "technology and society"),
            DefaultTopic(
                "literary-fiction",
                "Short literary fiction",
                "a short piece of literary fiction",
                TopicKind.STORY,
            ),
            DefaultTopic("travel-geography", "Travel and geography", "travel and geography"),
            DefaultTopic("history-of-language", "The history of language", "the history of language"),
            DefaultTopic("art-movements", "Art movements", "art movements"),
            DefaultTopic("science-of-sleep", "The science of sleep", "the science of sleep"),
        )
}
