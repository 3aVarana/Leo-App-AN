# Phase 5: Preferences, plan

Baseline `7096ec3` (phases 0-4 merged). iOS spec: `Leo/Features/Preferences/*` (5 files), `LeoTests/Features/PreferencesEditorViewModelTests.swift`, the editor half of `RootViewModelTests.swift:78-134`, and `LeoTests/Snapshots/{Onboarding,SettingsView,TopicsEditor}SnapshotTests.swift`. Reused from earlier phases (do not duplicate): `:domain` topic rules (`ReaderPreferencesTopicEditing.kt`: `check`, `insertTopic`, `setEnabled`, `resetSuggestedTopics`, `removeCustomTopic`, `isAtMinimum`, counts), `RoundSettings.EXERCISE_COUNT_RANGE`/`DEFAULT_EXERCISE_COUNT`, `TopicNames.English`, `readerPreferencesFixture`; `TopicReviewRepository` + `StubTopicReviewRepository`; `PreferencesRepositorySpy`; `AppContainer.topicReviews`/`topicNames`; components `Kicker`, `LeoButton(kind)`, `AgeRow`, `AgeSegmentedControl`, `TopicChip`/`CustomTopicChip`, `ScrollingScreen`/`LeoBottomBar`, `DomainLabels` (`displayName`, `summary`), `ResourceTopicNames.nameRes`, `Dp.scaled(relativeTo)`, `LocalAnimatesPlaceholders`; `assertViewSnapshot(fontScale=)`.

## File map (all `app/src/main/java/com/victorarana/leo/`, package `com.victorarana.leo.features.preferences` unless noted)

| iOS | Kotlin |
|---|---|
| `PreferencesEditorViewModel.swift` | `PreferencesEditorViewModel.kt` (androidx `ViewModel`, `StateFlow<PreferencesEditorUiState>`, plus `TopicMessage` sealed type, D2) |
| `TopicsEditor.swift` (`TopicsEditor`, `MinimumTopicsWarning`, `AddTopicField`) | `TopicsEditor.kt` (same three composables; `AddTopicField` private) |
| `OnboardingView.swift` (`OnboardingView`, `AgeGroupPicker`, `OnboardingTopics`, `OnboardingRoundSection`) | `OnboardingScreen.kt` (`OnboardingScreen` with its nested NavHost, `AgeGroupPicker`, `OnboardingTopics`, `OnboardingRoundSection`), `OnboardingRoute.kt` |
| `SettingsView.swift` | `SettingsScreen.kt` |
| `TextsPerRoundStepper.swift` | `TextsPerRoundStepper.kt` |
| `Root/RootViewModel.swift:15,54-67` | `features/root/RootViewModel.kt`: add `topicReviews`, `topicNames` ctor params, `makeOnboardingEditor()`, `makeSettingsEditor(): PreferencesEditorViewModel?` |
| `Root/RootView.swift:24-28,35` | `features/root/RootScreen.kt` (onboarding gate -> `OnboardingScreen`), `LeoNavHost.kt` + `LeoRoute.kt` (`LeoRoute.Settings`), `Placeholders.kt` (delete `OnboardingPlaceholder`; Home gets an "Open settings" link, Q3) |
| `MyApp` wiring | `MainActivity.kt`: pass `container.topicReviews`, `container.topicNames` to `RootViewModel` |
| (icon) | `res/drawable/ic_remove.xml` (Material Symbols `remove`, wght 500, same provenance as phase 4) |

## Behavior to preserve

