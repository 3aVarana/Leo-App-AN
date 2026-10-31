# Phase 4: App shell, plan

iOS spec: `Leo/App/MyApp.swift`, `Leo/Features/Root/*` (4 files), `Leo/Features/Components/*` (15 files). Tests: `LeoTests/Features/{LaunchIntroTests,ProgressDotsTests,RootViewModelTests}.swift`, `LeoTests/Snapshots/{ComponentsSnapshotTests,LaunchIntroSnapshotTests,UnavailableViewSnapshotTests}.swift`.
Builds on: `features/theme/` (LeoTheme, LeoColors, LeoTextStyle.toTextStyle, leoReadableWidth, leoScreenBackground, LocalAnimatesPlaceholders), `features/components/{DomainLabels,ResourceTopicNames}.kt`, `data/repositories/*` (suspend `PreferencesRepository`, `ModelAvailabilityProvider` with `StateFlow` + `refresh/download/prepare`), `data/genai/*`, `data/room/RoomRoundHistoryRepository.live`, `data/datastore/DataStorePreferencesRepository.create`, test support (`assertViewSnapshot`, `PreferencesRepositorySpy`, `StubModelAvailabilityProvider`, `StubTopicReviewRepository`, `ExerciseRepositoryFactorySpy`). Package root `com.victorarana.leo` (abbreviated `leo/` below), all in `:app`.

## File map

| iOS | Kotlin (`app/src/main/java/com/victorarana/leo/...`) |
|---|---|
| `App/MyApp.swift` | `LeoApplication.kt` (+ `android:name` in manifest), `AppContainer.kt`, `MainActivity.kt` (sets `RootScreen`) |
| `Root/RootViewModel.swift` | `features/root/RootViewModel.kt` (+ `RootUiState`); phase-4 subset, see D3 |
| `Root/RootView.swift` | `features/root/RootScreen.kt` (availability/onboarding gates + launch intro), `features/root/LeoNavHost.kt` (+ `LeoRoute`), `features/root/Placeholders.kt` (temporary) |
| `Root/LaunchIntro.swift` | `features/root/LaunchIntro.kt` (pure `LaunchIntro` object: `Timing`, `Phase`, `Exit`, `exit`, `canCompareHeader`, `headerMoved`, `hidesHeader`, `wordmarkMove`), `features/root/LaunchIntroOverlay.kt` (`Modifier.launchWordmarkTarget()`, `LaunchIntroHost` composable) |
| `Root/UnavailableView.swift` | `features/root/UnavailableScreen.kt` (takes `ModelAvailability`, not only a reason) |
| `Components/DotState.swift` | `features/components/DotState.kt` |
| `ProgressDots.swift` | `features/components/ProgressDots.kt` (`ProgressDots`, `DotRow` Layout + `DotRow.side`) |
| `RoundResultDots.swift`, `Kicker.swift`, `ReviewLabel.swift`, `DottedLeader.swift`, `AgeRow.swift`, `HistoryButton.swift`, `SkeletonLines.swift`, `AgeSegmentedControl.swift` | same name `.kt` in `features/components/` |
| `AnswerRow.swift` | `features/components/AnswerRow.kt` + `OptionState` enum there (iOS nests it in `QuizViewModel`; phase 6 reuses it) |
| `TopicChip.swift` | `features/components/TopicChip.kt` (`TopicChip`, `CustomTopicChip`, private `ChipLabel`) |
| `LeoButtonStyle.swift` | `features/components/LeoButton.kt` (`LeoButton(kind: LeoButtonKind, onClick, enabled, content)`) |
| `ScrollingScreen.swift` | `features/components/ScrollingScreen.kt` (`ScrollingScreen`, `TopOffset`, `leoBottomBar` slot) |
| `FlowLayout.swift` | none: Compose `FlowRow(horizontalArrangement = spacedBy(8.dp), verticalArrangement = spacedBy(8.dp))` already narrows an over-wide child to the row (`FlowLayout.swift:37-44`) |
| (SF Symbols) | `res/drawable/ic_*.xml` vectors (Q7); (Reduce Motion, Dynamic Type caps) `features/theme/Accessibility.kt` |

