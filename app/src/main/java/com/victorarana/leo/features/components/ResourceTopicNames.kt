package com.victorarana.leo.features.components

import android.content.Context
import androidx.annotation.StringRes
import com.victorarana.leo.R
import com.victorarana.leo.domain.AgeGroup
import com.victorarana.leo.domain.DefaultTopic
import com.victorarana.leo.domain.TopicNames

/**
 * The names of the suggested topics in the reader's language, from string resources. Passed to the
 * domain wherever the Swift code called `String(localized:)` on a topic's name.
 */
class ResourceTopicNames(
    context: Context,
) : TopicNames {
    // The application context, so a ViewModel holding this can't leak an Activity.
    private val context: Context = context.applicationContext

    override fun name(
        topic: DefaultTopic,
        group: AgeGroup,
    ): String = context.getString(nameRes(topic))

    companion object {
        /** The resource of a suggested topic's name, keyed by its English name (as in `Localizable.xcstrings`). */
        @StringRes
        fun nameRes(topic: DefaultTopic): Int =
            requireNotNull(nameResources[topic.englishName]) { "No string resource for topic ${topic.englishName}" }

        private val nameResources: Map<String, Int> =
            mapOf(
                "Pets and farm animals" to R.string.topic_pets_and_farm_animals,
                "Dinosaurs" to R.string.topic_dinosaurs,
                "The seasons" to R.string.topic_the_seasons,
                "A day at school" to R.string.topic_a_day_at_school,
                "Friendly dragons and fairies" to R.string.topic_friendly_dragons_and_fairies,
                "The beach" to R.string.topic_the_beach,
                "Insects and bugs" to R.string.topic_insects_and_bugs,
                "Helping at home" to R.string.topic_helping_at_home,
                "Birthday parties" to R.string.topic_birthday_parties,
                "A lost toy finds its way home" to R.string.topic_a_lost_toy_finds_its_way_home,
                "Trains and trucks" to R.string.topic_trains_and_trucks,
                "The moon and stars" to R.string.topic_the_moon_and_stars,
                "Baking cookies" to R.string.topic_baking_cookies,
                "Playground games" to R.string.topic_playground_games,
                "A kind robot" to R.string.topic_a_kind_robot,
                "Wild animals" to R.string.topic_wild_animals,
                "Volcanoes" to R.string.topic_volcanoes,
                "The solar system" to R.string.topic_the_solar_system,
                "Pirates and treasure" to R.string.topic_pirates_and_treasure,
                "Inventors" to R.string.topic_inventors,
                "Rainforests" to R.string.topic_rainforests,
                "Dinosaurs and fossils" to R.string.topic_dinosaurs_and_fossils,
                "Sports heroes" to R.string.topic_sports_heroes,
                "A mystery at school" to R.string.topic_a_mystery_at_school,
                "The ocean floor" to R.string.topic_the_ocean_floor,
                "Ancient Egypt" to R.string.topic_ancient_egypt,
                "How machines work" to R.string.topic_how_machines_work,
                "Video games" to R.string.topic_video_games,
                "Recycling" to R.string.topic_recycling,
                "A magical adventure" to R.string.topic_a_magical_adventure,
                "Space exploration" to R.string.topic_space_exploration,
                "Climate and weather" to R.string.topic_climate_and_weather,
                "Ancient civilizations" to R.string.topic_ancient_civilizations,
                "The human body" to R.string.topic_the_human_body,
                "Famous explorers" to R.string.topic_famous_explorers,
                "Inventions" to R.string.topic_inventions,
                "Animal behavior" to R.string.topic_animal_behavior,
                "Mythology" to R.string.topic_mythology,
                "A story about friendship" to R.string.topic_a_story_about_friendship,
                "Coding and robots" to R.string.topic_coding_and_robots,
                "Music history" to R.string.topic_music_history,
                "Survival stories" to R.string.topic_survival_stories,
                "Natural disasters" to R.string.topic_natural_disasters,
                "World cultures" to R.string.topic_world_cultures,
                "Mysteries of history" to R.string.topic_mysteries_of_history,
                "Ocean life" to R.string.topic_ocean_life,
                "Inventions that changed the world" to R.string.topic_inventions_that_changed_the_world,
                "Music and its history" to R.string.topic_music_and_its_history,
                "Sports science" to R.string.topic_sports_science,
                "Social media and friendship" to R.string.topic_social_media_and_friendship,
                "Volcanoes and earthquakes" to R.string.topic_volcanoes_and_earthquakes,
                "The human brain" to R.string.topic_the_human_brain,
                "Video game design" to R.string.topic_video_game_design,
                "Food and culture around the world" to R.string.topic_food_and_culture_around_the_world,
                "Renewable energy" to R.string.topic_renewable_energy,
                "Street art and graffiti" to R.string.topic_street_art_and_graffiti,
                "Robots and artificial intelligence" to R.string.topic_robots_and_artificial_intelligence,
                "The science of sleep" to R.string.topic_the_science_of_sleep,
                "Photography" to R.string.topic_photography,
                "A teenager facing a challenge" to R.string.topic_a_teenager_facing_a_challenge,
                "Volunteering and community" to R.string.topic_volunteering_and_community,
                "Architecture of famous buildings" to R.string.topic_architecture_of_famous_buildings,
                "History's turning points" to R.string.topic_history_s_turning_points,
                "Psychology and habits" to R.string.topic_psychology_and_habits,
                "Economics in daily life" to R.string.topic_economics_in_daily_life,
                "Science breakthroughs" to R.string.topic_science_breakthroughs,
                "Philosophy and ethics" to R.string.topic_philosophy_and_ethics,
                "Architecture" to R.string.topic_architecture,
                "World literature" to R.string.topic_world_literature,
                "Nutrition and health" to R.string.topic_nutrition_and_health,
                "Personal finance" to R.string.topic_personal_finance,
                "Technology and society" to R.string.topic_technology_and_society,
                "Short literary fiction" to R.string.topic_short_literary_fiction,
                "Travel and geography" to R.string.topic_travel_and_geography,
                "The history of language" to R.string.topic_the_history_of_language,
                "Art movements" to R.string.topic_art_movements,
            )
    }
}
