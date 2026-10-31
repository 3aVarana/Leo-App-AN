# Phase 5: Preferences, changes

Branch `port/phase-5-preferences`, baseline `7096ec3`. Nothing committed. `docs/learning/phase-5.md` is left to the user.

## Files

Added (`app/src/main/java/com/victorarana/leo/features/preferences/`): `PreferencesEditorViewModel.kt` (with `TopicMessage`, `PreferencesEditorUiState`), `TopicsEditor.kt` (`TopicsEditor`, `MinimumTopicsWarning`, private `AddTopicField`, `Spinner`), `TextsPerRoundStepper.kt`, `OnboardingScreen.kt` (`OnboardingScreen`, `AgeGroupPicker`, `OnboardingTopics`, `OnboardingRoundSection`, `Headline`, `SectionKicker`), `OnboardingRoute.kt`, `SettingsScreen.kt`. Also `res/drawable/ic_remove.xml`.

Changed: `features/root/RootViewModel.kt` (ctor `topicReviews`, `topicNames`; `makeOnboardingEditor`, `makeSettingsEditor`), `RootScreen.kt` (onboarding gate shows `OnboardingScreen`; passes `root` to `LeoNavHost`), `LeoNavHost.kt` (+ Settings destination), `LeoRoute.kt` (+ `Settings`, KDoc fixed), `Placeholders.kt` (`OnboardingPlaceholder` deleted; Home gets "Open settings"), `MainActivity.kt`, `features/components/TopicChip.kt` (optional `hint`), `values/`, `values-es/`, `values-pt-rBR/strings.xml` (27 strings each).

Tests added: `PreferencesEditorViewModelTests` (32), `TextsPerRoundStepperTests` (4), `OnboardingScreenTests` (3), `SettingsScreenTests` (4), 6 editor tests appended to `RootViewModelTests`, snapshot suites `TopicsEditorSnapshotTests` (6 cases), `OnboardingSnapshotTests` (7), `SettingsScreenSnapshotTests` (4), each light + dark; support `PreferencesEditorFixture.kt`. `RootViewModelTests`/`RootScreenTests` construction updated; `RootScreenTests.availableWithoutPreferencesShowsOnboarding` now finds "How old are you?".

## Decisions D1-D9 (as approved)

- D1 Onboarding stays a gate; `OnboardingScreen` owns a nested NavHost (`OnboardingRoute.AgeGroups/Topics`), picker selection is `rememberSaveable`. Done.
- D2 `PreferencesEditorUiState` + `TopicMessage`; `newTopic` is `mutableStateOf` with `onNewTopicChange`; all draft changes go through `updateDraft`. Done.
- D3 Review job: `viewModelScope.launch`, `ensureActive()` before applying, `CancellationException` rethrown. Deviation inside D3: the job is started `LAZY` and recorded before it starts, because on `Dispatchers.Main.immediate` a repository that never suspends would finish before `review` was assigned and leave the editor stuck "reviewing" (test `synchronousReviewFinishes`).
- D4 Settings editor scoped to its back-stack entry; onboarding editor keyed in the Activity store; `save()` also cancels the review (test `saveCancelsPendingReview`).
- D5 Settings is a full-screen route, slide up/down (none under Remove animations), hand-built Cancel / Settings / Done bar; back = Cancel.
- D6 Hand-built stepper, 48dp-wide buttons in a 48dp-high touch area, one adjustable TalkBack node (contentDescription "title. caption", stateDescription count, `progressBarRangeInfo` 3..9 steps 5, `setProgress`); controls go below the label at fontScale >= 1.3. Buttons carry test tags (hidden from TalkBack).
- D7 Haptic: `HapticFeedbackType.SegmentTick` on each refused tap (skipped for count 0).
- D8 Spinner: Material `CircularProgressIndicator` in inkMuted with a transparent track; still arc under `LocalAnimatesPlaceholders == false` or reduced motion.
- D9 Chip hint appended to the chip's description ("Name. At least 3 topics must stay on."); stepper per D6.

## Answers Q1-Q7

Q1 caption says TalkBack (es/pt-BR translated; phase 6 must really turn the timer off under TalkBack). Q2 route. Q3 temporary "Open settings" link on `HomePlaceholder` (verbatim, delete in phase 6). Q4 iOS-style grey pill with divider (neutral300, 32dp pill in a 48dp touch height). Q5 plain `<string tools:ignore="PluralsCandidate">`. Q6 hint appended. Q7 recolored Material 3 `Switch`.

## Other deviations

