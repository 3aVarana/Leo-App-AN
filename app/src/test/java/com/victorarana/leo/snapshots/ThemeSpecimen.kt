package com.victorarana.leo.snapshots

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.victorarana.leo.features.theme.LeoColors
import com.victorarana.leo.features.theme.LeoTextStyle
import com.victorarana.leo.features.theme.LeoTheme
import com.victorarana.leo.features.theme.leoReadableWidth
import com.victorarana.leo.features.theme.toTextStyle

/** Test-only: every color token as a swatch. */
@Composable
fun ColorSpecimen() {
    val c = LeoTheme.colors
    Column(
        Modifier.leoReadableWidth().padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for ((name, color) in c.swatches()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(28.dp).background(color).border(1.dp, c.divider))
                Text(name, style = LeoTextStyle.note.toTextStyle(), color = c.ink)
            }
        }
    }
}

/** Test-only: every text role, with a sample that has descenders ("gjpqy") and wraps to a second line. */
@Composable
fun TextSpecimen(roles: List<Pair<String, LeoTextStyle>>) {
    val c = LeoTheme.colors
    Column(
        Modifier.leoReadableWidth().padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        for ((name, style) in roles) {
            Column(Modifier.fillMaxWidth()) {
                Text(name, style = LeoTextStyle.kicker.toTextStyle(), color = c.inkMuted)
                Text(
                    "Gentle jumping pigs query quickly",
                    style = style.toTextStyle(),
                    color = c.ink,
                )
            }
        }
    }
}

val headlineRoles: List<Pair<String, LeoTextStyle>> =
    listOf(
        "display(48)" to LeoTextStyle.display(48f),
        "display(40)" to LeoTextStyle.display(40f),
        "score" to LeoTextStyle.score,
        "title" to LeoTextStyle.title,
        "question" to LeoTextStyle.question,
        "reviewQuestion" to LeoTextStyle.reviewQuestion,
        "verdict" to LeoTextStyle.verdict,
        "ageLabel" to LeoTextStyle.ageLabel,
        "wordmark" to LeoTextStyle.wordmark,
        "launchWordmark" to LeoTextStyle.launchWordmark,
    )

val bodyRoles: List<Pair<String, LeoTextStyle>> =
    listOf(
        "bodyLarge" to LeoTextStyle.bodyLarge,
        "resultMessage" to LeoTextStyle.resultMessage,
        "body" to LeoTextStyle.body,
        "bodySmall" to LeoTextStyle.bodySmall,
        "caption" to LeoTextStyle.caption,
        "note" to LeoTextStyle.note,
    )

val detailRoles: List<Pair<String, LeoTextStyle>> =
    listOf(
        "explanation" to LeoTextStyle.explanation,
        "reviewExplanation" to LeoTextStyle.reviewExplanation,
        "answer" to LeoTextStyle.answer,
        "answerMarker" to LeoTextStyle.answerMarker,
        "kicker" to LeoTextStyle.kicker,
    )

private fun LeoColors.swatches(): List<Pair<String, Color>> =
    listOf(
        "background" to background,
        "surface" to surface,
        "ink" to ink,
        "inkSoft" to inkSoft,
        "inkMuted" to inkMuted,
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
    )
