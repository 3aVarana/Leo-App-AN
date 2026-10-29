package com.victorarana.leo.features.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertEquals
import org.junit.Test

/** The token tables are copied from each colorset `Contents.json` in `Assets.xcassets/Colors` (sRGB, 0xAARRGGBB). */
class LeoColorsTests {
    private data class Token(
        val name: String,
        val argb: Long,
    )

    private fun LeoColors.table(): Map<String, Int> =
        mapOf(
            "background" to background,
            "surface" to surface,
            "ink" to ink,
            "divider" to divider,
            "accent" to accent,
            "accentPressed" to accentPressed,
            "accentLink" to accentLink,
            "accent100" to accent100,
            "accent700" to accent700,
            "accent800" to accent800,
            "accent900" to accent900,
            "magenta" to magenta,
            "magenta100" to magenta100,
            "magenta600" to magenta600,
            "magenta700" to magenta700,
            "magenta900" to magenta900,
            "neutral300" to neutral300,
            "neutral600" to neutral600,
            "neutral700" to neutral700,
        ).mapValues { it.value.toArgb() }

    private fun assertTable(
        expected: List<Token>,
        actual: LeoColors,
        label: String,
    ) {
        val table = actual.table()
        assertEquals("$label has all tokens", expected.map { it.name }.toSet(), table.keys)
        expected.forEach { assertEquals("$label ${it.name}", it.argb.toInt(), table.getValue(it.name)) }
    }

    @Test
    fun lightMatchesTheColorsets() = assertTable(LIGHT, leoColors(darkTheme = false, highContrast = false), "light")

    @Test
    fun darkMatchesTheColorsets() = assertTable(DARK, leoColors(darkTheme = true, highContrast = false), "dark")

    @Test
    fun highContrastLightOnlyChangesAccentAndDivider() =
        assertHighContrast(LIGHT, leoColors(darkTheme = false, highContrast = true), "high contrast light", 0xFF006786)

    @Test
    fun highContrastDarkOnlyChangesDivider() =
        assertHighContrast(DARK, leoColors(darkTheme = true, highContrast = true), "high contrast dark", 0xFF38A6CF)

    private fun assertHighContrast(
        standard: List<Token>,
        actual: LeoColors,
        label: String,
        accent: Long,
    ) {
        val dividerRgb = standard.first { it.name == "divider" }.argb and 0x00FFFFFF
        val expected =
            standard.map {
                when (it.name) {
                    "accent" -> Token("accent", accent)
                    "divider" -> Token("divider", 0x73000000L or dividerRgb)
                    else -> it
                }
            }
        assertTable(expected, actual, label)
    }

    @Test
    fun derivedInkTokensUseTheIosOpacities() {
        val ink = LightLeoColors.ink
        assertEquals(ink.copy(alpha = 0.78f), LightLeoColors.inkSoft)
        assertEquals(ink.copy(alpha = 0.7f), LightLeoColors.inkMuted)
        assertEquals(DarkLeoColors.ink.copy(alpha = 0.78f), DarkLeoColors.inkSoft)
        assertEquals(Color(0xFFEFEDED).copy(alpha = 0.7f), DarkLeoColors.inkMuted)
    }

    @Test
    fun highContrastKeepsTheDerivedInkTokens() {
        val colors = leoColors(darkTheme = true, highContrast = true)
        assertEquals(colors.ink.copy(alpha = 0.78f), colors.inkSoft)
    }

    private companion object {
        val LIGHT =
            listOf(
                Token("accent", 0xFF0088B0),
                Token("accent100", 0xFFE9F8FF),
                Token("accent700", 0xFF006786),
                Token("accent800", 0xFF004961),
                Token("accent900", 0xFF0A303E),
                Token("accentLink", 0xFF006786),
                Token("accentPressed", 0xFF006786),
                Token("background", 0xFFF3F2F2),
                Token("divider", 0x29201E1D),
                Token("ink", 0xFF201E1D),
                Token("magenta", 0xFFD6006C),
                Token("magenta100", 0xFFFFF1F4),
                Token("magenta600", 0xFFD82071),
                Token("magenta700", 0xFFAA0B56),
                Token("magenta900", 0xFF4B1528),
                Token("neutral300", 0xFFD7D3D3),
                Token("neutral600", 0xFF7D7979),
                Token("neutral700", 0xFF605D5D),
                Token("surface", 0xFFEAE9E9),
            )
        val DARK =
            listOf(
                Token("accent", 0xFF38A6CF),
                Token("accent100", 0xFF0A303E),
                Token("accent700", 0xFF62C5EE),
                Token("accent800", 0xFFCBEEFF),
                Token("accent900", 0xFFE9F8FF),
                Token("accentLink", 0xFF38A6CF),
                Token("accentPressed", 0xFF1186AC),
                Token("background", 0xFF1C1B1A),
                Token("divider", 0x2EEFEDED),
                Token("ink", 0xFFEFEDED),
                Token("magenta", 0xFFFF458E),
                Token("magenta100", 0xFF4B1528),
                Token("magenta600", 0xFFFF90B1),
                Token("magenta700", 0xFFFF90B1),
                Token("magenta900", 0xFFFFF1F4),
                Token("neutral300", 0xFF3D3A3A),
                Token("neutral600", 0xFFBAB6B6),
                Token("neutral700", 0xFFD7D3D3),
                Token("surface", 0xFF2A2828),
            )
    }
}
