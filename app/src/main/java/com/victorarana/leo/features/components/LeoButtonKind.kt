package com.victorarana.leo.features.components

/** The three Broadsheet buttons. Square 2dp corners, serif labels. (iOS `LeoButtonStyle.Kind`.) */
enum class LeoButtonKind {
    /** Full width, accent fill: the screen's main action. */
    Primary,

    /** A hairline border, for actions beside content, like Add. */
    Secondary,

    /** Accent text without a border, for quieter actions. */
    Ghost,
}
