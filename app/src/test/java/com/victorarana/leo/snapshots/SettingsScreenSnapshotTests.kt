package com.victorarana.leo.snapshots

import androidx.compose.ui.test.junit4.createComposeRule
import com.victorarana.leo.domain.AgeGroup
import com.victorarana.leo.domain.ReaderPreferences
import com.victorarana.leo.domain.readerPreferencesFixture
import com.victorarana.leo.features.preferences.SettingsScreen
import com.victorarana.leo.support.SNAPSHOT_DEVICE_QUALIFIERS
import com.victorarana.leo.support.assertViewSnapshot
import com.victorarana.leo.support.preferencesEditorFixture
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Port of `SettingsViewSnapshotTests`. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = SNAPSHOT_DEVICE_QUALIFIERS)
class SettingsScreenSnapshotTests {
    @get:Rule
    val composeRule = createComposeRule()

    private fun snapshot(
        name: String,
        preferences: ReaderPreferences,
        fontScale: Float = 1f,
        heightDp: Int,
    ) = composeRule.assertViewSnapshot(this::class, name, fontScale = fontScale, heightDp = heightDp) {
        SettingsScreen(preferencesEditorFixture(preferences), onCancel = {}, onDone = {})
    }

    @Test
    fun screen() =
        snapshot(
            "12-14",
            readerPreferencesFixture(
                AgeGroup.TWELVE,
                disabled = mapOf(AgeGroup.TWELVE to setOf("mythology")),
                custom = mapOf(AgeGroup.TWELVE to listOf("Chess")),
            ),
            heightDp = 1150,
        )

    /** The fewest suggested topics, and no Reset. */
    @Test
    fun sixToEight() = snapshot("6-8", ReaderPreferences(AgeGroup.SIX), heightDp = 1150)

    /** 25 suggested topics. */
    @Test
    fun fifteenToSeventeen() = snapshot("15-17", ReaderPreferences(AgeGroup.FIFTEEN), heightDp = 1200)

    /** 200% text: the stepper's controls go below its label (the age segments still fit at this width). */
    @Test
    fun largeFontScale() =
        snapshot("12-14-fontScale2", ReaderPreferences(AgeGroup.TWELVE), fontScale = 2f, heightDp = 2700)
}
