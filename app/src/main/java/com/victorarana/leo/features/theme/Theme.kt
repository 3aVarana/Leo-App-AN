package com.victorarana.leo.features.theme

import android.app.UiModeManager
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// The Broadsheet look of docs/Leo-Redesign-Plan.md, section 3: the color tokens, the Source Serif 4 text styles
// (LeoTextStyle.kt) and the screen layout helpers (Layout.kt). Views use these instead of Material defaults.

// MARK: - Colors

/**
 * The color sets of `Assets.xcassets/Colors`, one property per iOS `Color.leo...` token.
 * Hex values are copied from each colorset's `Contents.json` (sRGB).
 */
@Immutable
@Suppress("LongParameterList")
data class LeoColors(
    val background: Color,
    val surface: Color,
    val ink: Color,
    val divider: Color,
    val accent: Color,
    val accentPressed: Color,
    /** Ghost button labels: `accent700` in light mode, where `accent` is too light for small text. */
    val accentLink: Color,
    val accent100: Color,
    val accent700: Color,
    val accent800: Color,
    val accent900: Color,
    val magenta: Color,
    val magenta100: Color,
    val magenta600: Color,
    val magenta700: Color,
    val magenta900: Color,
    val neutral300: Color,
    val neutral600: Color,
    val neutral700: Color,
) {
    /** Body text that's a step quieter than [ink]. */
    val inkSoft: Color = ink.copy(alpha = INK_SOFT_ALPHA)

    /** Kickers and captions. */
    val inkMuted: Color = ink.copy(alpha = INK_MUTED_ALPHA)

    /** The colors that change in high contrast: [accent] and [divider], as in the iOS colorsets. */
    fun highContrast(dark: Boolean): LeoColors =
        copy(
            divider = divider.copy(alpha = HIGH_CONTRAST_DIVIDER_ALPHA),
            accent = if (dark) accent else HIGH_CONTRAST_LIGHT_ACCENT,
        )

    private companion object {
        const val INK_SOFT_ALPHA = 0.78f
        const val INK_MUTED_ALPHA = 0.7f
        const val HIGH_CONTRAST_DIVIDER_ALPHA = 0.45f
        val HIGH_CONTRAST_LIGHT_ACCENT = Color(0xFF006786)
    }
}

val LightLeoColors =
    LeoColors(
        background = Color(0xFFF3F2F2),
        surface = Color(0xFFEAE9E9),
        ink = Color(0xFF201E1D),
        divider = Color(0xFF201E1D).copy(alpha = 0.16f),
        accent = Color(0xFF0088B0),
        accentPressed = Color(0xFF006786),
        accentLink = Color(0xFF006786),
        accent100 = Color(0xFFE9F8FF),
        accent700 = Color(0xFF006786),
        accent800 = Color(0xFF004961),
        accent900 = Color(0xFF0A303E),
        magenta = Color(0xFFD6006C),
        magenta100 = Color(0xFFFFF1F4),
        magenta600 = Color(0xFFD82071),
        magenta700 = Color(0xFFAA0B56),
        magenta900 = Color(0xFF4B1528),
        neutral300 = Color(0xFFD7D3D3),
        neutral600 = Color(0xFF7D7979),
        neutral700 = Color(0xFF605D5D),
    )

val DarkLeoColors =
    LeoColors(
        background = Color(0xFF1C1B1A),
        surface = Color(0xFF2A2828),
        ink = Color(0xFFEFEDED),
        divider = Color(0xFFEFEDED).copy(alpha = 0.18f),
        accent = Color(0xFF38A6CF),
        accentPressed = Color(0xFF1186AC),
        accentLink = Color(0xFF38A6CF),
        accent100 = Color(0xFF0A303E),
        accent700 = Color(0xFF62C5EE),
        accent800 = Color(0xFFCBEEFF),
        accent900 = Color(0xFFE9F8FF),
        magenta = Color(0xFFFF458E),
        magenta100 = Color(0xFF4B1528),
        magenta600 = Color(0xFFFF90B1),
        magenta700 = Color(0xFFFF90B1),
        magenta900 = Color(0xFFFFF1F4),
        neutral300 = Color(0xFF3D3A3A),
        neutral600 = Color(0xFFBAB6B6),
        neutral700 = Color(0xFFD7D3D3),
    )

val HighContrastLightLeoColors: LeoColors = LightLeoColors.highContrast(dark = false)
val HighContrastDarkLeoColors: LeoColors = DarkLeoColors.highContrast(dark = true)

val LocalLeoColors = staticCompositionLocalOf { LightLeoColors }

/** Whether placeholders pulse. Off in snapshot tests, so the image doesn't depend on timing. */
val LocalAnimatesPlaceholders = staticCompositionLocalOf { true }