## Behavior to preserve

- **Composition** (`MyApp.swift:12-24`): one preferences repo, one availability provider, one topic-review repo, exercise repos made per round from `RoundSettings.ageGroup`, history repo; quiz `onRoundFinished` → history (wired in phases 6/7). Test host shows nothing (`:7,29-30`): `LeoApplication.onCreate` must stay cheap (container fields `by lazy`), so Robolectric tests never build ML Kit/Room.
- **Gates** (`RootView.swift:14-42`): `Available` + preferences → app; `Available` + no preferences → onboarding; else unavailable screen. Launch intro wraps all three (`:42`); the swap between onboarding and app animates (`:41`). Model becoming available swaps screens live (`RootViewModel.swift:32-36`).
- **RootViewModel**: loads preferences at init (`:29`); `save` ignores equal preferences (`:40`), else persists then sets (`:41-42`); onboarding draft is `ReaderPreferences(ageGroup = FIFTEEN)` (`:55`).
- **LaunchIntro timings** (`LaunchIntro.swift:14-46`): hold 0.3 s, move 0.48 s curve (0.65,0,0.25,1), page delay 0.2 s, page 0.48 s curve (0.2,0.7,0.2,1), rise 24, reduced-motion fade 0.2 s easeInOut, header-check margin 0.02; `pageEnd = swap = hold+pageDelay+pageDuration`, `playing = swap-hold`. Plays once per process (`:9,276`). Phase rules `:57-68`; `exit` `:82-84`; header compare `:89-91`; sub-pixel tolerance 0.5 on x/y/w/h (`:96-98`); `hidesHeader` `:102-104`; `wordmarkMove` center minus rise, scale = header h / overlay h (`:118-123`). Taps blocked until `done` (`:213`); overlay hidden from accessibility (`:253`); wordmark is `launchWordmark` (fixed 48, ink) centered on screen (`:237-246`); ground always drawn (`:222`); cancelled task doesn't finish the intro (`:287-290`). No header target → fade (L1).
- **Unavailable** (`UnavailableView.swift:8-28`): `ScrollingScreen`, `TopOffset(140)`, icon 40 scaled, accent700, a11y-hidden; `Kicker("Leo")` top 16; title `display(40)` heading, vertical 10; message `body` inkSoft; `Spacer(min 24)`. No button on iOS (`:3-4`). iOS copy + es/pt-BR in `Localizable.xcstrings:607-650,2625-2636,2785-2796`.
- **LeoButton** (`LeoButtonStyle.swift`): disabled 0.45 (`:41`); primary full width, min 52 scaled, 17 semibold, background text, h16, corner 2, pressed `accentPressed` (`:36,47-56`); secondary min 44, 15 semibold, ink, h14, pressed ink 0.14, divider hairline (`:57-64`); ghost 16 semibold `accentLink`, h4, min 44, pressed accent 0.18 (`:65-74`); centered multiline label (`:80`). No ripple.
- **AnswerRow** (`AnswerRow.swift`): spacing 14, first-baseline (`:17`); marker hidden from TalkBack unless `readsMarker` (`:21`); optional note `note` magenta700 (`:25-28`); icon 22 always laid out, opacity 0 when idle/dimmed (`:32-37`); padding v14 h16, corner 2 (`:42-44`); dimmed 0.7 (`:45`); colors per state `:49-83`.
- **AgeRow**: check 28 / ring 24 + horizontal pad (28-24)/2, ring 1.5 neutral600 (`AgeRow.swift:10-35`); label `ageLabel` accent700 when selected; caption inkMuted; v12; merged semantics + selected (`:42-43`).
- **Chips** (`TopicChip.swift`): toggle reads as a switch (`:3`), leading checkmark/plus (`:40`); custom chip trailing xmark, label = name, hint "Removes this topic." (`:24-28`), static without `onRemove` (`:30`); pressed 0.7 (`:51`); 15 regular, symbol 14 medium; on accent800 on accent100, off neutral700 + divider border; v7 h12, corner 2 (`:62-90`).
- **AgeSegmentedControl**: segments with 1-wide dividers, caption, single line, v9 h4, selected accent fill + background text, TalkBack label = `displayName` + selected (`:15-50`); falls back to a menu labelled "Age group" when segments don't fit (`:9-12,52-76`).
- **ProgressDots**: spacing 8, regular 28 / large 36 (`:20,47-48`); number 13/15 semibold, min scale 0.5 (`:56-58`); colors/fill/ring `:72-97`; capped at xxxLarge (`:28`); one TalkBack node "Text %1$d of %2$d" (`xcstrings:2321`), hidden when `currentIndex == null` (`:29-37`). `DotRow.side` `:106-110` (fits, never above ideal, never negative, count 0 → ideal).
- **RoundResultDots**: diameter 8, spacing 4, filled ink / ring 1.5 magenta, hidden, capped (`RoundResultDots.swift:9-33`).
- **HistoryButton**: `SIX` → ghost "Past rounds" with icon 20, min 44, capped ax1 (`:13-30`); others icon 24 in 44x44, label "Past rounds" (`:32-40`).
- **SkeletonLines**: widths [1,.96,1,.88,1,.72], bar 9, gap 12, height 9*6+12*5, neutral300 corner 1, pulse 0.9 s easeInOut repeat with 0.15 s stagger, 1↔0.35; static 0.6 under reduce motion or `LocalAnimatesPlaceholders == false`; hidden (`SkeletonLines.swift:6-48`).
- **Small ones**: Kicker = `kicker` style, uppercased, inkMuted default (`Kicker.swift:24-27`); ReviewLabel "Review" 14 semibold magenta700, v4 h10, divider border (`ReviewLabel.swift:6-12`); DottedLeader ink 0.45, width 1, dash [1,2], hidden (`DottedLeader.swift:7-9`); ScrollingScreen min height = viewport, readable width, ink; TopOffset spacer min 24 max h; bottom bar spacing 8, top 16, background (`ScrollingScreen.swift:9-44`).

