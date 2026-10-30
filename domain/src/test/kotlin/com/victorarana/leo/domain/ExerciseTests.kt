package com.victorarana.leo.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * The tests of `GeneratedExercise` and `ExerciseRepair` (19 of the 20 iOS `ExerciseTests`) arrive in
 * phase 3 with the code they test.
 */
class ExerciseTests {
    @Test
    fun nonRandomInitKeepsValues() {
        val exercise = exerciseFixture(options = listOf("A", "B", "C"), correctIndex = 2)
        assertEquals(listOf("A", "B", "C"), exercise.options)
        assertEquals(2, exercise.correctIndex)
        assertEquals("The Sleeping Mountain", exercise.title)
    }

    @Test
    fun initRejectsOutOfRangeCorrectIndex() {
        assertThrows(IllegalArgumentException::class.java) { exerciseFixture(correctIndex = 4) }
        assertThrows(IllegalArgumentException::class.java) { exerciseFixture(correctIndex = -1) }
    }

    @Test
    fun eachExerciseGetsANewId() {
        assertNotEquals(exerciseFixture().id, exerciseFixture().id)
    }
}
