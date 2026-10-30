# Phase 4: App shell, changes

Implemented from `plan.md` with the user's decisions (Q1-Q8). Nothing committed. `../Leo` untouched.

## Files

Added (all under `app/src/main/java/com/victorarana/leo/` unless noted)
- `LeoApplication.kt`, `AppContainer.kt` (lazy fields, one `GenerativeModel`), `MainActivity.kt` (rewritten: `installSplashScreen()`, `RootViewModel` via `viewModelFactory`).
- `features/root/`: `LaunchIntro.kt` (pure), `LaunchIntroOverlay.kt` (`LaunchIntroHost`, `Modifier.launchWordmarkTarget()`, `LaunchWordmarkTarget`), `RootViewModel.kt` (+ `PreferencesState`, `RootUiState`), `RootScreen.kt`, `LeoNavHost.kt`, `LeoRoute.kt`, `UnavailableScreen.kt`, `Placeholders.kt`.
- `features/components/`: `DotState`, `ProgressDots` (+ `DotRow`, `ProgressDotsSize`), `RoundResultDots`, `Kicker`, `ReviewLabel`, `DottedLeader`, `LeoButton` + `LeoButtonKind`, `AnswerRow` + `OptionState`, `AgeRow`, `TopicChip` (+ `CustomTopicChip`), `HistoryButton`, `SkeletonLines`, `AgeSegmentedControl`, `ScrollingScreen` (+ `TopOffset`, `LeoBottomBar`), `LeoIcon`.
- `features/theme/Accessibility.kt` (`LocalReduceMotion`, `isReduceMotion`, `rememberReduceMotion`, `CappedFontScale`, `Dp.scaled()`).
- `res/drawable/ic_{add,auto_awesome,cancel,check,check_circle,close,history,unfold_more}.xml`, `res/drawable/splash_icon.xml`; `assets/licenses/Apache-2.0-MaterialSymbols.md`.
- Tests (`app/src/test/.../`): `features/root/{LaunchIntroTests (14), RootViewModelTests (9), RootScreenTests (6)}`, `features/components/ProgressDotsTests (6)`, `features/theme/AccessibilityTests (6)`, `snapshots/{ComponentsSnapshotTests, LaunchIntroSnapshotTests, UnavailableScreenSnapshotTests}` and their 34 PNGs under `app/src/test/snapshots/`.

Changed
- `gradle/libs.versions.toml` (+ `androidx-core-splashscreen`), `app/build.gradle.kts` (kotlin-serialization plugin, navigation-compose, lifecycle-runtime-compose, lifecycle-viewmodel-compose, kotlinx-serialization-json, core-splashscreen).
- `AndroidManifest.xml` (`android:name=".LeoApplication"`, activity theme `Theme.Leo.Starting`), `res/values/themes.xml` (+ `Theme.Leo.Starting`), the three `strings.xml`.
- Q8 cleanup: `MlKitLanguageModel` (no `shared`/`sharedClient`, `client` required), `MlKitModelStatusSource(client)` and `GenAiModelAvailabilityProvider(source)` (no defaults), `GenAiExerciseRepository(ageGroup, model, language = ...)` and `GenAiTopicReviewRepository(genericRejection, model, language = ...)` (model is now required, so it moved before the defaulted `language`; `GenAiExerciseRepositoryTests` updated), androidTest live suites use `liveLanguageModel` (new, in `LiveModelRule.kt`: its own client).

## Dependency addition (approved by the user, Q5)
`androidx.core:core-splashscreen:1.2.0` (stable; looked up on Google Maven). Everything else added to `:app` was already in the catalog and approved (navigation-compose, lifecycle-*-compose, kotlinx-serialization-json, kotlin-serialization plugin).

