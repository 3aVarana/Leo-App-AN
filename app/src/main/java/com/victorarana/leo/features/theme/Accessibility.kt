package com.victorarana.leo.features.theme

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import kotlin.math.min

// What iOS gets from the environment (`accessibilityReduceMotion`, `dynamicTypeSize(...cap)`, `@ScaledMetric`),
// written for Android's settings: one animation scale, one font scale.

/** Replaces the system setting, for tests and previews (iOS passes `reduceMotion:` to the intro modifier). */
val LocalReduceMotion = compositionLocalOf<Boolean?> { null }

/**
 * Whether the system asks for no motion. Android has no "Reduce Motion" switch: "Remove animations" in
 * Accessibility settings (and the developer option) set the animator duration scale to 0.
 */
fun isReduceMotion(context: Context): Boolean =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

/** [isReduceMotion], kept up to date while the screen is shown, unless [LocalReduceMotion] overrides it. */
@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    var system by remember(context) { mutableStateOf(isReduceMotion(context)) }
    DisposableEffect(context) {
        val observer =
            object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    system = isReduceMotion(context)
                }
            }
        val uri = Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE)
        context.contentResolver.registerContentObserver(uri, false, observer)
        system = isReduceMotion(context)
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    return LocalReduceMotion.current ?: system
}

/**
 * Stops the font scale of [content] from growing past [max], like `.dynamicTypeSize(...cap)`. Sizes in sp stop
 * growing with it, and so do [scaled] sizes.
 */
@Composable
fun CappedFontScale(
    max: Float,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val capped = Density(density.density, min(density.fontScale, max))
    CompositionLocalProvider(LocalDensity provides capped, content = content)
}

/**
 * `@ScaledMetric(relativeTo:)`: a size that grows by the ratio the iOS text style it is tied to grows, given as
 * that style's default point size ([relativeTo]: footnote 13, subheadline 15, body 17, title2 22, ...), within any
 * enclosing [CappedFontScale]. The size itself isn't passed through `sp`: Android 14's nonlinear font curve
 * barely grows large sp values, which would leave a 36dp dot small next to its 15sp number.
 */
@Composable
fun Dp.scaled(relativeTo: Float): Dp = this * with(LocalDensity.current) { relativeTo.sp.toDp().value / relativeTo }

/** The footnote's `xxxLarge` cap relative to the default size (19 / 13): the dots stop growing here. */
const val DOT_FONT_SCALE_CAP = 1.46f

/** The subheadline's `accessibility1` cap relative to the default size (25 / 15). */
const val HEADER_BUTTON_FONT_SCALE_CAP = 1.67f
