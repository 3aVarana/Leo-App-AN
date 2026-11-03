# Phase 5: Preferences, review

Reviewer pass over `git diff 7096ec3` plus untracked files on `port/phase-5-preferences`. Spec: `../Leo/Leo/Features/Preferences/*`, `Features/Root/RootViewModel.swift`/`RootView.swift`, `LeoTests/Features/PreferencesEditorViewModelTests.swift`, `RootViewModelTests.swift:78-134`, the three snapshot suites and `LeoTestsSnapshots/`. Approved decisions D1-D9 and Q1-Q7 are not re-raised.

Ran: `./gradlew :domain:test testDebugUnitTest detekt ktlintCheck lintDebug`: BUILD SUCCESSFUL, 398 tests, 0 failures, 0 skipped (from the result XMLs). Opened `SettingsScreenSnapshotTests/12-14-en.png` next to iOS `screen.12-14-en.png`, plus `12-14-fontScale2-en.png` and `TopicsEditorSnapshotTests/9-11-reviewing-en.png`.

## What is right

- ViewModel parity is close to exact. `updateDraft` runs both `didSet` rules on every draft write, including no-op writes, as Swift does. `add()` has the same guard order and maps every `check` result the same way. `insert` clears the field or shows "already on your list". The review captures the group, drops a late result through `ensureActive()`, rethrows `CancellationException`, and maps any other error to `ReviewFailed`. The `LAZY` start is correct: under `Main.immediate`, a repository that never suspends would otherwise finish before `review` was assigned.
- All 30 iOS editor tests are ported under the same names with the same cases (parameterized cases loop), plus the 6 root editor tests. iOS assertions that Android can't express (`editor == nil` after deinit) are reasonably dropped.
- Copy: all 27 strings match `Localizable.xcstrings` in en/es/pt-BR, and `age_group_label` is reused ("Edad"/"Idade"). The only change is the approved TalkBack caption (Q1).
- The Settings editor is scoped to its back-stack entry, and its initializer runs only once preferences are `Ready` (the gate holds `LeoNavHost` until then), so `requireNotNull` can't fire after process death. The onboarding editor in the Activity store survives rotation.
- At 1x the Settings snapshot matches iOS structure, order and spacing. Nothing clips at 200%.

## Blocking

**B1. A second tap on Cancel or Done pops Home and leaves a blank screen.** `features/root/LeoNavHost.kt:59-60`.
`popBackStack()` runs on every click. While Settings plays its exit animation it is still composed and clickable, so a quick double tap pops Settings and then `Home`. The back stack is then empty and the NavHost draws nothing. Done also calls `editor.save()` twice; root's dedupe hides that, but the blank screen remains. iOS `dismiss()` is idempotent.
Fix: wrap both callbacks in `dropUnlessResumed { navController.popBackStack() }` (lifecycle-runtime-compose, already approved). The entry leaves RESUMED as soon as the pop starts, so the second tap is ignored. Do the same for `onSettings`/`onHistory` at `:37-38`, where a double tap pushes Settings twice. Add a Compose test that clicks Done twice and asserts Home is displayed.

## Should fix

**S1. After process death, onboarding saves the wrong age group.** `features/preferences/OnboardingScreen.kt:47-62`, `features/root/RootScreen.kt:67-71`.
The nested `rememberNavController()` and `selection` (`rememberSaveable`) are restored, so the reader comes back on the Topics screen. The editor is a new ViewModel, though, with `onboardingDraft` = FIFTEEN. The screen then shows "Suggested for 15 to 17", and Done saves FIFTEEN even though the picker still shows the reader's pick (for example 9 to 11). iOS can't hit this because the whole process restarts at the picker.
Fix: keep the draft in a `SavedStateHandle`: `ReaderPreferences` is `@Serializable`, so store its JSON. Build the editor with `createSavedStateHandle()` in the initializer, which also covers the Settings draft. The minimal alternative is `OnboardingRoute.Topics(val ageGroup: AgeGroup)`, with the editor's age group taken from the route. List the chosen fix in `changes.md`.