## Tests to port

| iOS | Kotlin (`app/src/test/java/com/victorarana/leo/...`) |
|---|---|
| `LaunchIntroTests` (14) | `features/root/LaunchIntroTests` (plain JUnit, `androidx.compose.ui.geometry.Rect/Offset`) |
| `ProgressDotsTests` (6) | `features/components/ProgressDotsTests` |
| `RootViewModelTests` | `features/root/RootViewModelTests`: now `loadsPreferencesAtInit`, `saveSetsAndPersists`, `savingEqualPreferencesDoesNotPersist`, `availabilityFollowsProvider`. Deferred: editor tests → phase 5, `preferencesDidChange*` → 6, history tests → 7 |
| `ComponentsSnapshotTests` | `snapshots/ComponentsSnapshotTests`: `gallery` (heightDp 1300+), `gallery-fontScale2`, `dots-narrow-fontScale2` (320dp frame); Android-only `ageSegmentedControl` (1x segments, 2x menu fallback) |
| `UnavailableViewSnapshotTests` | `snapshots/UnavailableScreenSnapshotTests`: `device-not-eligible`, `download-failed`, `other`, `downloadable`, `downloading-indeterminate`, `downloading-42`, plus `-fontScale2` for device-not-eligible and downloading-42 |
| `LaunchIntroSnapshotTests` | `snapshots/LaunchIntroSnapshotTests`: `launch`, `reduced-motion`, `rest`, over a test-only page with a `launchWordmarkTarget()` header (WelcomeView arrives in phase 6, which re-records against it) |
| Android-only | `RootViewModelTests.preparesAvailabilityAtInit`, `refreshesAvailabilityOnResume`, `retryDownloadCallsDownload` (if Q3), `loadingStateBeforePreferencesLoad`; `features/root/RootScreenTests` (Robolectric, no images): each gate shows the right screen, Home→History navigates and back pops; `features/theme/AccessibilityTests` (animator scale 0 → reduce motion) |

