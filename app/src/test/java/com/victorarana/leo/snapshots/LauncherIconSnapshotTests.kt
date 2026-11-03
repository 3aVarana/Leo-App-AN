package com.victorarana.leo.snapshots

import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.ColorDrawable
import android.widget.ImageView
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.victorarana.leo.R
import com.victorarana.leo.support.SNAPSHOT_DEVICE_QUALIFIERS
import com.victorarana.leo.support.assertViewSnapshot
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.reflect.KClass

private const val ICON_SIZE = 108
private const val THEMED_GROUND = 0xFFD7E3EA.toInt()
private const val THEMED_TINT = 0xFF004D63.toInt()

@Composable
private fun Icon(
    shape: Shape,
    themed: Boolean = false,
) {
    // Composed again when the mode flips: the factory's context carries the night mode of Compose's override, so the
    // dark image loads `drawable-night/ic_launcher_background.xml`, as a launcher in dark mode does.
    key(isSystemInDarkTheme()) {
        AndroidView(
            factory = { context ->
                val drawable =
                    if (themed) {
                        // A themed icon: the layer's alpha, tinted, over a quiet ground, scaled as a launcher does.
                        val layer = checkNotNull(context.getDrawable(R.drawable.ic_launcher_monochrome)).mutate()
                        layer.setTint(THEMED_TINT)
                        AdaptiveIconDrawable(ColorDrawable(THEMED_GROUND), layer)
                    } else {
                        context.getDrawable(R.mipmap.ic_launcher)
                    }
                ImageView(context).apply { setImageDrawable(drawable) }
            },
            modifier = Modifier.size(ICON_SIZE.dp).clip(shape),
        )
    }
}

private fun ComposeContentTestRule.snapshotIcons(
    testClass: KClass<*>,
    name: String,
) = assertViewSnapshot(testClass, name) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Icon(CircleShape)
            Icon(RoundedCornerShape(ICON_SIZE.dp / 4))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Icon(CircleShape, themed = true)
            Icon(RoundedCornerShape(ICON_SIZE.dp / 4), themed = true)
        }
    }
}

/**
 * Android only: the adaptive launcher icon under a circle and a rounded-square mask, which shows that the drawing
 * stays inside the safe zone, and the monochrome layer as a themed icon (wrapped in an `AdaptiveIconDrawable`, so it
 * gets the launcher's layer scaling). The dark image has the night background.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = SNAPSHOT_DEVICE_QUALIFIERS)
class LauncherIconSnapshotTests {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun icon() = composeRule.snapshotIcons(this::class, "masks")
}
