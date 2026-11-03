package com.victorarana.leo.features.components

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import com.victorarana.leo.features.theme.LeoTextStyle
import com.victorarana.leo.features.theme.LeoTheme
import com.victorarana.leo.features.theme.rememberResolvedLocale
import com.victorarana.leo.features.theme.toTextStyle

/**
 * A small uppercase label above a headline or a section. Uppercased in the language of the text (`textCase`): a
 * Turkish phone must not dot the I of English.
 */
@Composable
fun Kicker(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = LeoTheme.colors.inkMuted,
) {
    val spoken = text
    BasicText(
        text = text.uppercase(rememberResolvedLocale()),
        // Drawn in capitals, read as written: `textCase` is visual only on iOS, and TalkBack voices may spell
        // short all-caps words letter by letter.
        modifier = modifier.clearAndSetSemantics { this.text = AnnotatedString(spoken) },
        style = LeoTextStyle.kicker.toTextStyle().copy(color = color),
    )
}