## Android decisions

- **D1 Gates vs routes.** Mirror iOS: availability and onboarding are state gates in `RootScreen`, outside the `NavHost`; `NavHost` lives only in the Available+onboarded branch. `LeoRoute` (`@Serializable`) gets only `Home` and `History` now; later phases add `Review`, `HistoryRound(id)`, `Topics`. **Placeholders (explicit):** `HomePlaceholder` (phase 6 replaces with the quiz phase switch), `HistoryPlaceholder` (phase 7), `OnboardingPlaceholder` (phase 5, Q6). Verbatim text, no string resources, deleted when replaced. Settings sheet (`RootView.swift:24`) is phase 5's decision.
- **D2 AppContainer.** Owns one `GenerativeModel` (`Generation.getClient()`), passed to `MlKitLanguageModel` and `MlKitModelStatusSource` (phase-3 review N1); `topicReviews = GenAiTopicReviewRepository({ getString(R.string.topic_review_generic_rejection) }, model = languageModel)` (phase-3 D5); `makeExerciseRepository: (RoundSettings) -> ExerciseRepository`; `ResourceTopicNames` stays in `features/components` (phase-1 deferral; the container may import it). RootViewModel built via `viewModelFactory { initializer { ... } }`.
- **D3 Async preferences.** `load()` is suspend (phase-2 D1), so `RootUiState.preferences` is `Loading | NotOnboarded | Ready(ReaderPreferences)`. The intro's launch frame holds until both 0.3 s passed and preferences are no longer `Loading` (no flash of onboarding). `RootViewModel` calls `availability.prepare()` at init and `refresh()` on `ON_RESUME` (`LifecycleEventEffect`), per phase-3 R2-N5.
- **D4 Launch intro mechanics.** `PreferenceKey` → a `LaunchWordmarkTarget` holder in a `CompositionLocal`, written by `Modifier.launchWordmarkTarget()` via `onGloballyPositioned { boundsInWindow() }`; `allowsHitTesting(false)` → a full-size overlay consuming pointer input plus `clearAndSetSemantics {}` on the page until `done`; curves as `CubicBezierEasing`; `hasPlayed` in the process-wide object, so rotation/dark-mode recreation doesn't replay. Splash: Q5.
- **D5 Reduce motion & caps.** `rememberReduceMotion()` reads `Settings.Global.ANIMATOR_DURATION_SCALE == 0`, overridable via `LocalReduceMotion` for tests. Dynamic Type caps → `CappedFontScale(max)` providing a `Density` with `min(fontScale, max)`: dots 1.46 (footnote xxxLarge 19/13, matches `ProgressDotsTests.swift:18` 52.6 = 36x1.46), HistoryButton 1.67 (subheadline AX1 25/15). `@ScaledMetric` sizes → `dp * fontScale` (within the cap).
- **D6 Controls.** Hand-built `Box` + `clickable(indication = null)` + `collectIsPressedAsState`, not Material `Button`/`FilterChip`, so nothing looks stock. TopicChip `Role.Switch` + `toggleable`; AgeRow/segments `selectable`; menu fallback = `DropdownMenu` restyled, chosen by a `SubcomposeLayout` measuring the segment row's intrinsic width (port of `ViewThatFits`). Kicker uppercases with the current locale.
- **D7 Unavailable on Android.** Takes `ModelAvailability`: `DEVICE_NOT_ELIGIBLE` (allowlist explanation), `DOWNLOAD_FAILED`, `OTHER`, `Downloadable` (shown briefly before `prepare()` starts), `Downloading(fraction)` (restyled `LinearProgressIndicator`, determinate with percent, indeterminate for `null`; progress semantics for TalkBack). iOS `appleIntelligenceNotEnabled` has no AICore equivalent. Copy: Q1-Q4.

