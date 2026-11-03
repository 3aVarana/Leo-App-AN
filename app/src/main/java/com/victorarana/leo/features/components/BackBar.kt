package com.victorarana.leo.features.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.victorarana.leo.R
import com.victorarana.leo.features.theme.LeoTextStyle
import com.victorarana.leo.features.theme.LeoTheme
import com.victorarana.leo.features.theme.scaled
import com.victorarana.leo.features.theme.toTextStyle

private const val BODY = 17f

/**
 * The bar of a pushed page: the back arrow with the title of the screen below ([parentTitle]), and an optional
 * [trailing] caption ("2 of 3 missed"). Stands in for the navigation bar and its system back button on iOS, which
 * shows the previous screen's title (or "Back" when it has none, as History and the round page do). The caller
 * keeps the system bars' insets off it.
 */
@Composable
fun BackBar(
    parentTitle: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: String? = null,
) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LeoButton(kind = LeoButtonKind.Ghost, onClick = onBack) {
            LeoIcon(
                id = R.drawable.ic_arrow_back,
                tint = LocalContentColor.current,
                size = 24.dp.scaled(relativeTo = BODY),
            )
            Spacer(Modifier.width(4.dp))
            Text(parentTitle, maxLines = 1)
        }
        Spacer(Modifier.weight(1f))
        if (trailing != null) {
            BasicText(
                text = trailing,
                modifier = Modifier.padding(horizontal = 8.dp),
                style = LeoTextStyle.caption.toTextStyle().copy(color = LeoTheme.colors.inkSoft),
                softWrap = false,
            )
        }
    }
}
