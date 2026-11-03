package com.victorarana.leo.snapshots

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import com.victorarana.leo.domain.AgeGroup
import com.victorarana.leo.domain.DefaultTopics
import com.victorarana.leo.domain.ReaderPreferences
import com.victorarana.leo.domain.TopicReviewOutcome
import com.victorarana.leo.domain.readerPreferencesFixture
import com.victorarana.leo.features.preferences.MinimumTopicsWarning
import com.victorarana.leo.features.preferences.PreferencesEditorViewModel
import com.victorarana.leo.features.preferences.TopicsEditor
import com.victorarana.leo.features.theme.leoReadableWidth
import com.victorarana.leo.support.SNAPSHOT_DEVICE_QUALIFIERS
import com.victorarana.leo.support.StubTopicReviewRepository
import com.victorarana.leo.support.assertViewSnapshot
import com.victorarana.leo.support.preferencesEditorFixture
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Port of `TopicsEditorSnapshotTests`, plus 200% font scale. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = SNAPSHOT_DEVICE_QUALIFIERS)
class TopicsEditorSnapshotTests {
    @get:Rule
    val composeRule = createComposeRule()

    @Composable
    private fun Editor(viewModel: PreferencesEditorViewModel) {
        Column(Modifier.verticalScroll(rememberScrollState()).leoReadableWidth()) {
            MinimumTopicsWarning(viewModel)
            TopicsEditor(viewModel)
        }
    }

    private fun snapshot(
        name: String,
        viewModel: PreferencesEditorViewModel,
        fontScale: Float = 1f,
        heightDp: Int = 844,
    ) = composeRule.assertViewSnapshot(this::class, name, fontScale = fontScale, heightDp = heightDp) {
        Editor(viewModel)
    }

    private val customizedPreferences
        get() =
            readerPreferencesFixture(
                AgeGroup.NINE,
                disabled = mapOf(AgeGroup.NINE to setOf("volcanoes", "pirates-treasure")),
                custom = mapOf(AgeGroup.NINE to listOf("Chess", "Origami")),
            )

    /** Every suggested topic in [AgeGroup.SIX] turned off except the last 2, plus one of the reader's own. */
    private val minimumPreferences
        get() =
            readerPreferencesFixture(
                AgeGroup.SIX,
                disabled =
                    mapOf(
                        AgeGroup.SIX to
                            DefaultTopics
                                .topics(AgeGroup.SIX)
                                .map { it.id }
                                .dropLast(2)
                                .toSet(),
                    ),
                custom = mapOf(AgeGroup.SIX to listOf("Chess")),
            )

    @Test
    fun defaults() = snapshot("9-11-defaults", preferencesEditorFixture(ReaderPreferences(AgeGroup.NINE)))

    /** Turned-off chips, and the reader's own topics with their remove marks. */
    @Test
    fun customized() = snapshot("9-11-customized", preferencesEditorFixture(customizedPreferences))

    @Test
    fun customizedAt200Percent() =
        snapshot(
            "9-11-customized-fontScale2",
            preferencesEditorFixture(customizedPreferences),
            fontScale = 2f,
            heightDp = 1800,
        )

    /** The reader's own topic can't be removed, and after a refused tap the warning shows. */
    @Test
    fun atMinimum() {
        val viewModel = preferencesEditorFixture(minimumPreferences)
        viewModel.setEnabled(false, DefaultTopics.topics(AgeGroup.SIX).last())
        assertTrue(viewModel.uiState.value.isAtMinimum)
        assertTrue(viewModel.uiState.value.isShowingMinimumWarning)
        snapshot("6-8-minimum", viewModel)
    }

    /** After a rejected review, the reason shows under the field and the topic stays in it. */
    @Test
    fun withMessage() {
        val reviews =
            StubTopicReviewRepository(
                listOf(Result.success(TopicReviewOutcome.Rejected("Pick something you'd enjoy reading about."))),
            )
        val viewModel = preferencesEditorFixture(ReaderPreferences(AgeGroup.NINE), reviews)
        viewModel.onNewTopicChange("Taxes")
        viewModel.add()
        composeRule.waitForIdle()
        assertTrue(viewModel.uiState.value.message != null)
        snapshot("9-11-message", viewModel)
    }

    /** A pending review shows the progress indicator and disables the field. */
    @Test
    fun reviewing() {
        val viewModel = preferencesEditorFixture(ReaderPreferences(AgeGroup.NINE))
        viewModel.onNewTopicChange("Chess")
        viewModel.add()
        assertTrue(viewModel.uiState.value.isReviewing)
        snapshot("9-11-reviewing", viewModel)
    }
}
