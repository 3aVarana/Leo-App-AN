package com.victorarana.leo.support

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.fail

private const val MAX_ROUNDS = 100

/**
 * Runs the pending work of the test dispatcher until [condition] holds, and fails the test if it never does. The port
 * of `waitUntil`: there it polls the main actor, here the test scheduler runs everything that is due, so a few rounds
 * are enough and the failure is immediate instead of a timeout.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun TestScope.waitUntil(
    message: String = "The condition never became true",
    condition: () -> Boolean,
) {
    repeat(MAX_ROUNDS) {
        runCurrent()
        if (condition()) return
    }
    fail(message)
}

/** Lets everything that is due run, so a check that something did not happen is meaningful. (`settle()`.) */
@OptIn(ExperimentalCoroutinesApi::class)
fun TestScope.settle() = runCurrent()
