package com.victorarana.leo.snapshots

import androidx.compose.ui.test.junit4.createComposeRule
import com.victorarana.leo.support.SNAPSHOT_DEVICE_QUALIFIERS
import com.victorarana.leo.support.assertViewSnapshot
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = SNAPSHOT_DEVICE_QUALIFIERS)
class ThemeSnapshotTests {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun colors() = composeRule.assertViewSnapshot(this::class, "colors") { ColorSpecimen() }

    @Test
    fun colorsInHighContrast() =
        composeRule.assertViewSnapshot(this::class, "colors-highContrast", highContrast = true) { ColorSpecimen() }

    @Test
    fun headlineStyles() =
        composeRule.assertViewSnapshot(this::class, "headlineStyles", heightDp = TALL) { TextSpecimen(headlineRoles) }

    @Test
    fun bodyStyles() =
        composeRule.assertViewSnapshot(this::class, "bodyStyles", heightDp = SHORT) {
            TextSpecimen(bodyRoles)
        }

    @Test
    fun detailStyles() =
        composeRule.assertViewSnapshot(this::class, "detailStyles", heightDp = SHORT) { TextSpecimen(detailRoles) }

    @Test
    fun headlineStylesAt200Percent() =
        composeRule.assertViewSnapshot(this::class, "headlineStyles-fontScale2", fontScale = 2f, heightDp = TALLER) {
            TextSpecimen(headlineRoles)
        }

    @Test
    fun bodyStylesAt200Percent() =
        composeRule.assertViewSnapshot(this::class, "bodyStyles-fontScale2", fontScale = 2f, heightDp = SHORT) {
            TextSpecimen(bodyRoles)
        }

    @Test
    fun detailStylesAt200Percent() =
        composeRule.assertViewSnapshot(this::class, "detailStyles-fontScale2", fontScale = 2f, heightDp = SHORT) {
            TextSpecimen(detailRoles)
        }

    private companion object {
        const val TALL = 1600
        const val TALLER = 2600
        const val SHORT = 900
    }
}