/** Entry point for tokens inside a composable: `LeoTheme.colors.ink`. */
object LeoTheme {
    val colors: LeoColors
        @Composable
        @ReadOnlyComposable
        get() = LocalLeoColors.current
}

/** The token set for an appearance. Pure, so every combination is unit tested (`LeoColorsTests`). */
internal fun leoColors(
    darkTheme: Boolean,
    highContrast: Boolean,
): LeoColors =
    when {
        darkTheme && highContrast -> HighContrastDarkLeoColors
        darkTheme -> DarkLeoColors
        highContrast -> HighContrastLightLeoColors
        else -> LightLeoColors
    }

/** Android's "medium" contrast (0.5) and above map to the iOS "Increase Contrast" colors. */
private const val HIGH_CONTRAST_THRESHOLD = 0.5f

/**
 * Whether the system contrast setting asks for high-contrast colors, updated live like the iOS trait.
 * `UiModeManager.getContrast()` exists from API 34; below that there is no system setting, so standard colors.
 */
@Composable
private fun rememberHighContrast(): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        rememberSystemContrast() >= HIGH_CONTRAST_THRESHOLD
    } else {
        false
    }

@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
@Composable
private fun rememberSystemContrast(): Float {
    val context = LocalContext.current
    val uiModeManager = remember(context) { context.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager }
    var contrast by remember(uiModeManager) { mutableFloatStateOf(uiModeManager.contrast) }
    DisposableEffect(uiModeManager) {
        val listener = UiModeManager.ContrastChangeListener { contrast = it }
        uiModeManager.addContrastChangeListener(context.mainExecutor, listener)
        onDispose { uiModeManager.removeContrastChangeListener(listener) }
    }
    return contrast
}

/**
 * Provides the Broadsheet tokens and a [MaterialTheme] mapped onto them, so a stray Material default
 * (a ripple, a text field) stays on-brand. No dynamic color: Leo looks the same on every phone.
 */
@Composable
fun LeoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    highContrast: Boolean = rememberHighContrast(),
    content: @Composable () -> Unit,
) {
    val colors = leoColors(darkTheme, highContrast)
    CompositionLocalProvider(LocalLeoColors provides colors) {
        MaterialTheme(
            colorScheme = colors.toMaterialColorScheme(darkTheme),
            typography = LeoMaterialTypography,
            content = content,
        )
    }
}

/** Material's default roles in Source Serif 4, so a stray Material text isn't Roboto. Sizes stay Material's. */
private val LeoMaterialTypography: Typography =
    with(Typography()) {
        copy(
            displayLarge = displayLarge.copy(fontFamily = SourceSerif4),
            displayMedium = displayMedium.copy(fontFamily = SourceSerif4),
            displaySmall = displaySmall.copy(fontFamily = SourceSerif4),
            headlineLarge = headlineLarge.copy(fontFamily = SourceSerif4),
            headlineMedium = headlineMedium.copy(fontFamily = SourceSerif4),
            headlineSmall = headlineSmall.copy(fontFamily = SourceSerif4),
            titleLarge = titleLarge.copy(fontFamily = SourceSerif4),
            titleMedium = titleMedium.copy(fontFamily = SourceSerif4),
            titleSmall = titleSmall.copy(fontFamily = SourceSerif4),
            bodyLarge = bodyLarge.copy(fontFamily = SourceSerif4),
            bodyMedium = bodyMedium.copy(fontFamily = SourceSerif4),
            bodySmall = bodySmall.copy(fontFamily = SourceSerif4),
            labelLarge = labelLarge.copy(fontFamily = SourceSerif4),
            labelMedium = labelMedium.copy(fontFamily = SourceSerif4),
            labelSmall = labelSmall.copy(fontFamily = SourceSerif4),
        )
    }

private fun LeoColors.toMaterialColorScheme(dark: Boolean): ColorScheme {
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = accent,
        onPrimary = background,
        primaryContainer = accent100,
        onPrimaryContainer = accent900,
        secondary = magenta,
        onSecondary = background,
        secondaryContainer = magenta100,
        onSecondaryContainer = magenta900,
        background = background,
        onBackground = ink,
        surface = background,
        onSurface = ink,
        surfaceVariant = surface,
        onSurfaceVariant = inkSoft,
        surfaceContainerLowest = background,
        surfaceContainerLow = background,
        surfaceContainer = surface,
        surfaceContainerHigh = surface,
        surfaceContainerHighest = surface,
        outline = neutral600,
        outlineVariant = divider,
        error = magenta,
        onError = background,
        errorContainer = magenta100,
        onErrorContainer = magenta900,
    )
}
