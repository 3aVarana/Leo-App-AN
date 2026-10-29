package com.victorarana.leo.support

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.DarkMode
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.then
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziOptions.CompareOptions
import com.github.takahirom.roborazzi.captureRoboImage
import com.victorarana.leo.features.theme.LeoTheme
import com.victorarana.leo.features.theme.LocalAnimatesPlaceholders
import com.victorarana.leo.features.theme.leoScreenBackground
import org.robolectric.RuntimeEnvironment
import java.util.Locale
import kotlin.reflect.KClass

/** The simulated device, like iOS `iPhone13Pro`: 390x844 points at 3x (xxhdpi is 3x). */
const val SNAPSHOT_DEVICE_QUALIFIERS = "w390dp-h844dp-xxhdpi"

private const val SNAPSHOT_DEVICE_HEIGHT_DP = 844

/** Pixel difference tolerated before a snapshot fails: 1%, like the iOS `perceptualPrecision` of 0.99. */
private const val SNAPSHOT_TOLERANCE = 0.01f

/**
 * Renders [content] in light and dark and compares each with its recorded image
 * (`app/src/test/snapshots/<TestClass>/<name>-<language>.png`, `<name>-dark-<language>.png`).
 * Port of `assertViewSnapshot`: the folder is the test class's name, so the unique file names checked by
 * `SnapshotLayoutTests` also keep the references apart.
 *
 * Dark mode and [fontScale] go through `DeviceConfigurationOverride`, i.e. the same `Configuration` the system
 * would set, so `isSystemInDarkTheme()` and `values-night` resources behave as on a device. Placeholders don't
 * animate, so the image doesn't depend on timing. [heightDp] enlarges the window for specimens taller than the
 * device. [highContrast] stands for the system contrast setting.
 */
fun ComposeContentTestRule.assertViewSnapshot(
    testClass: KClass<*>,
    name: String,
    fontScale: Float = 1f,
    heightDp: Int = SNAPSHOT_DEVICE_HEIGHT_DP,
    highContrast: Boolean = false,
    content: @Composable () -> Unit,
) {
    val folder = requireNotNull(testClass.simpleName) { "Snapshot tests need a named class" }
    val language = Locale.getDefault().language
    // Tall specimens need a taller window; the width stays the iPhone 13 Pro's.
    RuntimeEnvironment.setQualifiers("+h${heightDp}dp")
    var dark by mutableStateOf(false)
    setContent {
        DeviceConfigurationOverride(
            DeviceConfigurationOverride.DarkMode(dark) then DeviceConfigurationOverride.FontScale(fontScale),
        ) {
            CompositionLocalProvider(LocalAnimatesPlaceholders provides false) {
                LeoTheme(highContrast = highContrast) {
                    Box(Modifier.fillMaxSize().leoScreenBackground()) { content() }
                }
            }
        }
    }
    val options =
        RoborazziOptions(compareOptions = CompareOptions(changeThreshold = SNAPSHOT_TOLERANCE))
    for (isDark in listOf(false, true)) {
        dark = isDark
        waitForIdle()
        val suffix = if (isDark) "-dark" else ""
        val path = "src/test/snapshots/$folder/$name$suffix-$language.png"
        onRoot().captureRoboImage(path, roborazziOptions = options)
    }
}
