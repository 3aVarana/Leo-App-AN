package com.victorarana.leo.features.theme

import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AccessibilityTests {
    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun setAnimatorScale(scale: Float) {
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, scale)
    }

    @Test
    fun animationsAreOnByDefault() {
        assertFalse(isReduceMotion(context))
    }

    @Test
    fun animatorScaleZeroMeansReduceMotion() {
        setAnimatorScale(0f)
        assertTrue(isReduceMotion(context))
        setAnimatorScale(1f)
        assertFalse(isReduceMotion(context))
        setAnimatorScale(0.5f)
        assertFalse(isReduceMotion(context))
    }

    @Test
    fun rememberReduceMotionFollowsTheSetting() {
        setAnimatorScale(0f)
        var reduce: Boolean? = null
        composeRule.setContent { reduce = rememberReduceMotion() }
        composeRule.waitForIdle()
        assertEquals(true, reduce)
    }

    @Test
    fun theLocalOverridesTheSetting() {
        setAnimatorScale(0f)
        var reduce: Boolean? = null
        composeRule.setContent {
            CompositionLocalProvider(LocalReduceMotion provides false) { reduce = rememberReduceMotion() }
        }
        composeRule.waitForIdle()
        assertEquals(false, reduce)
    }

    @Test
    fun cappedFontScaleStopsGrowingAtTheCap() {
        var below = 0f
        var above = 0f
        composeRule.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(2f)) {
                CappedFontScale(max = 3f) { below = LocalDensity.current.fontScale }
                CappedFontScale(max = DOT_FONT_SCALE_CAP) { above = LocalDensity.current.fontScale }
            }
        }
        composeRule.waitForIdle()
        assertEquals(2f, below, 0.001f)
        assertEquals(DOT_FONT_SCALE_CAP, above, 0.001f)
    }

    @Test
    fun scaledSizesFollowTheFontScaleWithinTheCap() {
        var scaled = 0.dp
        var capped = 0.dp
        composeRule.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(2f)) {
                scaled = 10.dp.scaled(relativeTo = 10f)
                CappedFontScale(max = 1.5f) { capped = 10.dp.scaled(relativeTo = 10f) }
            }
        }
        composeRule.waitForIdle()
        assertEquals(20f, scaled.value, 0.01f)
        assertEquals(15f, capped.value, 0.01f)
    }

    /** `ProgressDotsTests.swift:18`: the large dot at the cap is 36 x 1.46, however big the 36 is. */
    @Test
    fun aLargeSizeGrowsByTheRatioOfItsTextStyle() {
        var diameter = 0.dp
        composeRule.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(2f)) {
                CappedFontScale(max = DOT_FONT_SCALE_CAP) { diameter = 36.dp.scaled(relativeTo = 13f) }
            }
        }
        composeRule.waitForIdle()
        // Android 14's font curve grows the 13sp number a little more than iOS's footnote does; the dot follows it.
        assertEquals(52.6f, diameter.value, 1.5f)
    }
}