**S2. A double tap on Continue pushes Topics twice.** `features/preferences/OnboardingScreen.kt:54-58`.
Back from the top Topics then lands on a second Topics, not on the ages. iOS's `isPresented` boolean is idempotent.
Fix: `dropUnlessResumed { ... }`, or `navigate(OnboardingRoute.Topics) { launchSingleTop = true }`.

**S3. The "Suggested · N on" row has the wrong height and jumps when Reset appears.** `features/preferences/SettingsScreen.kt:108`. iOS `SettingsView.swift:51-60`.
iOS applies `.frame(minHeight: 44).padding(.top, 20)`: 20 above a row at least 44 tall. Here `heightIn(min = 44.dp).padding(top = 20.dp)` counts the 20 inside the 44, so the content area is only 24 high. Without Reset (6-8, defaults, the 200% image), the kicker sits about 20dp closer to the timer row than on iOS. With Reset (a 48dp touch area) the row is 68, so it jumps 24dp whenever Reset appears or disappears. iOS stays at 64 in both cases.
Fix: `Modifier.fillMaxWidth().padding(top = 20.dp).heightIn(min = 48.dp)`. Using 48 matches Reset's touch area, so the row no longer jumps. Re-record the Settings snapshots.

**S4. The keyboard can cover the Settings "Add a topic" field.** `features/preferences/SettingsScreen.kt:84-90`.
`windowInsetsPadding(safeDrawing.only(Bottom))` sits inside `verticalScroll`, and `safeDrawing` includes the IME. When the keyboard opens, the content gets taller but the viewport doesn't shrink. The text field's bring-into-view runs while the field is still on screen, and the keyboard then covers it. The field is the last item in Settings. Onboarding is fine, because `LeoBottomBar` consumes the IME inset and the viewport shrinks.
Fix: apply `Modifier.imePadding()` (or `consumeWindowInsets` plus IME padding) to the scroll viewport before `verticalScroll`, and keep only the navigation-bar inset inside the content. Check on a device as part of the pending device pass.

**S5. Home fades under the Settings slide, and the transitions are attached to the wrong destination.** `features/root/LeoNavHost.kt:43-46`.
`exitTransition` and `popEnterTransition` on the Settings destination apply only when something is pushed over Settings, which never happens. Home's exit and pop-enter while Settings opens and closes still use the NavHost default (700ms fade). So Home fades out above the rising sheet and fades back in as it slides down, where a sheet should leave the presenter still. This is also a concept worth getting right for `docs/learning`.
Fix: on the `Home` destination, set `exitTransition = { if (targetState.destination.hasRoute<LeoRoute.Settings>()) ExitTransition.None else null }`, and the matching `popEnterTransition` keyed on `initialState`. Then remove the two dead lambdas from Settings. (Compose already scales animations by `ANIMATOR_DURATION_SCALE`, so the `reduceMotion` branches are harmless but redundant.)

**S6. The haptic replays on rotation and when the editor re-enters composition.** `features/preferences/TopicsEditor.kt:62-64`.
`LaunchedEffect(state.minimumWarningCount)` runs again whenever the composable re-enters composition with a count above 0. That happens after a rotation (the onboarding editor survives it) and on Back to ages then Continue. iOS `sensoryFeedback(trigger:)` fires only on a change.
Fix: remember the last count you played (`rememberSaveable { mutableIntStateOf(state.minimumWarningCount) }`) and vibrate only when the new count is greater. Or expose a one-shot event from the ViewModel.

**S7. TalkBack says nothing while a topic is reviewed.** `features/preferences/TopicsEditor.kt:186`.
`clearAndSetSemantics {}` removes the indicator from TalkBack entirely. iOS `ProgressView` is announced ("In progress"). The Add button also disappears from under TalkBack focus with nothing in its place.
Fix: `clearAndSetSemantics { progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate }`. This also covers the static arc, so the fake 30% is never read out.

**S8. `synchronousReviewFinishes` doesn't test the bug it was written for.** `app/src/test/.../features/preferences/PreferencesEditorViewModelTests.kt:529-538`.
Main is a `StandardTestDispatcher`. Its `immediate` is the same dispatcher (`isDispatchNeeded` is true), so the body always waits for `runCurrent()`. A non-lazy `launch` would pass this test too. The bug described in `changes.md` D3 needs inline dispatch.
Fix: in this test, `Dispatchers.setMain(UnconfinedTestDispatcher())` before building the editor. To prove the test now catches the bug, check that it fails with `CoroutineStart.DEFAULT`.