## Open questions for the user

1. **Copy.** Proposed: title "Gemini Nano needed"; not eligible "This device doesn't support Gemini Nano, which Leo uses to create exercises."; other "Gemini Nano isn't available right now."; downloading "Gemini Nano is downloading to this device. Leo opens when it's ready."; failed "Gemini Nano couldn't be downloaded." Recommend these, with es/pt-BR drafted from the iOS translations' wording and listed in `changes.md` for your review.
2. **Allowlist explanation** under the not-eligible message: (a) generic "Gemini Nano runs on the phone itself and only on supported devices. Emulators can't run it."; (b) name phone families (goes stale); (c) add a link (opens a browser). Recommend (a).
3. **Download failed:** add a "Try again" primary button (calls `download()`)? iOS has none because it can't act. Recommend yes, listed as Android-specific.
4. **Downloading title:** same "Gemini Nano needed" title, or "Getting Leo ready"? Recommend "Getting Leo ready" for `Downloadable`/`Downloading` (iOS's `modelNotReady` copy says "still getting ready").
5. **System splash (API 31+)** can't show text without a library: (a) `values-v31/themes.xml` with `windowSplashScreenBackground` = ground and a transparent icon, then the intro's first frame draws "Leo" (a cut from ground to wordmark); (b) a vector drawable of the "Leo" glyphs as the splash icon, aligned by hand; (c) add `core-splashscreen` (unapproved). Recommend (a) now, (b) as phase-8 polish.
6. **Onboarding placeholder:** give it one "Continue" button that saves the iOS onboarding default (`ReaderPreferences(ageGroup = FIFTEEN)`, `RootViewModel.swift:55`) so the app and nav graph can be checked by hand until phase 5? Recommend yes.
7. **Icons:** SF Symbols have no Compose equivalent and Material 3 no longer bundles icons. (a) vector drawables copied from Material Symbols (Apache 2.0, notice in `res/raw` or README); (b) `material-icons-extended` (unapproved, large). Recommend (a).
8. **Phase-3 cleanup (N1):** remove `MlKitLanguageModel.shared/sharedClient` and the `= MlKitLanguageModel.shared` defaults; live tests build `MlKitLanguageModel(Generation.getClient())`. Recommend yes.

## Acceptance criteria

1. `./gradlew assembleDebug assembleRelease assembleDebugAndroidTest` pass; merged manifest has no `INTERNET`; release mapping keeps `GeneratedExercise` and `TopicReview` (phase-3 note).
2. `LeoApplication` + `AppContainer` build every repository with one `GenerativeModel`; no ML Kit/Room/DataStore import under `features/` (detekt).
3. Launch: intro plays once per process (not on rotation), fades on every phase-4 screen, blocks taps until done, honors reduce motion; all 14 `LaunchIntroTests` pass.
4. Unavailable screen renders all 6 states with approved copy in en/es/pt-BR; progress updates live; screen swaps to the app when the provider reports `Available`.
5. Nav graph: `Home` and `History` routes; back from History pops; onboarding/unavailable are gates; placeholders named and documented for phases 5-7.
6. Every component in the file map exists, matches the cited sizes/colors, and has TalkBack semantics (labels, roles, heading, merged/hidden nodes as cited).
7. Snapshots recorded (light, dark, 200%) for the gallery, narrow dots, age control, all unavailable states and the three intro frames; every new image opened and checked.
8. `./gradlew :domain:test testDebugUnitTest` and `./gradlew detekt ktlintCheck lintDebug` pass; new strings in all three `strings.xml`.
9. `changes.md` lists every Android-specific change (D1-D7, answers to Q1-Q8); `docs/learning/phase-4.md` written (one page).

## Tasks

1. App deps: `kotlin-serialization` plugin, `navigation-compose`, `lifecycle-viewmodel-compose`, `lifecycle-runtime-compose`, `kotlinx-serialization-json` in `app/build.gradle.kts` (all in the catalog). Build.
2. `LeoApplication`, `AppContainer` (lazy), manifest `android:name`; Q8 cleanup incl. androidTest live suites. `assembleDebug assembleRelease assembleDebugAndroidTest`.
3. `features/theme/Accessibility.kt` (reduce motion, `CappedFontScale`) + `AccessibilityTests`; Q7 drawables. Build + tests.
4. `DotState`, `ProgressDots`/`DotRow` + `ProgressDotsTests`, `RoundResultDots`, `Kicker`, `ReviewLabel`, `DottedLeader`; strings "Text %1$d of %2$d", "Review". Tests.
5. `LeoButton`, `AnswerRow` (+`OptionState`), `AgeRow`, `TopicChip`/`CustomTopicChip`, `HistoryButton`, `SkeletonLines`; strings "Past rounds", "Removes this topic." (from xcstrings 1617, 1841). Build.
6. `AgeSegmentedControl` ("Age group", xcstrings 511), `ScrollingScreen`/`TopOffset`/`leoBottomBar` (edge-to-edge insets). Build.
7. `ComponentsSnapshotTests`; `recordRoborazziDebug`; open images; verify.
8. `LaunchIntro` object + `LaunchIntroTests`. Tests.
9. `LaunchIntroOverlay` (`LaunchIntroHost`, `launchWordmarkTarget`), splash theme per Q5, `LaunchIntroSnapshotTests`. Record, check.
10. `RootViewModel`/`RootUiState` + `RootViewModelTests`. Tests.
11. `UnavailableScreen` + strings (Q1-Q4, 3 locales) + `UnavailableScreenSnapshotTests`. Record, check.
12. `LeoRoute`, `LeoNavHost`, `Placeholders`, `RootScreen`, `MainActivity`; `RootScreenTests`. Full build, tests, lint.
13. Hand check on a device if connected (cold launch, rotation, download states); `changes.md`; `docs/learning/phase-4.md` (Application/AppContainer vs `@main App`, ViewModel factory, NavHost vs NavigationStack, CompositionLocal vs Environment/PreferenceKey, Layout vs SwiftUI Layout).

## Decisions (user, 2026-10-30)

- **Q1, Q2, Q4 copy:** use the proposed English. Title "Gemini Nano needed"; "Getting Leo ready" for `Downloadable`/`Downloading`; allowlist option (a), the generic line, no link. es/pt-BR are drafted from the iOS translations' wording and listed in `changes.md` for review.
- **Q3 Try again:** **No.** Match iOS: the download-failed state shows the message only, with no button. Drop `retryDownloadCallsDownload`.
- **Q5 Splash:** **Add `androidx.core:core-splashscreen`** (approved by the user as a new dependency; add it to `gradle/libs.versions.toml` at a stable version). Use `installSplashScreen()` in `MainActivity`, ground background, and hand off to the launch intro's first frame without a visible cut. Record it in `changes.md` as a dependency addition.
- **Q6 Onboarding Continue button:** **No.** `OnboardingPlaceholder` is plain text only, with no button and no save. Manual checks past onboarding wait for phase 5; `RootScreenTests` reach the app by seeding preferences.
- **Q7 Icons:** (a) vector drawables copied from Material Symbols (Apache 2.0), with the license notice.
- **Q8 Phase-3 cleanup:** **Yes.** Remove `MlKitLanguageModel.shared/sharedClient` and its defaults; `AppContainer` owns the single `GenerativeModel`; live tests build their own client.
- **Review round 1 follow-ups (user, 2026-10-30):**
  - **Splash:** build a vector drawable of the "Leo" wordmark glyphs as the splash icon in this phase, aligned with the intro's first frame, so there is no visible cut.
  - **Icons:** replace the hand-redrawn icons with the original Material Symbols SVG paths copied from `google/material-design-icons` (Apache 2.0), and fix the per-file comments and the notice to match.
