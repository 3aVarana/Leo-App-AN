# Phase 6: Quiz, review

Reviewed against baseline `b1fde47` (uncommitted work on `port/phase-6-quiz`), `plan.md` (with the user decisions Q1-Q5 of 2026-11-03) and `changes.md`. iOS spec: `Leo/Features/Quiz/*`, `Root/RootView.swift`, `RootViewModel.swift`, `MyApp.swift`, the six `QuizViewModel*Tests`, `QuizPlanTests`, `RootViewModelTests`, and the six quiz snapshot suites.

Commands run (read-only): `./gradlew :domain:test testDebugUnitTest detekt ktlintCheck lintDebug` -> BUILD SUCCESSFUL; 558 tests, 0 failures, 0 errors, 0 skipped (summed from the result XMLs; `testDebugUnitTest` re-executed, Roborazzi verify included).

## What was checked and holds

- **Generation loop** (`QuizViewModel.kt:305-375` vs `QuizViewModel.swift:236-280`): one at a time, in order; `writingTopicName` set at the loop head and followed by `onAttempt` only for the current `generationNumber`; a late result after cancel is dropped (`!isActive`); a failure pops the head and re-queues it at the back with the same skill and `replacementTopics` while `replacementsLeft > 0`, else keeps it in `failedJobs` and logs; `showCurrent` when Loading after each item. `MAX_REPLACEMENTS` is reset only by `prepareRound`; `retry`/`retryWithDifferentTopics` neither use nor reset it. Every path keeps `exercises + queue + failedJobs == exerciseCount` (pause changes none of the three).
- **Q1**: `CancellationException` from the repository while the job is active is a failure; the job's own cancellation (`!isActive`) rethrows. `ExerciseGenerator.attempt` rethrows cancellation and `ensureActive()`s on `GenAiException(CANCELLED)`, so a pause can't turn into a failure through the repository either.
- **Q2, pause/resume** in isolation: a pause during an in-flight generation neither loses nor duplicates an item (head stays queued, the cancelled job exits on `!isActive` even with a repository that ignores cancellation, its late `onAttempt` is ignored because `pause()` bumps `generationNumber`). `resume()` can't double-start (`generation?.isActive`), and `isPaused` composes correctly with `configure` (prepared but not started until `resume`) and `start` (shows Loading, generation waits for `resume`). The first ON_START at composition is a harmless no-op.
- **Guards**: `configure` no-op for equal settings once planned; `start` ignored before configure and re-prepares a used round; `finishReading` only while Answering; `select` ignored while reading/after a pick/without exercise; `next` only after a pick; `onRoundFinished` exactly once before `Finished` (double `next()` and the `isCurrent` fade-out guard both covered by tests).
- `optionState`, `dotState`, `plannedTopicNames`, `failedTopicName`, `readingTime`, `isLastExercise`, `makePlan` (`(i + i/topicCount) % topicCount`, shuffled full skill cycles, two random spares), `replacementTopics` (untried shuffled, then tried shuffled, first 3): identical to Swift.
- **Tests**: every iOS test of the six VM suites, `QuizPlanTests` and the three `RootViewModelTests` is present with the same name and the same assertions (parameterized cases looped). Android-only tests cover Q1, Q2, `onCleared`, the fade-out guard, process-death Review pop, timer lifecycle/restoration/reset/TalkBack.
- **Strings**: all 48 strings, in en/es/pt-BR, match `Localizable.xcstrings` verbatim (checked by script), placeholders in the right order; `quiz_topic_separator` keeps its trailing space; Q5 plain strings as decided.
- **Architecture**: no `com.google.mlkit` / `androidx.room` under `features/`; composables only lay out state; `QuizViewModel` depends on `ExerciseRepository` via a factory; fakes, no mocks; no network code.
- **Reading timer**: `rememberSaveable(resetKey)` + `key(exercise.id)` (starts full per exercise, survives rotation), `repeatOnLifecycle(RESUMED)` of the Home entry, time-up once (the timer leaves composition when `finishReading` flips `isReading`), absent when off or with TalkBack.
- `RoundHistoryRecorder` on `applicationScope` (`SupervisorJob + Main`), errors logged not thrown; Review route restored after process death pops; `AnimatedContent(contentKey = phase)` keeps the leaving phase's own state.
- **Snapshots** opened next to the iOS references: Exercise `reading`, Welcome `nine-texts` 200%, Exercise `unanswered` 200%, Review `1-of-1` 200%, Result `5-of-6` 200%. Layout matches (differences are the documented status-bar inset, display-text bottom gap, and Android 14 non-linear font scaling growing large text less than iOS AX sizes); nothing clipped. One row-alignment defect, S1.

