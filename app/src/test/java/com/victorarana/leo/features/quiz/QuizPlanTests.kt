package com.victorarana.leo.features.quiz

import com.victorarana.leo.domain.ComprehensionSkill
import com.victorarana.leo.domain.RoundSettings
import com.victorarana.leo.domain.RoundTopic
import com.victorarana.leo.domain.topic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Port of `QuizPlanTests`: checks `QuizViewModel.makePlan` over seeds 0 until 100 for each topic count and round size.
 * With exactly 2 topics the plan repeats a main topic back to back (exercises 1 and 2), but the app always has at
 * least 3, so counts below 3 aren't tested. The checks are properties, so `Random(seed)` stands in for `SplitMix64`.
 */
class QuizPlanTests {
    private val seeds = 0 until 100

    private fun topics(count: Int): List<RoundTopic> = List(count) { topic("topic $it") }

    private fun plan(
        topics: List<RoundTopic>,
        count: Int = RoundSettings.DEFAULT_EXERCISE_COUNT,
        seed: Int,
    ) = QuizViewModel.makePlan(topics, count, Random(seed))

    @Test
    fun plan() {
        for (topicCount in 3..20) {
            for (count in RoundSettings.EXERCISE_COUNT_RANGE) {
                for (seed in seeds) checkPlan(topicCount, count, seed)
            }
        }
    }

    private fun checkPlan(
        topicCount: Int,
        count: Int,
        seed: Int,
    ) {
        val topics = topics(topicCount)
        val label = "topics $topicCount, count $count, seed $seed"
        val plan = plan(topics, count, seed)
        val main = plan.map { it.topics[0] }
        val skills = plan.map { it.skill }

        assertEquals(label, count, plan.size)
        if (count <= ComprehensionSkill.entries.size) assertEquals(label, count, skills.toSet().size)
        if (count >= ComprehensionSkill.entries.size) {
            assertEquals(label, ComprehensionSkill.entries.toSet(), skills.toSet())
        }
        if (topicCount >= count) {
            assertEquals(label, main.size, main.toSet().size)
        } else {
            for ((previous, next) in main.zipWithNext()) assertTrue("$label: $main", previous != next)
        }
        for (item in plan) {
            assertEquals(label, 3, item.topics.size)
            assertEquals("$label: ${item.topics}", item.topics.size, item.topics.toSet().size)
            assertTrue(label, topics.containsAll(item.topics))
        }
    }

    @Test
    fun sameSeedSamePlan() {
        for (topicCount in listOf(3, 6, 20)) {
            for (count in listOf(3, 6, 9)) {
                val topics = topics(topicCount)
                for (seed in seeds) {
                    assertEquals(
                        "topics $topicCount, count $count, seed $seed",
                        plan(topics, count, seed),
                        plan(topics, count, seed),
                    )
                }
            }
        }
    }

    // MARK: - Replacement topics

    private fun replacement(
        failed: List<RoundTopic>,
        topics: List<RoundTopic>,
        seed: Int,
    ) = QuizViewModel.replacementTopics(failed, topics, Random(seed))

    @Test
    fun replacementTopicsPreferUntried() {
        for (topicCount in listOf(4, 5, 6, 20)) {
            val topics = topics(topicCount)
            val failed = topics.take(3)
            val untriedCount = minOf(topicCount - 3, 3)
            for (seed in seeds) {
                val label = "topics $topicCount, seed $seed"
                val replacement = replacement(failed, topics, seed)
                assertEquals(label, 3, replacement.size)
                assertEquals(label, 3, replacement.toSet().size)
                assertTrue(label, topics.containsAll(replacement))
                assertTrue("$label: $replacement", replacement.take(untriedCount).none { it in failed })
                assertTrue("$label: $replacement", replacement.drop(untriedCount).all { it in failed })
            }
        }
    }

    /** With no other topics, the failed ones come back, in another order for some seeds. */
    @Test
    fun replacementTopicsWithoutOthers() {
        val topics = topics(3)
        val orders = mutableSetOf<List<RoundTopic>>()
        for (seed in seeds) {
            val replacement = replacement(topics, topics, seed)
            assertEquals("seed $seed", topics.toSet(), replacement.toSet())
            assertEquals("seed $seed", 3, replacement.size)
            orders += replacement
        }
        assertTrue(orders.size > 1)
    }

    @Test
    fun replacementTopicsSameSeedSameResult() {
        val topics = topics(10)
        val failed = topics.take(3)
        for (seed in seeds) {
            assertEquals("seed $seed", replacement(failed, topics, seed), replacement(failed, topics, seed))
        }
    }
}
