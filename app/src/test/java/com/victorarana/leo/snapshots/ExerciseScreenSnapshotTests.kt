package com.victorarana.leo.snapshots

import androidx.compose.ui.test.junit4.createComposeRule
import com.victorarana.leo.domain.AgeGroup
import com.victorarana.leo.domain.Exercise
import com.victorarana.leo.domain.RoundAnswer
import com.victorarana.leo.domain.exerciseFixture
import com.victorarana.leo.features.quiz.ExerciseScreen
import com.victorarana.leo.features.quiz.QuizUiState
import com.victorarana.leo.features.quiz.QuizViewModel
import com.victorarana.leo.support.SNAPSHOT_DEVICE_QUALIFIERS
import com.victorarana.leo.support.assertViewSnapshot
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Port of `ExerciseViewSnapshotTests`. The screen is stateless, so each case builds the `QuizUiState` the
 * ViewModel would be in instead of driving a round.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = SNAPSHOT_DEVICE_QUALIFIERS)
class ExerciseScreenSnapshotTests {
    @get:Rule
    val composeRule = createComposeRule()

    private fun state(
        exercise: Exercise,
        ageGroup: AgeGroup = AgeGroup.NINE,
        index: Int = 0,
        isReading: Boolean = false,
        selected: Int? = null,
    ) = QuizUiState(
        phase = QuizViewModel.Phase.Answering,
        currentIndex = index,
        currentExercise = exercise,
        selectedOption = selected,
        isReading = isReading,
        roundAgeGroup = ageGroup,
        answers =
            if (selected == null) emptyList() else listOf(RoundAnswer(index, exercise, selected)),
    )

    private fun snapshot(
        name: String,
        state: QuizUiState,
        isReadingTimed: Boolean = true,
        fontScale: Float = 1f,
        heightDp: Int = 844,
    ) = composeRule.assertViewSnapshot(this::class, name, fontScale = fontScale, heightDp = heightDp) {
        ExerciseScreen(
            state = state,
            exercise = checkNotNull(state.currentExercise),
            isReadingTimed = isReadingTimed,
            onFinishReading = {},
            onSelect = {},
            onNext = {},
        )
    }

    private val exercise = exerciseFixture()

    /** The passage, with the full reading time left. */
    @Test
    fun reading() = snapshot("reading", state(exercise, isReading = true))

    /** The timer is off in Settings: only the button. */
    @Test
    fun readingUntimed() = snapshot("reading-untimed", state(exercise, isReading = true), isReadingTimed = false)

    /** The largest passage text, for the youngest readers. */
    @Test
    fun youngestReaders() = snapshot("reading-6-8", state(exercise, AgeGroup.SIX, isReading = true), heightDp = 1000)

    @Test
    fun readingLargeFontScale() =
        snapshot("reading-fontScale2", state(exercise, isReading = true), fontScale = 2f, heightDp = 2400)

    /** The question replaced the passage. */
    @Test
    fun beforeAnswering() = snapshot("unanswered", state(exercise))

    @Test
    fun beforeAnsweringLargeFontScale() =
        snapshot("unanswered-fontScale2", state(exercise), fontScale = 2f, heightDp = 2000)

    @Test
    fun correctAnswer() = snapshot("correct", state(exercise, selected = exercise.correctIndex), heightDp = 1250)

    @Test
    fun wrongAnswer() = snapshot("wrong", state(exercise, selected = 0), heightDp = 1250)

    @Test
    fun wrongAnswerWithoutExplanation() =
        snapshot(
            "wrong-no-explanation",
            state(exerciseFixture(explanation = ""), selected = 0),
            heightDp = 1250,
        )

    /** The button reads "See results". */
    @Test
    fun lastExerciseAnswered() =
        snapshot("last-answered", state(exercise, index = 5, selected = exercise.correctIndex), heightDp = 1250)

    /** A wrong pick on the last text: the button reads "See results". */
    @Test
    fun lastExerciseMissed() = snapshot("last-missed", state(exercise, index = 5, selected = 0), heightDp = 1250)
}