## Findings

### B1 (Blocking). Rotation cancels and restarts the text being written
`app/src/main/java/com/victorarana/leo/features/root/RootScreen.kt:42-43`

`LifecycleEventEffect` observes the Activity, and a configuration change runs the old Activity through ON_STOP (before the composition is disposed), then the new one through ON_START. So every rotation (also dark-mode or font-size change) calls `pause()`, which cancels the in-flight Gemini Nano request, and `resume()` starts the same item again from scratch. A reader waiting on Loading who rotates the phone waits for the whole text again; generation running ahead in the background loses its progress each time. Nothing is lost or duplicated, but this is a regression vs iOS introduced by the Android-only D7, and `changes.md` doesn't mention it.

Fix: skip the pause during a configuration change; the `QuizViewModel` survives it, so the running job just keeps going and the new Activity's `resume()` is a no-op (`generation?.isActive`).
```kotlin
val activity = LocalActivity.current // activity-compose 1.13
LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
    if (activity?.isChangingConfigurations != true) viewModel.quiz.pause()
}
```
Add a `QuizScreenTests` case: `scenario.recreate()` while a generation is pending -> `cancelledCount == 0` and `calls.size == 1` (if Robolectric's recreate doesn't set `isChangingConfigurations`, test the predicate through a small seam instead). Note the behavior in `changes.md`.

### B2 (Blocking). A device-language change mid-round re-prepares the round under the reader
`app/src/main/java/com/victorarana/leo/features/root/LeoNavHost.kt:63`, `features/root/RootViewModel.kt:167-170`, `features/quiz/QuizViewModel.kt:209-214`

iOS reference: `RootView.swift:21-23` + `QuizViewModel.swift:168-172`. On iOS this can't happen mid-round: settings are reachable only from Welcome/Results, and a language change terminates the app. On Android the `QuizViewModel` survives the locale configuration change (no `android:configChanges`), the new Activity's `LaunchedEffect(preferences)` runs `preferencesDidChange()`, and `roundSettings(topicNames)` now carries the suggested topics' names in the new language (`RoundTopic.name` is part of equality). `configure` therefore sees new settings and calls `prepareRound()` in the middle of a round:
- `exercises` is emptied while `phase`, `currentIndex` and `answers` stay. After answering text k the reader sits on Loading until k+2 texts of the new plan have been written (one at a time).
- `isRoundStarted` becomes `false`, so "Practice again" does not prepare a new round: it replays exercises 0..k of that plan from index 0, and then the texts the reader already read (k+1.. were shown from the same list).

Fix (Android-only behavior, ask the user to confirm, then list it in `changes.md`): don't re-prepare a started, unfinished round. Recommended: in `QuizViewModel.configure`, when `isRoundStarted && phase != Finished`, store the settings and apply them in the next `start()` (which already re-prepares a used round), so the new language reaches the next round as `prepareRound`'s comment intends. Test: `configureMidRoundAppliesAtTheNextStart` (no new factory call, `jobCount`/`currentExercise` unchanged mid-round; `start()` after Finished uses the new settings).

### S1 (Suggestion, parity). Stacked result rows: the number sits 6 dp above the title
`app/src/main/java/com/victorarana/leo/features/quiz/ResultScreen.kt:189,197`

iOS `ResultView.swift:128-135` pads the whole `HStack` (number and stack) by 6 vertically and aligns on the first baseline. Here only the `Column` is padded, so in `ResultScreenSnapshotTests/5-of-6-fontScale2-*.png` every number is visibly higher than its title's first line. Fix: when stacked, put `padding(vertical = 6.dp)` on the `Row` instead of the `Column` (or align the number with `Modifier.alignBy(FirstBaseline)` against the title). Re-record the two images.

### S2 (Suggestion, parity). Welcome count is not clamped
`app/src/main/java/com/victorarana/leo/features/quiz/QuizScreen.kt:54`

iOS passes `preferences.roundSettings.exerciseCount` (clamped, `RootView.swift:52`). Use `ReaderPreferences.clampedExerciseCount(preferences.exercisesPerRound)` (or `roundSettings(...)`), so the headline and dots can never disagree with the round the quiz prepares.

### S3 (Suggestion). Formatting locale can differ from the strings' locale
`WelcomeScreen.kt:115,128`, `ReadingTimer.kt:104`, `ResultScreen.kt:241`

`LocalConfiguration.current.locales[0]` is the device locale. On a device in a language the app doesn't ship (say French) the sentence comes from `values/` (English) but `ListFormatter` joins with "et", and the timer's spoken value is French. Use the locale the resources resolved to, e.g. `configuration.locales.getFirstMatch(arrayOf("en", "es", "pt-BR"))`, in one shared helper. Compare with what iOS Foundation formatters do in that case before changing.