- Chip rows keep phase 4's 48dp touch height, so vertical gaps between chip rows are larger than iOS (no extra row spacing added).
- `TopicChip` gained an optional `hint` parameter (needed for Q6).
- Settings' "Reset" is a ghost `LeoButton` with a 14sp label; its touch area is 48dp (phase-4 rule), iOS min height 44.
- The Settings top bar sits below the status bar inside the safe area; the title is centered between the two ghost buttons.
- `RootViewModel` editors save through `::save` (iOS uses `[weak self]`); the root outlives every editor.
- Snapshot additions beyond iOS: `9-11-customized-fontScale2`. iOS `12-14-ax` became `12-14-fontScale2` (names per plan). At 200% the age control still fits as segments on the 390dp snapshot device, so the menu fallback is not exercised by these images.
- Visual comparison with `../Leo/LeoTestsSnapshots/`: topics editor (customized), topics-nine, settings 12-14 compared and match in structure; differences are the Android ones listed above (touch heights, Material switch, no navigation chrome from the OS). Other images were opened and checked for clipping; none found.

## Commands run

- `./gradlew assembleDebug` -> BUILD SUCCESSFUL.
- `./gradlew :domain:test testDebugUnitTest` -> BUILD SUCCESSFUL, 398 tests, 0 failures, 0 skipped (from result XMLs).
- `./gradlew detekt ktlintCheck lintDebug` -> BUILD SUCCESSFUL (after `ktlintFormat`, and fixing detekt: TooManyFunctions and TooGenericExceptionCaught suppressed with reasons, display sizes as constants).
- `./gradlew recordRoborazziDebug --tests ...` for the three new snapshot suites.

No device was connected (`adb` is not on the PATH here), so the device checks (onboarding, live topic review, TalkBack, rotation mid-review, haptics) are not done.

## Review round 1 fixes

- B1: `dropUnlessResumed` (created inside each destination, so it reads the entry's lifecycle) on Home's two links, Settings Cancel and Done (Done saves inside the dropped tap, so it saves once). Tests `doubleTapOnDoneLeavesHomeShowing`, `doubleTapOnOpenSettingsOpensItOnce` (both failed before the fix).
- S1: `OnboardingRoute.Topics(rawAgeGroup)` (the `AgeGroup.rawValue` string: an enum route argument fails lint `MissingKeepAnnotation`, and `:domain` can't use `@Keep`); the Topics destination puts the route's age group into the editor (and draws the ground until they agree). After process death the pick survives; other draft edits (toggled topics, texts per round, custom topics) are lost then, which iOS never faces. Test `topicsScreenRestoredAfterProcessDeathKeepsThePickedAgeGroup` (`StateRestorationTester`).
- S2: Continue is `dropUnlessResumed`; test `doubleTapOnContinuePushesTopicsOnce`.
- S3: Suggested row is `padding(top = 20.dp).heightIn(min = 48.dp)`; Settings snapshots re-recorded and opened (6-8 and 12-14 light/dark: the row no longer jumps when Reset appears). Deviation: 48 instead of iOS's 44 (Reset's touch area).
- S4: `imePadding()` on the Settings scroll viewport. Needs the device pass.
- S5: Home's `exitTransition` / `popEnterTransition` are `None` when Settings is the other end; the dead lambdas on Settings are gone.
- S6: the haptic plays only when the count exceeds the last played one (`rememberSaveable`).
- S7: the spinner is announced as an indeterminate progress bar.
- S8: `synchronousReviewFinishes` runs on an `UnconfinedTestDispatcher` Main; verified it fails with `CoroutineStart.DEFAULT` and passes with `LAZY`.
- Nits fixed: N1 keyboard hidden after Done on the field; N2 field and spinner at least 48dp (deviation from iOS 44); N3 error/rejection message is a polite live region (Android addition); N4 snapshot comment; N5 `ic_remove.xml` provenance comment; N7 no-op `onCleared` removed.
- Skipped: N6 (`trim()` vs `whitespacesAndNewlines`; harmless, not changed).

## Review round 2

- R2-S1: Home's exit towards Settings is `ExitTransition.KeepUntilTransitionsFinished` (was `None`). Test `homeStaysComposedWhileSettingsSlidesOver` (paused clock, Home still there 100ms after opening Settings). Note: the test also passed with `None` in this Navigation/Compose version (a frame-by-frame probe showed Home composed for the whole ~460ms slide and removed at its end), so I could not make it fail before the fix; the explicit value states the intent and doesn't depend on that behavior.
- N-R2-2: `OnboardingRoute.Topics.ageGroup` uses `AgeGroup.fromRawValue` with `checkNotNull`.
- N-R2-1 (accepted): the age group survives process death through the route argument plus a `LaunchedEffect` that writes it into the editor, not through a `SavedStateHandle`. Only the age group is restored; the rest of the draft is lost then.
- N-R2-3: S4 (keyboard over the Settings field) and the haptic remain for the device pass.
