package com.victorarana.leo.features.theme

import android.content.Context
import androidx.core.content.res.ResourcesCompat
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters

/** Port of `ThemeTests.fontIsRegistered`: a missing font file must fail here, not fall back silently. */
@RunWith(ParameterizedRobolectricTestRunner::class)
class ThemeTests(
    private val weight: LeoWeight,
) {
    @Test
    fun fontIsBundled() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertNotNull("${weight.name} font is missing", ResourcesCompat.getFont(context, weight.fontRes))
    }

    companion object {
        @JvmStatic
        @Parameters(name = "{0}")
        fun weights(): List<LeoWeight> = LeoWeight.entries
    }
}