### S4 (Suggestion, idiom). Icons inside `LeoButton` hardcode the button's foreground
`GenerationFailedScreen.kt:56`, `ReviewScreen.kt:122`

`LeoButton` provides `LocalContentColor`; tint with `LocalContentColor.current` so the icon follows the button kind (and any future pressed/disabled color) instead of repeating `colors.background` / `accentLink`.

### S5 (Suggestion, idiom). Conditional composable call inside `takeIf`
`ExerciseScreen.kt:111`

`rememberTalkBackOn()` runs only when `isReadingTimed` is true, inside an inline lambda. Legal, but it reads like a plain predicate. Hoist it: `val talkBackOn = rememberTalkBackOn()`, then `state.readingTime.takeIf { isReadingTimed && !talkBackOn }`.

### S6 (Suggestion). Filler assertion
`app/src/test/java/com/victorarana/leo/features/quiz/QuizViewModelRoundTests.kt:388`

`assertNotNull(quiz.state)` checks nothing and has no iOS counterpart. Remove it.

## Verdict

**Changes needed.** Two blocking items, both Android lifecycle/configuration issues in otherwise faithful, well-tested work: B1 (rotation restarts the in-flight generation through the D7 pause) and B2 (a locale change mid-round re-prepares the round, causing a long stall and repeated texts after "Practice again"). B2's fix changes `configure`'s behavior on Android, so confirm it with the user first. The suggestions can go in with them or later.

## Round 2

Re-reviewed the fixes listed in `changes.md` "Review fixes (round 2)".

Commands run (read-only): `./gradlew :domain:test testDebugUnitTest detekt ktlintCheck lintDebug --rerun-tasks` -> BUILD SUCCESSFUL, 71 tasks executed (nothing reused), 559 tests, 0 failures, 0 errors, 0 skipped (summed from the result XMLs; Roborazzi verify included). Mutation check in a throwaway copy of the repo under the session scratchpad (the project wasn't touched): with the B1 guard removed (`viewModel.quiz.pause()` unconditional), `QuizScreenTests.recreatingTheActivityMidGenerationDoesNotCancelIt` fails (1 of 10), so the test really exercises the configuration-change path.

| Finding | Status |
|---|---|
| B1 rotation restarts generation | **Fixed.** `RootScreen.kt:46-49`. `LocalActivity` comes from activity-compose (1.13 here, available since 1.10), captured at composition, so the ON_STOP lambda reads the old Activity, whose `isChangingConfigurations` is already true when the framework stops it for a relaunch. A real departure (home, another app, screen off) has it false, so it pauses as before (`leavingTheAppPausesGenerationAndReturningResumesIt` still passes). Null `LocalActivity` (no Activity host: previews, some tests) means `!= true`, so it pauses: the safe default. The new Activity's ON_START `resume()` is a no-op while the job is active. The new test recreates the Activity mid-generation and checks no cancel, one call and one prepared round. Verified by the mutation above. |
| B2 locale change mid-round | **Resolved by user decision** (keep iOS behavior, 2026-11-03), documented as a known issue in `changes.md` with cause and effect. Not blocking. |
| S1 stacked row number offset | **Fixed.** `ResultScreen.kt:184-188`: the 6 dp vertical padding is on the `Row` when stacked. Opened `5-of-6-fontScale2` light and dark, full height and a crop of the rows: every number is level with its title's first line, like iOS `accessibilitySize.5-of-6-ax`. Nothing clipped; dark colors correct. |
| S2 Welcome count clamp | **Fixed.** `QuizScreen.kt:54` uses `ReaderPreferences.clampedExerciseCount`. |
| S3 formatting locale | **Deferred to phase 8**, noted in `changes.md`. Acceptable. |
| S4 icon tints | **Fixed.** `GenerationFailedScreen.kt:57`, `ReviewScreen.kt:123` use `LocalContentColor.current`. |
| S5 conditional composable call | **Fixed.** `ExerciseScreen.kt:111-112`. |
| S6 filler assertion | **Fixed.** Removed from `QuizViewModelRoundTests`. |

No regressions found: the changes are local to the lines above, the full suite and lint pass from scratch, and the other snapshot references still verify.

Still open for the user (device needed, listed in `changes.md`): live Gemini Nano round, `BACKGROUND_USE_BLOCKED` with pause/resume, rotation on a real device (B1 end to end), TalkBack, haptics, Remove animations, process-death restore of Review, `connectedDebugAndroidTest`.

### Verdict (round 2)

**Approve.** No Blocking findings remain.
