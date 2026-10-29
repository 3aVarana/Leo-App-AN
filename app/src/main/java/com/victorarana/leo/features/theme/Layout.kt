package com.victorarana.leo.features.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

// MARK: - Layout

private val ReadableGutter = 24.dp
private val ReadableMaxWidth = 648.dp

/**
 * The 24dp gutters, and a centered column of 648dp including them (about 600dp of content) on tablets and in landscape.
 * Content stays start-aligned inside it. (iOS `leoReadableWidth`.)
 */
fun Modifier.leoReadableWidth(): Modifier =
    fillMaxWidth()
        .wrapContentWidth(Alignment.CenterHorizontally)
        .widthIn(max = ReadableMaxWidth)
        .fillMaxWidth()
        .padding(horizontal = ReadableGutter)

/**
 * The Broadsheet ground behind a whole screen. Edge-to-edge, so it also fills behind the system bars as long as
 * the modifier sits before any inset padding. (iOS `leoScreenBackground`.)
 */
@Composable
fun Modifier.leoScreenBackground(): Modifier = background(LeoTheme.colors.background)
