package com.victorarana.leo.features.theme

import androidx.annotation.FontRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.victorarana.leo.R

/** The three Source Serif 4 files in `res/font`. There is no bold, like on iOS. */
enum class LeoWeight(
    @param:FontRes val fontRes: Int,
    val fontWeight: FontWeight,
    val fontStyle: FontStyle,
) {
    Regular(R.font.source_serif_4_regular, FontWeight.Normal, FontStyle.Normal),
    Italic(R.font.source_serif_4_italic, FontWeight.Normal, FontStyle.Italic),
    Semibold(R.font.source_serif_4_semibold, FontWeight.SemiBold, FontStyle.Normal),
}

val SourceSerif4: FontFamily =
    FontFamily(LeoWeight.entries.map { Font(it.fontRes, it.fontWeight, it.fontStyle) })

/**
 * A text role from the type table in section 3.2 of the redesign plan: the mock's size, weight and line height.
 *
 * Sizes and line heights are in sp, so they follow the user's font scale. iOS scales each role with its own
 * `relativeTo` text style; Android has one font scale, so that parameter is dropped (see phase-0 `changes.md`, D2).
 */
data class LeoTextStyle(
    val size: Float,
    val weight: LeoWeight = LeoWeight.Regular,
    /** In sp at the default font scale. `null` keeps the font's own line height. */
    val lineHeight: Float? = null,
    /** Letter spacing in em. */
    val tracking: Float = 0f,
    /** `false` keeps the font at [size] dp at every font scale. */
    val scalesWithFontSize: Boolean = true,
) {
    companion object {
        /** Headlines: 48 on Welcome, 40 on Loading and Error, 38 and 34 in onboarding. */
        fun display(size: Float): LeoTextStyle =
            LeoTextStyle(
                size = size,
                weight = LeoWeight.Semibold,
                lineHeight = size * (if (size >= DISPLAY_LARGE_THRESHOLD) 1.04f else 1.08f),
                tracking = if (size >= DISPLAY_LARGE_THRESHOLD) -0.025f else -0.02f,
            )

        private const val DISPLAY_LARGE_THRESHOLD = 48f

        val score = LeoTextStyle(size = 64f, weight = LeoWeight.Semibold, lineHeight = 64f, tracking = -0.03f)
        val title = LeoTextStyle(size = 30f, weight = LeoWeight.Semibold, lineHeight = 33f, tracking = -0.02f)
        val question = LeoTextStyle(size = 20f, weight = LeoWeight.Semibold, lineHeight = 26f)
        val reviewQuestion = LeoTextStyle(size = 19f, weight = LeoWeight.Semibold, lineHeight = 24.7f)
        val verdict = LeoTextStyle(size = 20f, weight = LeoWeight.Semibold)
        val ageLabel = LeoTextStyle(size = 22f, weight = LeoWeight.Semibold, lineHeight = 26.4f)
        val wordmark = LeoTextStyle(size = 20f, weight = LeoWeight.Semibold)

        /** The launch screen's "Leo". Fixed, like the launch image it continues. */
        val launchWordmark = LeoTextStyle(size = 48f, weight = LeoWeight.Semibold, scalesWithFontSize = false)
        val bodyLarge = LeoTextStyle(size = 17f, lineHeight = 27f)
        val resultMessage = LeoTextStyle(size = 19f, lineHeight = 29f)
        val body = LeoTextStyle(size = 16f, lineHeight = 24f)
        val bodySmall = LeoTextStyle(size = 15f, lineHeight = 22f)
        val caption = LeoTextStyle(size = 14f)
        val note = LeoTextStyle(size = 13f)
        val explanation = LeoTextStyle(size = 18f, weight = LeoWeight.Italic, lineHeight = 28f)
        val reviewExplanation = LeoTextStyle(size = 17f, weight = LeoWeight.Italic, lineHeight = 26f)
        val answer = LeoTextStyle(size = 17f, lineHeight = 24f)
        val answerMarker = LeoTextStyle(size = 15f, weight = LeoWeight.Semibold)
        val kicker = LeoTextStyle(size = 12f, tracking = 0.08f)
    }
}

/**
 * The Compose [TextStyle] of a role. iOS draws the text through an `OverhangingTextRenderer` because iOS 26 clips
 * glyphs to the line box. Compose never clips them, so the renderer isn't ported: `Trim.None` with centered
 * alignment splits a tight line height evenly above and below the glyphs. Font padding is off by default since
 * Compose 1.5, so the line height is exactly what the table says (D3).
 */
@Composable
fun LeoTextStyle.toTextStyle(): TextStyle {
    val density = LocalDensity.current
    val fontSize = if (scalesWithFontSize) size.sp else with(density) { size.dp.toSp() }
    val lineHeightUnit: TextUnit =
        when {
            lineHeight == null -> TextUnit.Unspecified
            scalesWithFontSize -> lineHeight.sp
            else -> with(density) { lineHeight.dp.toSp() }
        }
    return TextStyle(
        fontFamily = SourceSerif4,
        fontWeight = weight.fontWeight,
        fontStyle = weight.fontStyle,
        fontSize = fontSize,
        lineHeight = lineHeightUnit,
        letterSpacing = tracking.em,
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    )
}
