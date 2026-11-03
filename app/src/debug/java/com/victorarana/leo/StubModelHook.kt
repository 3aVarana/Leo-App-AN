package com.victorarana.leo

import android.content.Intent
import android.util.Log
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
    private const val TAG = "StubModelHook"
    private const val ENABLED_EXTRA = "leo.stubModel"
    private const val AVAILABILITY_EXTRA = "leo.stubAvailability"

    @Volatile private var enabled = false

    @Volatile private var availabilityName = "available"

    // Set once the container has asked for either provider: switching after that would pair the stub writer with
    // the real availability (an `am start` without `-S` against a live process).
    @Volatile private var handedOut = false

    fun enableIfRequested(intent: Intent?) {
        if (intent?.getBooleanExtra(ENABLED_EXTRA, false) != true) return
        if (handedOut) {
            if (!enabled) Log.w(TAG, "Ignoring $ENABLED_EXTRA: the model is already chosen; restart with am start -S")
            return
        }
        enabled = true
        availabilityName = intent.getStringExtra(AVAILABILITY_EXTRA) ?: "available"
    }

    fun languageModel(): LanguageModel? {
        handedOut = true
        return if (enabled) StubLanguageModel() else null
    }

    fun availability(): ModelAvailabilityProvider? {
        handedOut = true
        return if (enabled) StubModelAvailabilityProvider.named(availabilityName) else null
    }
}