ViewModel (`PreferencesEditorViewModel.swift`)
- Nothing reaches `onSave` until `save()` (`:4-5,148-150`); `save()` passes the whole draft (tests `:321-342`).
- Changing `draft.ageGroup` cancels a pending review (`:11-13`); any draft change that leaves the minimum hides the warning (`:14-16`).
- Editing `newTopic` to a *different* value clears `message` (`:21-27`).
- `canAdd` = trimmed text non-empty and not reviewing (`:62-64`); trimming is `whitespacesAndNewlines` (`:156-158`).
- Topics are those of `draft.ageGroup` (`:68-74,152-154`); `canResetSuggestedTopics` = group has disabled ids (`:89-91`).
- `isToggleDisabled` = on and at minimum (`:98-100`); turning one off at the minimum is refused, sets `isShowingMinimumWarning`, increments `minimumWarningCount` (`:103-110`); turning on is always allowed.
- `removeCustomTopic` is a no-op at the minimum (`:117-120`).
- `add()` ignores calls while reviewing or with empty text (`:126-128`), then by `check` result: InvalidLength -> "Topics must be between 2 and 60 characters." (`:131`); AlreadyOnList -> "That topic is already on your list." (`:133,161`); ReEnablesSuggested -> insert without review (`:135`); TooManyCustomTopics -> "You can have up to 20 of your own topics." (`:137`); NeedsReview -> review (`:139`).
- `insert` clears the field on success, else shows "already on your list" (`:165-171`).
- Review: clears message, captures the group at add time (`:176-177`), drops its result if cancelled (`:185`); Accepted(phrase) -> insert into the captured group (`:188-189`), Rejected(reason) -> reason verbatim (`:190-191`), any error -> "We couldn't check that topic. Please try again." (`:192-193`). Editor going away cancels the review (`:54-56`).
Topics editor (`TopicsEditor.swift`)
- Chips in a flow (`:11-16`); kicker "Your topics" heading, 22 top / 10 bottom (`:18-21`); custom chips only when non-empty, remove hidden at minimum (`:23-33`); light haptic per refused tap (`:37`); a11y hint "At least 3 topics must stay on." only on disabled toggles (`:40-45`).
- Warning "Keep at least 3 topics on." note style, magenta700 (`:61-64`).
- Field: placeholder "Add a topic" ink 65% (`:78-79`), size 16 relative to callout (`:81`), IME done submits (`:83-84`), disabled while reviewing (`:85`), 10 horizontal padding, min height 44 scaled (`:72,86-87`), surface fill, divider border, corner 2 (`:88-89`); reviewing swaps "Add" (secondary, disabled when `!canAdd`) for a spinner of the same size (`:90-97`); message below in note/magenta700, 6 spacing (`:99-103`).
Onboarding (`OnboardingView.swift`)
- Picker: selection starts nil, Continue disabled until picked (`:6,59`); Continue sets `draft.ageGroup` then pushes topics (`:16-18`); "Welcome to Leo" kicker, "How old are you?" display 38 heading, body copy inkSoft (`:35-43`); five `AgeRow`s, 2 spacing (`:44-50`); Continue pinned at bottom, 16 top (`:56-63`).
- Topics: display 34 heading "What do you like reading about?" (`:78-81`); "%d topics on · keep at least 3. Leo picks one for each text." bodySmall inkSoft (`:82-84`); warning (`:85`); kicker "Suggested for %s" with `displayName` (`:87`); editor; "Round" kicker + stepper (`:112-123`); Done pinned at bottom (`:99-105`); system back returns to ages (`:69`). Onboarding draft = `ReaderPreferences(FIFTEEN)` (`RootViewModel.swift:55`, already `RootViewModel.onboardingDraft`).
Settings (`SettingsView.swift`)
- Order: "Age group" kicker + `AgeSegmentedControl` + summary caption inkMuted (`:19-26`); "Round" + stepper (`:28-32`); "Reading" + timer switch with "Reading timer" 17 semibold and caption (`:34-47`); "Suggested · %d on" kicker + "Reset" ghost 14 only when resettable, min height 44 (`:49-60`); warning; editor (`:61-63`). Title "Settings", Cancel discards, Done saves then dismisses (`:73-89`). Nothing saved until Done (`:3`). Editor built from current preferences each time it opens (`RootViewModel.swift:59-61`).
Stepper (`TextsPerRoundStepper.swift`)
- Range 3...9 (`:30`); label "Texts per round" + accent count + caption (`:36-49`); a11y: label "Texts per round", value = count, hint = caption, visual label hidden (`:27-33`); at accessibility sizes buttons go below the label (`:11-19`).

