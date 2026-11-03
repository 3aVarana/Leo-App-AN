# Phase 6: Quiz, changes

Branch `port/phase-6-quiz`, baseline `b1fde47`. Nothing committed. Learning note: `docs/learning/phase-6.md`.

## Files

Added, `app/src/main/java/com/victorarana/leo/`:
- `features/quiz/`: `QuizViewModel.kt` (`QuizViewModel`, `QuizUiState`, nested `Phase` and `PlanItem`, companion `MAX_REPLACEMENTS`, `makePlan`, `replacementTopics`), `AgeGroupTypography.kt`, `WelcomeScreen.kt`, `LoadingScreen.kt`, `GenerationFailedScreen.kt`, `ExerciseScreen.kt` (screen, private `OptionButton`, `Modifier.shake`), `ReadingTimer.kt`, `ResultScreen.kt`, `ReviewScreen.kt`, `QuizScreen.kt`, `GearButton.kt` (the gear shared by Welcome and Results).
- `data/repositories/RoundHistoryRecorder.kt`.
- `res/drawable/ic_settings.xml`, `ic_error.xml`, `ic_refresh.xml`, `ic_arrow_back.xml` (Material Symbols outlined, weight 500, same provenance comment and license as phase 4).

Changed: `AppContainer.kt` (`applicationScope`, `roundHistoryRecorder`), `MainActivity.kt` (quiz in the Activity store, passed to the root), `features/root/RootViewModel.kt` (`quiz` constructor parameter, `preferencesDidChange()`), `RootScreen.kt` (ON_STOP pause, ON_START resume), `LeoNavHost.kt` (Home shows `QuizScreen`, `LaunchedEffect(preferences)`, `LeoRoute.Review` destination), `LeoRoute.kt` (+ `Review(startIndex)`), `Placeholders.kt` (`HomePlaceholder` deleted), `features/theme/Accessibility.kt` (+ `LocalTalkBackOn`, `rememberTalkBackOn()`), `values/`, `values-es/`, `values-pt-rBR/strings.xml` (48 strings and the `quiz_spelled_out_counts` array each).

Tests added (`app/src/test/java/com/victorarana/leo/`): `features/quiz/` `QuizViewModelTests` (33), `QuizViewModelGenerationTests` (14), `QuizViewModelRoundTests` (19), `QuizViewModelReadingTests` (6), `QuizViewModelRoundSizeTests` (5), `QuizViewModelOptionStateTests` (4), `QuizPlanTests` (5), `ReadingTimerTests` (9), `QuizScreenTests` (9), `WelcomeScreenTests` (7); `data/repositories/RoundHistoryRecorderTests` (2); 3 tests appended to `RootViewModelTests`; support `WaitUntil.kt`, `MainDispatcherRule.kt`, `QuizViewModelSupport.kt`. Snapshot suites (light + dark each): `WelcomeScreenSnapshotTests` (8 cases), `LoadingScreenSnapshotTests` (4), `GenerationFailedScreenSnapshotTests` (4), `ExerciseScreenSnapshotTests` (11), `ResultScreenSnapshotTests` (6), `ReviewScreenSnapshotTests` (5). `LaunchIntroSnapshotTests` now wraps the real `WelcomeScreen` (re-recorded `rest` and `reduced-motion`; `launch` is unchanged because the page is hidden).