## Android-specific changes and deviations from iOS
- **D1 Gates vs routes.** As planned. `LeoRoute` = `Home`, `History`. Placeholders (verbatim text, no resources, to delete): `HomePlaceholder` (phase 6, has an "Open history" link so the graph can be exercised), `HistoryPlaceholder` (phase 7), `OnboardingPlaceholder` (phase 5, plain text, no button, no save: Q6). The settings sheet is phase 5's.
- **D2 AppContainer.** One `GenerativeModel` (`Generation.getClient()`) shared by `MlKitLanguageModel` and `MlKitModelStatusSource`. Exposes `preferences`, `availability`, `topicReviews` (generic rejection read from `R.string.topic_review_generic_rejection`), `history` (not wired until phase 7), `topicNames`, `makeExerciseRepository(RoundSettings)`. `RootViewModel` takes only preferences and availability for now.
- **D3 Async preferences.** `PreferencesState` = `Loading | NotOnboarded | Ready`. The intro's launch frame holds until 0.3 s have passed and `isReady` (the model is not `Available`, or preferences loaded). Detail: while the model is unavailable the first screen doesn't need preferences, so it doesn't wait for them. `prepare()` at init, `refresh()` + `downloadIfNeeded()` on `ON_RESUME`. The first `ON_RESUME` fires at start, so a cold start refreshes twice and may call `downloadIfNeeded()` twice; the provider's download mutex makes the second call wait for the first and return, so no second download starts.
- **D4 Launch intro.** `CompositionLocal` holder instead of a `PreferenceKey`; full-size overlay swallowing pointer input; the page is `clearAndSetSemantics` until `Done` (so TalkBack skips the page for about one second, as the plan said; iOS leaves it readable: revisit if the device check shows a problem); `hasPlayed` is process-wide and set when the intro reaches `Done` (PR #9 review), so rotation afterwards doesn't replay it, and an Activity recreated mid-intro (the hold can last up to the 2 s availability ceiling) plays it again from the launch frame, which waits for `isReady`, instead of showing the provider's placeholder. Differences from the SwiftUI modifier: when the header moves mid-move the wordmark jumps back to the center while it fades (iOS glides); the rise is 24dp and converted to px for the move target. `LaunchIntro.Timing` constants are UPPER_SNAKE (`HOLD`, `PAGE_END`...) because detekt wants that for `const val`; the curves are `CubicBezierEasing` (the reduced-motion `easeInOut` is `(0.42, 0, 0.58, 1)`).
- **Splash (Q5).** `Theme.Leo.Starting` (parent `Theme.SplashScreen`): ground background, the "Leo" wordmark vector as the icon (`splash_icon.xml`, review round 1), `postSplashScreenTheme = Theme.Leo`, `installSplashScreen()` in `MainActivity`. Aligned with the intro's first frame within a pixel at font scale 1 (see round 1 fixes). Not checked on a device.
- **D5 Reduce motion and caps.** `Settings.Global.ANIMATOR_DURATION_SCALE == 0`, observed with a `ContentObserver`, overridable with `LocalReduceMotion`. `CappedFontScale(1.46)` for the dots and `RoundResultDots`, `1.67` for the history text button. `@ScaledMetric` = `Dp.scaled()` (`value.sp.toDp()`, so it follows Android 14's nonlinear scaling and the caps).
- **D6 Controls.** Hand-built (`clickable(indication = null)`, pressed state), no Material buttons. `LeoButton` has a content overload and a `text` overload. Small differences: `AnswerRow` uses top alignment with equal line heights for marker and text instead of a first-baseline alignment (Compose rows can't take a column's first baseline); `AgeRow` is `Role.RadioButton` (iOS: button + selected); `AgeSegmentedControl` is a `SubcomposeLayout` that measures the natural width (`ViewThatFits`) plus a small `Layout` for the segments (each at least its label's width, equal when they fit), menu fallback is a restyled `DropdownMenu`. `CustomTopicChip`: TalkBack's click label is "Remove this topic" (see copy below), not iOS's "Removes this topic." hint, because Android announces it as "Double tap to ...". `ProgressDots` numbers shrink with `TextAutoSize.StepBased` (min 50%), `FlowLayout` is not ported (`FlowRow` does the same). `DotRow` takes the ideal diameter as a parameter (children fill the square), not from their ideal size.
- **D7 Unavailable.** Takes the whole `ModelAvailability`; draws nothing for `Available`. Q2(a): the not-eligible state has a second paragraph. Q3: no button on download failure. `Downloadable` and `Downloading` use "Getting Leo ready" (Q4). The progress bar is hand-drawn (square, 4dp): determinate with a percent, or a pulsing full bar for an unknown size; still under reduced motion and in snapshots. TalkBack gets progress range + percent on the bar's node. `ic_auto_awesome` stands in for the `apple.intelligence` symbol.
- **Q7 Icons.** Written as vector drawables from the Material icon paths (24dp, Apache 2.0, notice in `assets/licenses/Apache-2.0-MaterialSymbols.md`). Path data copied from `google/material-design-icons` in review round 1; only the plain disc behind the check and cancel marks is drawn by hand.
- **Ground behind bars.** `ScrollingScreen` pads content with `safeDrawing` insets (bottom bar above the navigation bar) while the ground fills edge to edge.
- **Not ported yet, as planned:** editor tests, `preferencesDidChange*`, history tests (phases 5-7). `AnswerRow` does not announce its state to TalkBack (iOS doesn't in this file either; phase 6's quiz screen must).

## Copy drafts for review (es / pt-BR)
Wording follows the iOS translations. New strings: Gemini Nano copy, "Getting Leo ready", the allowlist line, the progress label and the chip action are mine; the rest are copied.

| key | en | es | pt-BR |
|---|---|---|---|
| `unavailable_title` | Gemini Nano needed | Se necesita Gemini Nano | É necessário o Gemini Nano |
| `unavailable_preparing_title` | Getting Leo ready | Preparando Leo | Preparando o Leo |
| `unavailable_device_not_eligible_message` | This device doesn't support Gemini Nano, which Leo uses to create exercises. | Este dispositivo no es compatible con Gemini Nano, que Leo usa para crear los ejercicios. | Este dispositivo não é compatível com o Gemini Nano, que o Leo usa para criar os exercícios. |
| `unavailable_device_not_eligible_detail` | Gemini Nano runs on the phone itself and only on supported devices. Emulators can't run it. | Gemini Nano se ejecuta en el propio teléfono y solo en dispositivos compatibles. Los emuladores no pueden ejecutarlo. | O Gemini Nano roda no próprio telefone e apenas em dispositivos compatíveis. Emuladores não conseguem executá-lo. |
| `unavailable_other_message` | Gemini Nano isn't available right now. | Gemini Nano no está disponible en este momento. | O Gemini Nano não está disponível no momento. |
| `unavailable_downloading_message` | Gemini Nano is downloading to this device. Leo opens when it's ready. | Gemini Nano se está descargando en este dispositivo. Leo se abrirá cuando esté listo. | O Gemini Nano está sendo baixado neste dispositivo. O Leo abre quando estiver pronto. |
| `unavailable_download_failed_message` | Gemini Nano couldn't be downloaded. | No se pudo descargar Gemini Nano. | Não foi possível baixar o Gemini Nano. |
| `unavailable_download_progress` | Download progress | Progreso de la descarga | Progresso do download |
| `custom_topic_chip_remove_action` | Remove this topic | Quitar este tema | Remover este tema |
| `progress_dots_accessibility_label` | Text %1$d of %2$d | Texto %1$d de %2$d | Texto %1$d de %2$d |
| `review_label` | Review | Repasar | Revisar |
| `history_button_label` | Past rounds | Rondas pasadas | Rodadas passadas |
| `age_group_label` | Age group | Edad | Idade |

## Snapshots
Recorded with `recordRoborazziDebug`, light and dark, then compared by `testDebugUnitTest`: `ComponentsSnapshotTests` (gallery, gallery 200%, narrow dots 200%, age control 1x, 200%, and 270dp menu fallback), `LaunchIntroSnapshotTests` (launch, reduced-motion, rest, over a test-only page), `UnavailableScreenSnapshotTests` (device-not-eligible, download-failed, other, downloadable, downloading-indeterminate, downloading-42, plus 200% for device-not-eligible and downloading-42). I opened the light and dark gallery, the 200% gallery (light and dark), narrow dots, every age-control image except the 1x dark one at full size, the three intro frames (light; launch dark, reduced-motion dark), and the unavailable states (en light for most, dark for several; not every dark variant of every state). Findings fixed during review: the Results-style dotted-leader row broke "Correct" at 200% (the test row now gives the title priority), and the age segments clipped labels at 200% (segment widths are now at least the label's).

## Not done
- No device connected (`adb` isn't installed here): the cold-launch, rotation, TalkBack, splash and download-state checks of task 13 are left for the user. The live-model suites compile (`assembleDebugAndroidTest`) but were not run.
- `docs/learning/phase-4.md`: left to the user, as asked.

## Commands run (final state)
- `./gradlew assembleDebug assembleRelease assembleDebugAndroidTest`: BUILD SUCCESSFUL. Release dex still contains `GeneratedExercise` and `TopicReview` under their own names; the no-INTERNET manifest check is wired into the build.
- `./gradlew :domain:test testDebugUnitTest`: BUILD SUCCESSFUL (includes Roborazzi verify of the 34 new images).
- `./gradlew detekt ktlintCheck lintDebug`: BUILD SUCCESSFUL (lint: 0 errors, 0 warnings, 1 hint).


## Review round 1 fixes

Blocking
- **B1 TopOffset.** Compose's `Column` can't cap a weighted spacer and still give the rest to the others, and a
  `Spacer` doesn't grow to a `height` cap by itself. `ScrollingScreen` now lays its content out with a small
  private `SpacerColumn` (SwiftUI's split: every flexible child starts at its minimum, the rest is shared by
  weight, a child that reaches its maximum stops and the others share what it leaves). The scope is
  `ScrollingScope` (`Modifier.weight(weight, min, max)`, `Modifier.align(Horizontal)`); `TopOffset(height)` is
  `weight(1f, min = 24.dp, max = height)`. Phase 6 screens call `Spacer(Modifier.weight(1f))` inside
  `ScrollingScreen { }` as before; `Modifier.align` replaces `ColumnScope.align`. The Unavailable icon now sits
  140dp below the top (checked on the 390x844 image).
- **B2 `Dp.scaled(relativeTo)`.** Grows a size by the ratio the tied text size grows (`relativeTo.sp.toDp() /
  relativeTo`), within the caps. Call sites: dots and result dots 13 (footnote), answer icon 17 (body) and its
  line box 24, age check/ring 22 (title2), chip symbol 15 (subheadline), history text icon 15 and icon button 20
  (title3), buttons 17 and 15, Unavailable icon 34 (largeTitle). The large dot at the cap is 53.8dp (iOS 36 x 1.46
  = 52.6): Android 14's font curve grows the 13sp number a little more than a linear scale; the test allows 1.5dp.
- **B3 Launch flash.** `RootUiState.availabilityChecked`: false until the first `refresh()` returns (ceiling 2 s,
  `AVAILABILITY_CHECK_CEILING_MILLIS`, then it stops waiting), then `downloadIfNeeded()` (the second half of
  `prepare()`, now its own interface method). `RootScreen`'s `isReady` requires it. New tests: not checked until
  the first refresh returns, gives up after the ceiling.
- **B4 Splash.** `drawable/splash_icon.xml` is now the "Leo" glyph outlines (Source Serif 4 Semibold, 48dp, kerned),
  exported with CoreText from `res/font/source_serif_4_semibold.otf`, on the 288dp splash canvas, filled with the new
  `@color/leo_ink` (light and night). I rendered it centered in the 390x844 snapshot window and compared its ink
  bounding box with the intro's launch frame: 471,1220 to 698,1318 against 471,1221 to 699,1318 px, so within a
  pixel at font scale 1. Limitations: at another font scale the intro's wordmark is fixed 48dp too
  (`scalesWithFontSize = false`), so it stays aligned; the system's own splash animation/fade and the icon mask on
  API 31+ are not checked on a device. API 29-30 use the library's splash view with the same drawable.
- **B5 Leader row.** New `DottedLeaderRow(title, status)` in `DottedLeader.kt` (a `Layout`: status keeps its width at
  the trailing edge, title keeps priority and wraps only when it must, leader fills the rest, 20dp minimum, 8dp
  gap). The gallery uses it; phase 7's Results rows should too. Re-recorded and compared with iOS: dots run from
  the title to "Correct" at 1x; at 200% the title wraps and the leader falls back to its minimum.
- **B6 Icons.** `ic_add`, `ic_close`, `ic_check`, `ic_history`, `ic_unfold_more` (weight 500) and `ic_auto_awesome`
  (filled) are now the path data of the Material Symbols SVGs in `google/material-design-icons`
  (`symbols/web/<name>/materialsymbolsoutlined/`), fetched from raw.githubusercontent.com into a scratch folder; only
  the `d` attribute was used, wrapped in `translateY(960)` for the `0 -960 960 960` viewBox. The per-file comments and
  `assets/licenses/Apache-2.0-MaterialSymbols.md` say so.

Non-blocking, done
- **N1** `ic_check_circle` and `ic_cancel` are a plain 30% disc (the one shape drawn here, noted in the notice and the
  files) with the Material Symbols check/close mark at full tint, so one tint gives the hierarchical look.
- **N2** chip symbols are an 18dp box (scaled relative to 15) so the mark is about the size of iOS's 14pt symbol.
- **N8** age menu items: `selected` semantics and a visible check on the selected one.
- **N9** `Kicker` draws capitals but TalkBack reads the text as written.
- **200% gallery** trimmed to 2200dp (about 7000px) from 3600.
- Snapshot count in this file corrected (34).

Left as the review judged them / still open
- N4 (page hidden from TalkBack during the intro), N5, N6: accepted as is, listed above. N7 (the wordmark jumps
  back instead of gliding when the header moves mid-move): not changed.
- N10: `save()` double persist, `rememberNavController` inside the gate branch (check "Don't keep activities" once
  phase 7 lands), disabled primary button in dark mode: not changed.
- Still needing a device: splash alignment and fade on API 29, 31+ and 34+, TalkBack, rotation, download states.

## Review round 2 fixes
- **R2-B1.** `RootViewModel` no longer cancels the first `refresh()` at the ceiling: the check runs in its own job,
  the 2 s wait only decides when `availabilityChecked` flips, and `downloadIfNeeded()` waits for the check to finish,
  so it never acts on the provider's placeholder. `StubModelAvailabilityProvider` got a `refreshResult` (what the late
  answer says). New/extended tests: after the ceiling `availabilityChecked` is true with `downloadCount == 0`, and one
  download starts once a slow check answers `Downloadable`; a slow check that answers not-eligible shows not-eligible
  and never downloads; a slow check that answers `Available` never downloads.
- **R2-N1** `ScrollingScope` is `@LayoutScopeMarker`. **R2-N2** a spacer's maximum is clamped to at least its minimum.
- **R2-N3** `DottedLeaderRow` handles unbounded width (natural width, leader at its minimum) and its 8dp rise is
  `scaled(relativeTo = 17f)`. Only the 200% gallery pixels changed (the dots sit a little higher); I re-recorded the
  two files and opened the light one. The 1% snapshot tolerance had let that shift pass verify.
- **R2-N4** the `Theme.Leo.Starting` comment now describes the wordmark icon.
- Still open from round 2: the device checks of the splash (API 29-30 centering behind the status bar, API 31+ and 34+
  mask and size against the intro's first frame), TalkBack after the intro, rotation, download states.

## Commands run after the fixes (`--rerun-tasks`)
Round 1 fixes, all with `--rerun-tasks --no-build-cache` (every task executed); the round 2 run is below:
- `./gradlew assembleDebug`: BUILD SUCCESSFUL (42 tasks executed). Also run: `assembleRelease assembleDebugAndroidTest`, successful.
- `./gradlew :domain:test testDebugUnitTest`: BUILD SUCCESSFUL (42 tasks executed, Roborazzi verify of the re-recorded images).
- `./gradlew detekt ktlintCheck lintDebug`: BUILD SUCCESSFUL (55 tasks executed). Lint first failed `VectorRaster` on the
  288dp splash icon; suppressed in that file with the reason.

Round 2, all with `--rerun-tasks`:
- `./gradlew assembleDebug`: BUILD SUCCESSFUL (42 tasks executed).
- `./gradlew :domain:test testDebugUnitTest`: BUILD SUCCESSFUL (42 tasks executed; snapshot verify on).
- `./gradlew detekt ktlintCheck lintDebug`: BUILD SUCCESSFUL (55 tasks executed).

## PR #9 review fixes

- **Launch intro stuck on a cold start (blocking):** `LaunchedEffect(plays)` captured the first `isReady`, so a launch where AICore hadn't answered yet never left the launch frame. It now reads `rememberUpdatedState(isReady)` (`LaunchIntroOverlay.kt`). New `LaunchIntroHostTests.introEndsOnceReadyTurnsTrueAfterTheFirstFrame` fails on the old code and passes now.
- **Download on resume:** `RootViewModel.onResume()` calls `downloadIfNeeded()` after its refresh, so a model that became downloadable while the app was away (after a failed download, say) is downloaded instead of leaving an indeterminate bar. Tests `downloadsOnResumeWhenTheModelBecameDownloadable`, `resumeDoesNotDownloadAnAvailableModel`.
- **No state write during composition:** the host sets `target.isHidden` in a `SideEffect`, and the header reads it in `graphicsLayer` (the draw phase), so it is still hidden on the first frame.
- **Download scope (accepted, not changed):** the download collection runs in `viewModelScope`, so leaving the app from "Getting Leo ready" stops Leo's collection. AICore keeps downloading on its side, and the next launch or resume picks up its progress.
- **Second PR review:** `hasPlayed` is now set when the intro finishes, not when it starts, so a recreation during the up-to-2 s hold waits for `isReady` again instead of showing "Getting Leo ready" (test `LaunchIntroHostTests.introCountsAsPlayedOnlyOnceItFinishes`). The download bar's pulse is read in `graphicsLayer`, so it no longer recomposes every frame. D3 now describes the duplicate `downloadIfNeeded()` at start.
- **Touch targets (open, for the user):** chips, age segments (about 34dp tall) and the secondary/ghost `LeoButton` (44dp) are below Android's 48dp minimum touch target. Compose still extends their hit area to 48dp when nothing else is closer, but Accessibility Scanner flags the node bounds. Kept at the iOS sizes until the user decides.
- **Third PR review:** corrected the `onResume()` KDoc (a second `download()` waits for the running one to end, it doesn't return at once). Back stack after process death (N10) stays a device check for phase 7, with "Don't keep activities" on, once History is a real destination.
- **Fourth PR review:** a resume while the last resume's check is still running (waiting on a download) starts no second one; saves are serialized behind a `Mutex` and re-check equality inside it, so two quick equal saves persist once (closes N10's save half; iOS saves synchronously, so it has no such race); the intro's `Done` delay reads `reduceMotion` through `rememberUpdatedState`.
- **Fifth PR review:** the host reads the header's frame through `snapshotFlow` in an effect that runs only while the intro runs, so a header that moves after it (scrolling, phase 6) no longer recomposes `LaunchIntroHost`. New test `resumeWhileTheLastResumeWaitsOnADownloadStartsNoSecondCheck` (the stub's `download()` can now wait on a gate).
- **Sixth PR review:** the age segments are a `selectableGroup()`, so TalkBack reads them as one group; the pixels left by dividing the row go to the last segment, so the control spans the full width.