## Android decisions

- **D1 Onboarding stays a gate** (phase-4 D1). `RootScreen`'s `Gate.Onboarding` shows `OnboardingScreen(editor)`; inside, its own `rememberNavController()` with `@Serializable OnboardingRoute.AgeGroups` / `Topics` (iOS: `OnboardingView` owns a `NavigationStack`, `:14-24`), so system/predictive back pops to ages. Picker `selection` is `rememberSaveable` (iOS `@State`, not in the VM).
- **D2 Editor state.** `PreferencesEditorUiState(draft, message: TopicMessage?, isReviewing, isShowingMinimumWarning, minimumWarningCount)` plus derived getters (suggested/custom topics, counts, `isAtMinimum`, `canResetSuggestedTopics`, `canAdd`). Messages are `TopicMessage { InvalidLength, AlreadyOnList, TooManyCustomTopics, ReviewFailed, Rejected(reason) }` resolved with `stringResource` (no Context in VMs). `newTopic` is a `mutableStateOf` in the VM with `onNewTopicChange()` (Android guidance for text fields; a StateFlow round-trip loses IME edits). Draft mutators (`setAgeGroup`, `setExercisesPerRound`, `setReadingTimerOn`) all go through one private `updateDraft {}` that applies the two `didSet` rules. Ctor: `(draft, topicReviews, topicNames: TopicNames, onSave)`; `ResourceTopicNames` in the app, `TopicNames.English` in tests.
- **D3 Review job.** `viewModelScope.launch`; after `review()` returns, `ensureActive()` before applying (port of `!Task.isCancelled`); catch `Exception` but rethrow `CancellationException`. `isolated deinit` -> `onCleared` (viewModelScope cancels).
- **D4 Lifetimes.** Settings editor: `viewModel(initializer { root.makeSettingsEditor()!! })` inside the `LeoRoute.Settings` destination, so each open gets a fresh draft and popping clears it. Onboarding editor: keyed in the Activity store (the gate isn't a destination), so it survives rotation. Because it then outlives onboarding, `save()` also calls `cancelReview()`; observably equal to iOS (the view and its review go away on save). Android test `saveCancelsPendingReview`; listed in `changes.md`.
- **D5 Settings presentation** (iOS sheet, `RootView.swift:24`): `LeoRoute.Settings` full-screen destination, slide-up enter / slide-down exit (none under reduced motion); system back = Cancel; top bar hand-built: Ghost "Cancel", heading "Settings", Ghost "Done". Q2.
- **D6 Stepper.** No Compose stepper. Hand-built two-segment control with `ic_remove`/`ic_add`, minus disabled at 3, plus at 9, 48dp touch targets (phase-4 decision). One TalkBack node: `contentDescription` "Texts per round" + caption, `stateDescription` = count, `progressBarRangeInfo(3..9, steps = 5)` + `setProgress` (swipe up/down adjusts, like iOS adjustable). Layout switch at `fontScale >= 1.3` stands in for `isAccessibilitySize`. Look: Q4.
- **D7 Haptic.** `LaunchedEffect(minimumWarningCount)` (skip 0) -> `LocalHapticFeedback.performHapticFeedback(...)` with the lightest type the BOM offers.
- **D8 Spinner.** Restyled `CircularProgressIndicator` (inkMuted), static arc when `LocalAnimatesPlaceholders` is false or reduced motion, so snapshots and `waitForIdle` don't hang on an infinite animation.
- **D9 Hints.** TalkBack has no hint: the chip's minimum hint is appended to its description (Q6); stepper as D6.
- Clean-up: `LeoRoute` KDoc mentions a future `Topics` route that iOS doesn't have; replace with `Settings`.

## Strings (en / es / pt-BR copied from `Leo/Localizable.xcstrings`, line given)
`preferences_welcome` 2945, `onboarding_age_title` 1249, `onboarding_age_message` 1057, `onboarding_continue` 849, `onboarding_topics_title` 2961, `onboarding_topics_count` 158 (`%1$d`), `onboarding_suggested_for` 2241 (`%1$s`), `preferences_round` 1969, `preferences_done` 961, `topics_your_topics` 3169, `topics_add_placeholder` 495, `topics_add` 479, `topics_minimum_hint` 703, `topics_minimum_warning` 1345, `topics_invalid_length` 2705, `topics_already_on_list` 2433, `topics_too_many` 3057, `topics_review_failed` 2897, `settings_title` 2033, `settings_cancel` 801, `settings_reading` 1793, `settings_reading_timer` 1809, `settings_reading_timer_caption` 2561 (Q1), `settings_suggested_count` 2257 (`%1$d`), `settings_reset` 1873, `texts_per_round` 2417, `texts_per_round_caption` 1233. Reuse `age_group_label` (511) for the "Age group" kicker. Count strings: Q5.

## Tests to port
- `PreferencesEditorViewModelTests.swift` -> `app/src/test/.../features/preferences/PreferencesEditorViewModelTests.kt`, every test, same names (`deinitCancelsReview` -> `clearingTheViewModelCancelsReview` via `ViewModelProvider(ViewModelStore(), factory)` + `store.clear()`); parameterized cases loop inside one test; `waitUntil`/`settle` -> `StandardTestDispatcher` as Main + `runCurrent`/`advanceUntilIdle`. Plus Android-only `saveCancelsPendingReview`.
- `RootViewModelTests.swift:81-133` -> 6 tests appended to `features/root/RootViewModelTests.kt` (`settingsEditorStartsFromCurrentPreferences`, `settingsEditorSaveGoesThroughRoot`, `onboardingEditorSaveEndsOnboarding`, `onboardingEditorStartsAtSixTexts`, `onboardingEditorSavesTextsPerRound`, `settingsEditorIsNilBeforeOnboarding`), `advanceUntilIdle` after saves.
- Android-only Compose tests (Robolectric): `TextsPerRoundStepperTests` (bounds, semantics, `setProgress`); `OnboardingScreenTests` (Continue disabled until pick, pushes topics, back returns, Done saves and the gate swaps to the app); `SettingsScreenTests` (opens from Home, Cancel/back discard, Done saves). `RootScreenTests.availableWithoutPreferencesShowsOnboarding` now finds "How old are you?"; constructor updates.
- Snapshots (`app/src/test/.../snapshots/`, light + dark, compare with `../Leo/LeoTestsSnapshots/<Suite>/`): `OnboardingSnapshotTests`: `none`, `12-14`, `12-14-fontScale2` (h 2400), `first-screen`, `topics-nine`, `topics-fifteen` (h 1450), `round-fontScale2` (h 700). `SettingsScreenSnapshotTests`: `12-14` (h 1150), `6-8` (h 1150), `15-17` (h 1200), `12-14-fontScale2` (taller as needed). `TopicsEditorSnapshotTests`: `9-11-defaults`, `9-11-customized`, `6-8-minimum`, `9-11-message`, `9-11-reviewing`, plus `9-11-customized-fontScale2` (Android addition for the 200% rule).

## Open questions for the user
1. Timer caption says "No limit while VoiceOver is on." Use "No limit while TalkBack is on." (es "TalkBack está activado", pt-BR "o TalkBack está ativado")? Recommend yes; phase 6 must then really turn the timer off under TalkBack.
2. Settings as a full-screen route sliding up with a Cancel/Settings/Done top bar (D5), or a Material `ModalBottomSheet` (closer to the iOS card, but drag-to-dismiss vs text field and scroll is fiddly)? Recommend the route.
3. Settings has no entry point until phase 6 (Welcome/Result). Add a temporary "Open settings" link to `HomePlaceholder` (verbatim text, deleted in phase 6)? Recommend yes.
4. Stepper look: copy the iOS system stepper (grey rounded pill with a divider, as in `screen.12-14-en.png`) or Broadsheet (square 2dp corners, hairline border like the secondary button)? Recommend the iOS look for parity.
5. "%d topics on · keep at least 3..." has no plural variants on iOS (the count is never below 3). Plain `<string>` with `tools:ignore="PluralsCandidate"` (parity) or `<plurals>`? Recommend plain string.
6. Chip minimum hint: append "At least 3 topics must stay on." to the chip's TalkBack description (read before "On, Switch")? Recommend yes.
7. Reading timer control: Material 3 `Switch` recolored with Leo colors (Android idiom), or a hand-drawn iOS-style toggle? Recommend recolored `Switch`.

## Acceptance criteria
1. First launch with the model available shows the age picker; Continue disabled until a pick; Continue opens topics; back returns; Done saves and the app appears; relaunch skips onboarding.
2. Settings opens with current preferences; every control edits the draft only; Cancel and system back discard; Done saves once (unchanged preferences don't persist).
3. Every message, limit (2-60 chars, 20 custom, minimum 3, 3-9 texts) and review outcome matches "Behavior to preserve"; a review is cancelled by an age change, Cancel/back, and save.
4. All ported `PreferencesEditorViewModelTests` (30 iOS tests) and 6 root editor tests pass, plus the Android-only tests above.
5. Snapshot suites recorded light and dark, 200% included; every new image opened and compared with its iOS reference; no clipping at 200%.
6. TalkBack: headings on kickers and titles, stepper adjustable with value, timer row a switch, chips as phase 4 plus the hint.
7. All new strings in `values/`, `values-es/`, `values-pt-rBR/`; no hard-coded UI text outside `Placeholders.kt`.
8. No ML Kit or Room import under `features/`; `./gradlew assembleDebug`, `./gradlew :domain:test testDebugUnitTest`, `./gradlew detekt ktlintCheck lintDebug` pass.
9. `changes.md` lists D1-D9 and the answers to Q1-Q7; `docs/learning/phase-5.md` written.

## Tasks
1. Strings (3 locales) and `ic_remove.xml`. Build.
2. `PreferencesEditorViewModel` + `TopicMessage` + `PreferencesEditorViewModelTests`. Tests.
3. `RootViewModel` editor factories + ctor params, `MainActivity`, update `RootViewModelTests`/`RootScreenTests` construction, add the 6 editor tests. Tests.
4. `TextsPerRoundStepper` + `TextsPerRoundStepperTests`. Tests.
5. `TopicsEditor`, `MinimumTopicsWarning`, `AddTopicField` (spinner D8, haptic D7) + `TopicsEditorSnapshotTests`. Record, open, verify.
6. `OnboardingScreen` (+ `OnboardingRoute`, picker, topics, round section); `RootScreen` gate; delete `OnboardingPlaceholder`; `OnboardingScreenTests` + `OnboardingSnapshotTests`. Record, open, verify.
7. `SettingsScreen`, `LeoRoute.Settings` in `LeoNavHost`, Home link (Q3); `SettingsScreenTests` + `SettingsScreenSnapshotTests`. Record, open, verify.
8. Full build, unit tests, lint; device check if connected (onboarding, live topic review, settings, TalkBack, rotation mid-review); `changes.md`; `docs/learning/phase-5.md` (nested NavHost vs `NavigationStack`, VM scoped to a back-stack entry vs `@State`, `mutableStateOf` for text fields, semantics for adjustable controls).

## Decisions (user, 2026-10-31)
The plan is approved, including D1-D9.
- Q1: Change the caption to TalkBack ("No limit while TalkBack is on.", es/pt-BR as proposed). Phase 6 must really switch the timer off under TalkBack.
- Q2: Settings is a full-screen `LeoRoute.Settings` route (D5).
- Q3: Add a temporary "Open settings" link to `HomePlaceholder`. Delete it in phase 6.
- Q4: The stepper copies the iOS system stepper (grey rounded pill with a divider).
- Q5: Plain `<string>` with `tools:ignore="PluralsCandidate"`.
- Q6: Append the minimum hint to a disabled chip's TalkBack description.
- Q7: Material 3 `Switch` recolored with Leo colors.