Tests updated: `RootViewModelTests`, `RootScreenTests`, `SettingsScreenTests`, `OnboardingScreenTests` (constructor takes a quiz; "Home placeholder" became the Welcome "Start" button; the "Open settings" link became the gear's content description "Settings"). `RootScreenTests.theBackStackSurvives...` now uses Settings as the pushed screen, since there is no way to History. `RootScreenTests.homeNavigatesToHistoryAndBackPops` is removed, with a comment: it moves to phase 7 (Q3).

## Answers Q1 to Q5 (as decided)

- Q1: a `CancellationException` from the repository while the generation job is still active is a failed exercise. `QuizViewModel.generate` checks its own `isActive`: cancelled ends the loop, otherwise it is handled like any failure (replacement or kept for retry). Tests `cancellationThrownByTheRepositoryCountsAsAFailure` and `...WithNoReplacementsLeftIsKeptForRetry` (the first fails if the check is removed; verified).
- Q2: `pause()` on ON_STOP cancels the current generation, keeps the queue head and counts no failure; `resume()` on ON_START runs `generateRemaining()` when the queue is non-empty and nothing is generating. Wired in `RootScreen` with `LifecycleEventEffect`. Android-only VM tests `pauseKeepsTheQueueAndDoesNotCountAFailure`, `resumeContinuesGeneration`, plus `resumeWhileGeneratingDoesNotRestartIt`, `resumeWithoutARoundDoesNothing`, `configureWhilePausedWaitsForResume`, `pausedGenerationNoLongerChangesTheWritingTopic`; and `QuizScreenTests.leavingTheAppPausesGenerationAndReturningResumesIt` (moves the Activity to CREATED and back to RESUMED). One addition beyond the plan: an `isPaused` flag, so a round prepared while the app is in the background (settings saved, then the app stopped) does not start generating until `resume()`.
- Q3: History button hidden. `WelcomeScreen` and `ResultScreen` keep iOS's optional `onHistory`; `QuizScreen` passes null. The snapshots pass `onHistory = {}`, as the iOS snapshots do, so the History button's look stays covered. `HistoryPlaceholder` and `LeoRoute.History` stay for phase 7.
- Q4: Review top bar is a ghost back button (arrow + "Results", an extra `parentTitle` parameter with that default) on the left and "%d of %d missed" on the right.
- Q5: `quiz_topics_more` and `review_count` are plain strings with `tools:ignore="PluralsCandidate"`. Spanish therefore says "1 temas más" for one extra topic, exactly like iOS (checked in `WelcomeScreenTests.spanish`).

## Decisions D1 to D10

- D1 done as planned: `QuizViewModel : ViewModel` in the Activity store (`MainActivity` factory), passed to `RootViewModel`. Constructor `(makeRepository, now: () -> Instant, random: Random, onRoundFinished)`. `Phase` and `PlanItem` are nested in the class (iOS names), `makePlan`/`replacementTopics` are in the companion.
- D2 done: one `publish()` rebuilds `StateFlow<QuizUiState>`; derived values are members of the state. The generation job is `CoroutineStart.LAZY` and recorded before it starts; a late result after cancel is dropped. `QuizScreen` uses `AnimatedContent(contentKey = { it.phase })`. One addition: `QuizScreen` ignores callbacks of a screen that is fading out (`isCurrent`), so a second action on a leaving Welcome (Start) cannot prepare another round (`QuizScreenTests.aSecondTapOnStartWhileTheScreenFadesOutDoesNothing`; fails without the guard, verified). Real touches land on the new screen above the old one, so this guards actions only.
- D3 done: `ReadingTimer` composable, `remaining` in `rememberSaveable` keyed by the exercise id, `repeatOnLifecycle(RESUMED)` loop with `delay(1000)`. `ReadingTimerTests` use the Compose `mainClock` and a `LifecycleRegistry.createUnsafe` of the test's own (no `lifecycle-runtime-testing` dependency). Spoken value through ICU `MeasureFormat` WIDE, zero units omitted ("1 minute, 30 seconds").
- D4 done: `rememberTalkBackOn()` (touch exploration state with a listener, `LocalTalkBackOn` override); the timer is absent while it is on. `liveRegion = Polite` on the merged Loading heading. Option value is `stateDescription`; the Review row action is `onClick(label = "Review")`. Question focus: `FocusRequester` on the focusable heading after read to question (device check).
- D5 done: Quiz phases live inside `LeoRoute.Home`; `LeoRoute.Review(startIndex)` is pushed with a resumed-lifecycle check (the `dropUnlessResumed` form takes no argument), misses come from `quiz.uiState`, an empty list pops at once (`aReviewRouteWithoutMissesPopsAtOnce`). Review slides in from the side (none under Remove animations); Home stays composed under it like under Settings. No `BackHandler` on Home.
- D6 done: `RoundHistoryRecorder(repository, scope).record(round)` on `AppContainer.applicationScope`, logging failures like `RoundHistoryViewModel.swift:89-95`.
- D7 done (Q2).
- D8 done: haptic `HapticFeedbackType.Confirm` / `Reject` (fires on a change of the verdict only, not on rotation), shake `Animatable` + `graphicsLayer.translationX` skipped under reduced motion, timer bar `animateFloatAsState(tween(1000, LinearEasing))`, scroll to feedback with `BringIntoViewRequester` (animated; Compose plays it at zero duration under Remove animations), 250 ms read-to-question crossfade and 300 ms phase crossfade.
- D9 changed: ICU `RuleBasedNumberFormat` (spell-out) is not part of the Android SDK (the class does not resolve against compileSdk 37). Round sizes are only 3 to 9, so the spelled-out count comes from a string array `quiz_spelled_out_counts` (en, es, pt-BR), first letter title-cased in the locale; other counts would fall back to digits. Topic lists use `android.icu.text.ListFormatter` as planned ("A, B, and C" in English, like iOS). Passage text is a `SelectionContainer`.
- D10 done (Q3).

## Other deviations from the plan or from iOS

- `ExerciseScreen` is stateless (state and callbacks), not given the ViewModel; `QuizScreen` collects the state and passes `quiz::next` and the like.
- `QuizUiState.dots` is a convenience member (the screens' `(0..<exerciseCount).map(dotState)`).
- Welcome and Results gears share `GearButton` (touch area 48 dp, iOS 44). The header Row at 200% font scale keeps working because `HistoryButton` caps its own scale.
- Result row stacking at accessibility sizes uses `fontScale >= 1.3` as the stand-in for `isAccessibilitySize` (same threshold as the phase-5 stepper). The row number is centered on the title's first 24 dp line instead of first-baseline aligned (padding fixed in the review round).
- Failure icon: a solid Material `error` circle; the SF Symbol is drawn hierarchically (a lighter ring).
- Reading timer under Settings: iOS keeps counting under a sheet; here the Home entry is not RESUMED while Settings is on top, so the timer pauses. The gear is not reachable during an exercise, so this does not show.
- The timer's remaining seconds survive rotation (`rememberSaveable`) but not process death, like the rest of an unfinished round (D9).
- Observation, not changed: display texts (48/40) have a larger gap under their last line than iOS's snapshots (about 12 dp), since iOS's tight line height does not shrink the last line's box and Compose's does. This is the phase-0 text style and affects every screen alike.
- Snapshots differ from iOS references in the expected ways: no status-bar inset at the top (the Android snapshot has no system bars), Material Symbols gear and clock, Review's top bar shows the back label.
- `review_back_to_round` is unused until phase 7 (the snapshot test uses it); it carries `tools:ignore="UnusedResources"` with a comment. `quiz_failed_kicker` carries `PluralsCandidate` ignore (an ordinal, not a quantity).
- Plan task 8 asked for `docs/learning/phase-6.md`: written (slightly over a page).

## Snapshot comparison

Every new image was opened. Compared against the iOS reference of the same case: Welcome `six`, `adult`, `no-topics`, `two-topics`, `9-texts`, `nine-texts`/`six` at 200%; Loading `1-of-6`, `long-topic`, 200%; Failed `text-2`, 200%; Exercise `reading`, `reading-6-8`, `wrong`, 200% reading and unanswered, `last-missed`; Result `3-of-6`, `0-of-6`, 200%; Review `2-of-3`, `1-of-1-back-to-round`, 200%; the rest were opened and checked for clipping. Nothing is clipped at 200%. Fix made after comparing: the Result row number is ink (iOS), not accent.

## Commands run

- `./gradlew assembleDebug` after each task: BUILD SUCCESSFUL.
- `./gradlew recordRoborazziDebug --tests ...` for the six new suites and `LaunchIntroSnapshotTests` (and `ResultScreenSnapshotTests` again after the number-color fix).
- `./gradlew :domain:test testDebugUnitTest detekt ktlintCheck lintDebug` -> BUILD SUCCESSFUL; 558 tests, 0 failures, 0 skipped (summed from the result XMLs, domain and app).
- `./gradlew verifyRoborazziDebug` -> BUILD SUCCESSFUL (all committed references match, earlier phases included).
- `./gradlew ktlintFormat` on changed files; detekt fixes: `CyclomaticComplexMethod` (ExerciseScreen split into `ExerciseEffects` and `ExerciseBottomBar`; `QuizScreen` suppressed with a reason: one branch per phase), `ReturnCount` (restructured with `when`; `generate` suppressed with a reason), `SpreadOperator` (suppressed, at most two measures), `NestedBlockDepth` (test extracted), long lines.
- Mutation checks, then reverted: the Q1 check turned into an unconditional rethrow (2 tests failed); the `isCurrent` guard removed (1 test failed).

## Review fixes (round 2)

Changed: `features/root/RootScreen.kt`, `features/quiz/{QuizScreen,ResultScreen,GenerationFailedScreen,ReviewScreen,ExerciseScreen}.kt`, `test/.../features/quiz/{QuizScreenTests,QuizViewModelRoundTests}.kt`, `test/snapshots/ResultScreenSnapshotTests/*` (re-recorded).

- **B1 done.** `RootScreen` pauses the quiz on ON_STOP only when `LocalActivity.current?.isChangingConfigurations != true`. A rotation, dark-mode or font-size change no longer cancels the in-flight Gemini Nano request; the ViewModel survives and the new Activity's `resume()` is a no-op (`generation?.isActive`). New test `QuizScreenTests.recreatingTheActivityMidGenerationDoesNotCancelIt` (`ActivityScenario.recreate()` mid-generation, content set again: `cancelledCount == 0`, one call, one prepared round). Robolectric's `recreate()` does set `isChangingConfigurations`: with the guard forced to `true` the test fails (checked, then reverted). `leavingTheAppPausesGenerationAndReturningResumesIt` (a real move to CREATED, no configuration change) still passes and still pauses.
- **B2 documented, not changed (user decision of 2026-11-03: keep iOS behavior).** Known issue: a device-language change mid-round recreates the Activity while the `QuizViewModel` survives. The suggested topics' names change language, so `roundSettings(topicNames)` compares different and `configure` re-prepares the round (`RootView.swift:21-23` + `QuizViewModel.swift:168-172`, which iOS never reaches mid-round because a language change terminates the app). Effect: the reader sits on Loading after the current text until the new plan's texts are written, and "Practice again" reuses that plan (`isRoundStarted` is false) instead of preparing a new round. Parity chosen on purpose; revisit only if it shows up in QA.
- **S1 done.** Stacked Result rows (fontScale >= 1.3): the 6 dp vertical padding is on the `Row`, not on the `Column`, so the number stays on the title's first line like iOS's padded `HStack`. `ResultScreenSnapshotTests` re-recorded (the recorder rewrites the whole suite; only the two `5-of-6-fontScale2` images differ); both opened, numbers now level with the first title line, nothing clipped.
- **S2 done.** Welcome uses `ReaderPreferences.clampedExerciseCount(preferences.exercisesPerRound)`, like iOS's `roundSettings.exerciseCount`.
- **S3 not done, follow-up for phase 8 (Polish).** Formatting locale (`ListFormatter` in `WelcomeScreen.kt`, `ReadingTimer.kt`, `ResultScreen.kt`) is the device locale, which can differ from the locale the string resources resolved to (e.g. a French device gets English text with French "et"). Decide in phase 8 after checking what Foundation formatters do in iOS in that case.
- **S4 done.** Icons inside `LeoButton` (`GenerationFailedScreen`, `ReviewScreen`) tint with `LocalContentColor.current`.
- **S5 done.** `rememberTalkBackOn()` is hoisted out of the `takeIf` lambda in `ExerciseScreen`.
- **S6 done.** Removed the filler `assertNotNull(quiz.state)` (and its import) in `QuizViewModelRoundTests`.

Commands: `./gradlew ktlintFormat`; `./gradlew :app:testDebugUnitTest --tests '*QuizScreenTests*'` (green; red with the B1 guard disabled); `./gradlew recordRoborazziDebug --tests '*ResultScreenSnapshotTests*'`; `./gradlew :domain:test testDebugUnitTest detekt ktlintCheck lintDebug` -> BUILD SUCCESSFUL, 559 tests, 0 failures, 0 errors, 0 skipped (summed from the result XMLs).

## Not run: needs a device

No Android device is connected. Not done: a live round on Gemini Nano; `BACKGROUND_USE_BLOCKED` behavior and the pause/resume while another app is in front; the timer in the background and across rotation on a real device; TalkBack (headings, Loading re-announcement, option values and no click when answered, "Review" action on missed rows, timer node, question focus after "Answer the question"); haptic feel (`Confirm`/`Reject` exist on the BOM's Compose, effect on device); shake and scroll-to-feedback with Remove animations on; process-death restore of Review (route pops); system back from Home (task goes to the background on API 31+); `@LiveModel` instrumented tests (`connectedDebugAndroidTest`).

## CI review nits (round 3, 2026-11-03)

The advisory "Claude Review" check on the pull request found nothing blocking; the user asked for its two timer nits to be fixed.

- `ReadingTimer.kt`: the seconds left moved out of the timer into `ReadingTimerState` (+ `rememberReadingTimerState(total)`, saved across recreation with its own `Saver`). `ReadingTimer` takes the state as a parameter that defaults to a fresh one; the `resetKey` parameter is gone. The per-exercise reset comes from `key(exercise.id)` in `QuizScreen`, which the KDoc now says.
- `ExerciseScreen.kt`: holds the `ReadingTimerState` for the exercise and passes it to the bottom bar, so switching TalkBack on and off while reading hides and shows the timer without restarting the countdown. `ExerciseBottomBar` takes the state instead of the exercise id.
- `ReadingTimerTests.kt`: `resetKey` removed; `aNewExerciseStartsFull` wraps the timer in `key(exerciseId)` as `QuizScreen` does; new `timerKeepsItsTimeAcrossATalkBackToggle` (mutation-checked: fails when the bottom bar gets a fresh state). With the test clock paused, nothing else flushes snapshot writes, so the test toggles TalkBack with `Snapshot.withMutableSnapshot`.
- The other two nits need no code change: AICore rejecting a request between ON_PAUSE and ON_STOP is a device check (added to the NOT RUN list), and `applicationScope` on `Dispatchers.Main` is a deliberate composition-root choice.
- Commands: `./gradlew :domain:test testDebugUnitTest detekt ktlintCheck lintDebug`: BUILD SUCCESSFUL, 560 tests, 0 failures.
