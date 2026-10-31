package com.victorarana.leo.features.components

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.intl.Locale
import com.victorarana.leo.features.theme.LeoTextStyle
import com.victorarana.leo.features.theme.LeoTheme
import com.victorarana.leo.features.theme.toTextStyle

/** A small uppercase label above a headline or a section. Uppercased in the reader's locale (`textCase`). */
@Composable
fun Kicker(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = LeoTheme.colors.inkMuted,
) {
    val spoken = text
    BasicText(
        text = text.uppercase(Locale.current.platformLocale),
        // Drawn in capitals, read as written: `textCase` is visual only on iOS, and TalkBack voices may spell
        // short all-caps words letter by letter.
        modifier = modifier.clearAndSetSemantics { this.text = AnnotatedString(spoken) },
        style = LeoTextStyle.kicker.toTextStyle().copy(color = color),
    )
}
