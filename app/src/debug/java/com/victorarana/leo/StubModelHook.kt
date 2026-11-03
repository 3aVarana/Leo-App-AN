package com.victorarana.leo

import android.content.Intent
import com.victorarana.leo.data.genai.LanguageModel
import com.victorarana.leo.data.genai.StubLanguageModel
import com.victorarana.leo.data.genai.StubModelAvailabilityProvider
import com.victorarana.leo.data.repositories.ModelAvailabilityProvider

/**
 * The debug half of the stub model's switch (the release half, in `app/src/release/`, never answers). Turn it on
 * for a whole process with an intent extra, so the emulator walks the app without Gemini Nano:
 *
 *     adb shell am start -S --ez leo.stubModel true -n com.victorarana.leo/.MainActivity
 *     adb shell am start -S --ez leo.stubModel true --es leo.stubAvailability downloadable ...
 *
 * `-S` restarts the process, because the container reads the switch once. [AVAILABILITY_EXTRA] is `available` (the
 * default), `downloadable` (a simulated download that ends `available`) or `notEligible`.
 */
object StubModelHook {
    private const val ENABLED_EXTRA = "leo.stubModel"
    private const val AVAILABILITY_EXTRA = "leo.stubAvailability"

    @Volatile private var enabled = false

    @Volatile private var availabilityName = "available"

    fun enableIfRequested(intent: Intent?) {
        if (intent?.getBooleanExtra(ENABLED_EXTRA, false) == true) {
            enabled = true
            availabilityName = intent.getStringExtra(AVAILABILITY_EXTRA) ?: "available"
        }
    }

    fun languageModel(): LanguageModel? = if (enabled) StubLanguageModel() else null

    fun availability(): ModelAvailabilityProvider? =
        if (enabled) StubModelAvailabilityProvider.named(availabilityName) else null
}
