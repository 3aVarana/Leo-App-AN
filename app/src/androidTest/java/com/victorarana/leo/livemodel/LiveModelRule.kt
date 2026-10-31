package com.victorarana.leo.livemodel

import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.victorarana.leo.data.genai.MlKitLanguageModel
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.rules.TestRule
import org.junit.rules.Timeout
import org.junit.runner.Description
import org.junit.runners.model.Statement
import java.util.concurrent.TimeUnit

/**
 * Marks the suites that use the real on-device model (the Swift `LiveModel` suite and its tag). Gradle can
 * select them with `-Pandroid.testInstrumentationRunnerArguments.annotation=com.victorarana.leo.livemodel.LiveModel`.
 * They need a physical device that supports Gemini Nano; on anything else [LiveModelRule] skips them.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class LiveModel

/**
 * Skips the test unless `checkStatus()` is `AVAILABLE` (the Swift `.enabled(if: TestEnvironment.isModelAvailable)`),
 * and fails it after [timeoutMinutes] (the Swift `.timeLimit`). Results vary between runs: a failure means
 * "look at the prompts", not a broken build.
 */
class LiveModelRule(
    timeoutMinutes: Long,
) : TestRule {
    private val timeout = Timeout(timeoutMinutes, TimeUnit.MINUTES)

    override fun apply(
        base: Statement,
        description: Description,
    ): Statement {
        val guarded =
            object : Statement() {
                override fun evaluate() {
                    val status =
                        try {
                            runBlocking { Generation.getClient().checkStatus() }
                        } catch (_: Exception) {
                            FeatureStatus.UNAVAILABLE
                        }
                    assumeTrue(
                        "Gemini Nano is not available on this device (status $status)",
                        status == FeatureStatus.AVAILABLE,
                    )
                    base.evaluate()
                }
            }
        return timeout.apply(guarded, description)
    }
}

/** The model the live suites talk to, over its own client: there is no `AppContainer` in an instrumented test. */
val liveLanguageModel: MlKitLanguageModel by lazy { MlKitLanguageModel(Generation.getClient()) }