## Nit

- **N1.** `features/preferences/TopicsEditor.kt:143`: `KeyboardActions(onDone = …)` replaces the default action, so the keyboard stays up after Done. iOS `onSubmit` on a single-line field also ends editing. Either call `LocalSoftwareKeyboardController.current?.hide()` / `defaultKeyboardAction(ImeAction.Done)` after `add()`, or list "keyboard stays open to add another" in `changes.md`.
- **N2.** `features/preferences/TopicsEditor.kt:125,158`: the field and spinner are 44dp at 1x while the Add button's touch area is 48dp. The row is 48 with Add and 44 with the spinner, so it shifts 4dp when a review starts, and the field is below the 48dp touch rule from phase 4. Use `maxOf(height, 48.dp)` for both.
- **N3.** `TopicsEditor.kt:157-166`, `OnboardingScreen.kt`: `state.message` and `MinimumTopicsWarning` appear without an announcement. This matches iOS, so it is not a parity issue. Optional: `liveRegion = LiveRegionMode.Polite` on the message, which helps TalkBack users after a review. If you add it, record it as a deliberate improvement.
- **N4.** `app/src/test/.../snapshots/SettingsScreenSnapshotTests.kt:55` says "The age segments give way to a menu", but `changes.md` notes they don't at 200% on this device. Reword the comment, or add a narrower-width or 2.5x case if the menu fallback should get a snapshot.
- **N5.** `res/drawable/ic_remove.xml:3` says both "derived from the add glyph" and cites `remove_wght500_24px.svg`. Say which: copied from the remove SVG, or derived.
- **N6.** `PreferencesEditorViewModel.kt:109`: Kotlin `trim()` isn't exactly `whitespacesAndNewlines`. It misses U+0085 (NEL) and a few other format characters. This is harmless in practice; mention it only if `:domain` trims with the same rule.
- **N7.** `PreferencesEditorViewModel.kt:195-197`: `onCleared` only nulls `review`. The comment says `viewModelScope` does the cancelling, which is true, so the override could go entirely.

## Verdict

**Changes needed.** B1 is a one-line fix with a test. Fix S1-S8 in this phase too: S3, S4 and S5 are visible on a device, and S8 makes the D3 deviation actually covered. The device pass (onboarding, live review, TalkBack, rotation mid-review, keyboard over the Settings field) is still open, because no device was connected.

---

# Round 2

Re-reviewed the working tree against "Review round 1 fixes" in `changes.md`.

Ran: `./gradlew :domain:test testDebugUnitTest detekt ktlintCheck lintDebug --rerun-tasks`: BUILD SUCCESSFUL. `:domain:test`, `:app:testDebugUnitTest` and `:app:lintDebug` executed; the result XMLs are freshly written: 402 tests, 0 failures, 0 skipped (the 4 new Compose tests added). Roborazzi verify is on (`roborazzi.test.verify=true`), so the re-recorded snapshots match.

## Round 1 findings, verified

