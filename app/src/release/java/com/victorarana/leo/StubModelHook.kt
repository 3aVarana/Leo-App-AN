package com.victorarana.leo

import android.content.Intent
import com.victorarana.leo.data.genai.LanguageModel
import com.victorarana.leo.data.repositories.ModelAvailabilityProvider

/**
 * The release half of the stub model's switch: it never answers, so release builds always use Gemini Nano. The debug
 * half, with the same members, is in `app/src/debug/`. No stub class exists in this variant.
 */
@Suppress("FunctionOnlyReturningConstant")
object StubModelHook {
    @Suppress("UNUSED_PARAMETER")
    fun enableIfRequested(intent: Intent?) = Unit

    fun languageModel(): LanguageModel? = null

    fun availability(): ModelAvailabilityProvider? = null
}