| Finding | Fix | Test | Verdict |
|---|---|---|---|
| B1 double pop | `dropUnlessResumed` created inside each destination (`LeoNavHost.kt:51-52,69-75`), so it reads the entry's lifecycle, not the Activity's. Done's `save()` is inside the dropped block. | `doubleTapOnDoneLeavesHomeShowing`, `doubleTapOnOpenSettingsOpensItOnce`: paused clock, two clicks; the second test ends with Cancel, which would reveal a second Settings. | Correct. Meaningful. |
| S1 process death | `OnboardingRoute.Topics(rawAgeGroup)`; the destination writes the route's group into the editor and draws the ground until they agree (`OnboardingScreen.kt:69-78`). Rotation is safe: `setAgeGroup` with the same group doesn't cancel a review, and the minimum warning is unchanged. A re-push can't race the old entry's exit because Continue is dropped until Ages is resumed. | `topicsScreenRestoredAfterProcessDeathKeepsThePickedAgeGroup`: `StateRestorationTester`, a new editor after restore, asserts the draft is NINE. | Correct. Meaningful. See N-R2-1 for style. |
| S2 Continue | `dropUnlessResumed` inside the AgeGroups destination. | `doubleTapOnContinuePushesTopicsOnce` (back must land on the ages). | Correct. |
| S3 Suggested row | `padding(top = 20.dp).heightIn(min = 48.dp)`; 64 vs iOS 68, logged. | Snapshots re-recorded. | Correct. |
| S4 IME | `imePadding()` on the viewport before `verticalScroll`. It consumes the IME inset, so the inner `safeDrawing.only(Bottom)` falls back to the nav-bar inset. No double padding. | Device only. | Correct in code; device pass still open. |
| S5 transitions | Home's exit / popEnter are keyed on Settings, `null` elsewhere, and the dead lambdas are gone. | None. | Partly: see R2-S1. |
| S6 haptic | `rememberSaveable` last-played count; the effect always resets `played` to the current count. After process death (new editor, count 0, restored `played` above 0) it resets silently. Same on Settings reopen. | None (haptics can't be asserted). | Correct. |
| S7 spinner | `clearAndSetSemantics { progressBarRangeInfo = Indeterminate }`. | Covered indirectly by the snapshot. | Correct. |
| S8 test | `UnconfinedTestDispatcher` Main. It shares the scheduler set in `@Before`, so `runCurrent` still applies. The implementer confirmed it fails with `CoroutineStart.DEFAULT`. | | Correct. Now meaningful. |
| N1-N5, N7 | Keyboard hidden on Done; field and spinner `maxOf(44 scaled, 48)` (follows iOS above about 1.1x); polite live region on the message (Android addition, logged); comments fixed; `onCleared` removed. | | Correct. |

## Blocking

None.

## Should fix

**R2-S1. Home is removed the moment Settings starts sliding, so the sheet rises over the bare ground.** `features/root/LeoNavHost.kt:40-42`.
`ExitTransition.None` gives the exiting content nothing to animate. AnimatedContent (NavHost) then disposes it right away; see the KDoc on `ExitTransition.None` in compose-animation 1.12.1. During the slide-up, the area above Settings shows the root ground instead of Home. That is the opposite of what S5 asked for (presenter stays still), and more abrupt than the fade it replaced. `popEnterTransition = None` is fine, because entering content appears at once.
Fix: return `ExitTransition.KeepUntilTransitionsFinished` instead of `ExitTransition.None`. It is a public member of `AnimatedContentTransitionScope`, so it is in scope inside the NavHost lambda. It keeps Home composed, motionless, until the slide ends. NavHost already sets the z-order, so Settings stays on top.
Test: in `SettingsScreenTests`, cancel the Settings opened in `setUp`, set `mainClock.autoAdvance = false`, click "Open settings", call `mainClock.advanceTimeBy(100)`, then `onNodeWithText("Home placeholder (phase 6)").assertExists()`. This fails today and passes with the fix.

## Nit

- **N-R2-1.** `features/preferences/OnboardingScreen.kt:71-77`: the composable now writes to the ViewModel (`LaunchedEffect { editor.setAgeGroup(...) }`), and it renders a gate frame. That is acceptable, but it is the one spot where a screen holds logic. Alternative: build the onboarding editor with `createSavedStateHandle()` and keep the age group (or the whole draft JSON) there, which would also keep toggled topics and texts per round after process death. If you keep the route approach, list it as accepted in `changes.md`.
- **N-R2-2.** `features/preferences/OnboardingRoute.kt:26`: use the existing `AgeGroup.fromRawValue(rawAgeGroup)` (`domain/.../AgeGroup.kt:79`), with `requireNotNull` or `checkNotNull`, instead of repeating `entries.first { … }`.
- **N-R2-3.** S4 and the haptic are still unverified on a device. Keep them on the device checklist in `qa.md`.

## Verdict

**Changes needed, small.** Only R2-S1 remains: a one-token fix plus the paused-clock test above. Every round-1 fix is correct, and each new test would fail without its fix. Approve once R2-S1 lands and the suite passes again. The device pass remains for the user.
