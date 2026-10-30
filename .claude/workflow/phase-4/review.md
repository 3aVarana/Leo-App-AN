# Phase 4: App shell, review

Reviewed against baseline `359e24b` plus untracked files on `port/phase-4-app-shell`. I read every ported file next to its Swift original (`Leo/App/MyApp.swift`, `Leo/Features/Root/*`, `Leo/Features/Components/*`) and compared the tests with `LeoTests/Features/{LaunchIntro,ProgressDots,RootViewModel}Tests.swift` and the three iOS snapshot suites. I opened all 34 new PNGs under `app/src/test/snapshots/` (light, dark and 200%; I cropped the tall gallery images to read them) and compared them with `../Leo/LeoTestsSnapshots/`.

Commands: `./gradlew :domain:test testDebugUnitTest detekt ktlintCheck lintDebug` finished with BUILD SUCCESSFUL (tasks were up to date from the implementer's run, so nothing new was executed). No device, so I didn't check runtime behavior.

## Blocking

### B1. `TopOffset` doesn't cap at its height, so every `ScrollingScreen` page sits in the vertical middle
`app/src/main/java/com/victorarana/leo/features/components/ScrollingScreen.kt:84`. iOS: `ScrollingScreen.swift:25-31` (`Spacer(minLength: 24).frame(maxHeight: height)`).

`Spacer(Modifier.weight(1f).heightIn(min = 24.dp, max = height))`: a `weight(1f)` child (fill = true) gets its share of the space as a fixed constraint (min = max = share), and `heightIn` can't go below an incoming minimum. So `max = 140.dp` does nothing, and the top spacer always takes half the free space. The snapshots show it. In `UnavailableScreenSnapshotTests/device-not-eligible-en.png` the icon is about 275dp from the top and the gap below the text is the same size. In iOS `deviceNotEligible.device-not-eligible-en.png` the icon is at the safe area plus 140. Every state and both themes look the same. Every phase-6 screen (Welcome, Loading, Result, Error) uses `TopOffset`, so this spreads if it isn't fixed now.

Fix: `Spacer(Modifier.weight(1f, fill = false).heightIn(min = TopOffsetMinimum, max = height))`. That gives `min(share, height)`, the same as SwiftUI's least-flexible-first split. Re-record the Unavailable snapshots and check that the icon lands 140dp below the top inset.

### B2. `Dp.scaled()` doesn't follow the text style, so dots, icons and min heights barely grow
`app/src/main/java/com/victorarana/leo/features/theme/Accessibility.kt:72`, used in `ProgressDots.kt:73`, `RoundResultDots.kt:33`, `AnswerRow.kt:79-83`, `AgeRow.kt:35-36`, `TopicChip.kt:106`, `HistoryButton.kt:49,72`, `LeoButton.kt:57-58`, `UnavailableScreen.kt:98`. iOS: `@ScaledMetric(relativeTo: .footnote)` in `ProgressDots.swift:47-48`, plus the other `@ScaledMetric`s.

`value.sp.toDp()` uses Android 14's nonlinear font curve at the size of the *dimension*. That curve barely grows large sp values. A 28sp or 36sp "size" stays about the same, while the 13sp/15sp numbers inside grow about 1.46x. Measured in the snapshots, the regular dots are 28dp at 1x and 29dp at 200%, and the large dots are 36dp at both. The plan's D5 says 52.6 = 36 x 1.46, which these images don't show. The result shows in `gallery-fontScale2-*.png` and `dots-narrow-fontScale2-*.png`: the numbers crowd their circles, and the regular row is clearly smaller than the large row. In iOS `dots-narrow-ax-*.png` both rows fill the narrow frame at the same shrunken side, with room around the digits. `@ScaledMetric(relativeTo:)` grows a size by the ratio its text style grows.

Fix: scale relative to a text size, e.g. `fun Dp.scaled(relativeTo: TextUnit): Dp = this * (relativeTo.toDp().value / relativeTo.value)` inside the composable density. Pass the iOS `relativeTo` size of each call site (footnote 13 for the dots, body 17 for the answer icon, title2 22 for the age check, subheadline 15 for the chips and history text button, title3 20 for the history icon, headline 17 and subheadline 15 for the button heights, largeTitle 34 for the Unavailable icon). Re-record the components and Unavailable snapshots. Optionally add a JVM test that the large dot's ideal at 200% equals 36 x 1.46 within a pixel (the port of `ProgressDotsTests.swift:18`).

### B3. Cold launch on a supported device can flash "Getting Leo ready"
`app/src/main/java/com/victorarana/leo/features/root/RootScreen.kt:33`, `RootViewModel.kt:63`, `GenAiModelAvailabilityProvider.kt:68`. iOS: `RootViewModel.swift:32-36` (availability is synchronous, so the intro always reveals the right screen).

`isReady = availability !is Available || preferences !is Loading`. The provider *starts* at `Downloading(fraction = null)` until the first `checkStatus()` returns, so `isReady` is true from the first frame. If AICore's first status call takes longer than the 0.3 s hold (binding to AICore on a cold start can), the intro raises the Unavailable "Getting Leo ready" page, and a moment later it cuts to onboarding or the app. That is the flash D3 was written to prevent, just with a different screen. `RootScreenTests` can't catch it because the stub starts at `Available`.

Fix: tell "not checked yet" apart from "downloading". For example, `RootViewModel` runs `availabilityProvider.refresh()`, then sets an `availabilityChecked` flag in `RootUiState`, then calls `prepare()` (or the provider exposes a `hasChecked` StateFlow). `isReady` then requires `availabilityChecked && (availability !is Available || preferences !is Loading)`. Consider a ceiling (e.g. 2 s) so a stuck AICore still shows the Unavailable screen. Add a `RootViewModelTests` case with a stub whose `refresh()` suspends.

### B4. Splash: the user's decision was "no visible cut", and there is one
`app/src/main/res/values/themes.xml` (`Theme.Leo.Starting`), `app/src/main/res/drawable/splash_icon.xml`, `MainActivity.kt:28`. Plan "Decisions" Q5: *"hand off to the launch intro's first frame without a visible cut"*.

The implementer flagged this honestly (changes.md, Splash). Still, the user approved a new dependency specifically to avoid the cut, and the result is the plan's option (a): bare ground, then "Leo" pops in on the intro's first frame. This needs the user's explicit acceptance, or option (b) now: a vector of the Source Serif 4 "Leo" glyphs (outlines exported from the bundled font) as `windowSplashScreenAnimatedIcon`, sized and centered to match `LeoTextStyle.launchWordmark` at font scale 1. The intro centers the wordmark in the full window, which is where the system draws the icon. At other font scales a small size jump stays, so record that as a limitation. Until the user decides, don't merge as "done per Q5".

### B5. The dotted-leader row in the gallery is broken at both sizes (recorded and committed as the reference)
`app/src/test/java/com/victorarana/leo/snapshots/ComponentsSnapshotTests.kt:168-181` (and `LEADER_WEIGHT = 0.001f` at `:53`). iOS: `ComponentsSnapshotTests.swift:39-45`, `DottedLeader.swift:22-29`.

At 1x (`gallery-en.png`, `gallery-dark-en.png`) the leader is a single dot and "Correct" sits right after the title instead of at the trailing edge. At 200% the row breaks the title onto two lines and leaves an empty gap with no dots. In iOS `gallery.gallery-en.png` the dots fill from the title to "Correct", which sits at the trailing edge. Cause: the title's `weight(1f, fill = false)` takes about 99.9% of the free space as unused allowance, the leader's 0.001 weight gets about 0px, and `widthIn(min = 20.dp)` can't raise a weighted child's fixed width (same mechanism as B1). The component itself is fine. The test row is wrong, and it is the pattern phase 7's Results screen will copy.

Fix: a small `Layout` for the row: measure the status, then the title with `max - status - 2 x gap - 20dp` (title keeps priority, like `layoutPriority(1)`), then the leader with the rest. Or keep the Row: title without weight but measured with that max via `widthIn(max = ...)` from `BoxWithConstraints`, and leader `Modifier.weight(1f)`. Re-record and compare with the iOS image.

## Non-blocking

### N1. Icons are filled glyphs, not SF Symbols' hierarchical rendering
`AnswerRow.kt:80-85`, `AgeRow.kt:58`, `HistoryButton.kt:46-51,69-74`, `UnavailableScreen.kt:95-103`. iOS: `.symbolRenderingMode(.hierarchical)` in `AnswerRow.swift:35`, `AgeRow.swift:29`, `HistoryButton.swift:18`, `UnavailableView.swift:14`.

In iOS the check and x circles are a light tinted disc with a full-color mark. Android draws a solid disc with the mark knocked out. It reads heavier, and in dark mode it is the opposite tone (compare `gallery-dark-en.png` B row and "9 to 11" with iOS `gallery.gallery-dark-en.png`). Recommended before phase 6 snapshots: two-path vectors (disc path with `fillAlpha` around 0.3, mark path at full alpha), so one tint gives the hierarchical look.

### N2. Chip symbols look smaller than on iOS
`TopicChip.kt:106,117,123`. iOS: `TopicChip.swift:62,90` (a 14pt *font* symbol, medium weight). A 14dp box holding a 24-unit Material glyph that uses about 14 of its 24 units draws a mark of about 8dp. iOS's checkmark/plus/xmark at 14pt are about 12-14pt tall. Visible in the gallery. Size the box so the glyph matches (about 20dp, scaled per B2), or crop the viewport.

### N3. Icon provenance and notice
`res/drawable/ic_*.xml` comments say "Material Symbols glyph". The path data is the classic **Material Icons** (filled, baseline) set, typed from memory rather than copied (changes.md, Q7). The shapes match what I know of those icons, and they render recognisably in the snapshots: sparkles, history clock, check, x, add, check circle, cancel circle, unfold. The notice (`assets/licenses/Apache-2.0-MaterialSymbols.md`) names both families, credits Google LLC and includes the full Apache 2.0 text, so it is accurate in substance. Still: (a) fix the per-file comments to say "Material Icons (filled)"; (b) Q7 said "copied", so either replace the files with the ones from `google/material-design-icons` (the repo the notice cites) or tell the user they were reproduced by hand; (c) consider renaming the notice to `Apache-2.0-MaterialIcons.md`.

### N4. Page hidden from TalkBack during the intro (implementer-flagged): acceptable
`LaunchIntroOverlay.kt:162`. iOS: `LaunchIntro.swift:213` only disables hit testing. On Android, TalkBack activates nodes through semantics actions, not pointer events, so the pointer-swallowing overlay alone wouldn't stop a double-tap during the intro. Clearing the page's semantics for about 1 s is a reasonable platform equivalent of "taps wait for the swap". It is listed in changes.md. Check on the device that TalkBack announces the first screen once the intro ends. If focus stays on nothing, call `requestFocus` / `sendAccessibilityEvent(TYPE_WINDOW_CONTENT_CHANGED)` on Done.

### N5. Intro cut short when the Activity is recreated in the first second (implementer-flagged): acceptable
`LaunchIntroOverlay.kt:91,126`. iOS behaves the same way: `LaunchIntro.hasPlayed` is set when `play()` starts (`LaunchIntro.swift:276`), and a re-created modifier starts at `.done` (`:177`). A rotation in the first second shows the page at rest, which is fine. After process death the intro replays, which counts as a cold launch.

### N6. `CustomTopicChip` click label instead of a hint (implementer-flagged): acceptable
`TopicChip.kt:89`. iOS: `TopicChip.swift:28`. `onClickLabel` is how Android expresses a hint ("Double tap to remove this topic"). The wording follows the iOS translations ("Quita / Remove este tema" becomes "Quitar / Remover este tema"). It is listed in changes.md.

### N7. Wordmark jumps back to the center when the header moves mid-move
`LaunchIntroOverlay.kt:235-241`. iOS: `LaunchIntro.swift:264-266` animates `headerMoved` with `reducedMotionCurve`, so the wordmark glides back while fading. It is listed in changes.md. The jump can only happen in the first 0.18 s of the move, when the wordmark has barely left the center. Fine for now, but it can be animated (keep the last translation and tween it to zero over `REDUCED_MOTION_FADE`).

### N8. Age menu items don't say which one is selected
`AgeSegmentedControl.kt:204-221`. iOS: `AgeSegmentedControl.swift:53-60` uses a `Picker`, which shows a checkmark and gives VoiceOver "selected". Add `Modifier.semantics { selected = group == selection }` (or `selectable`) on each item, and a visible check for the selected one, not only a color change.

### N9. Kicker uppercases the text TalkBack reads
`Kicker.kt:20`. iOS `textCase(.uppercase)` is visual only. TalkBack gets "LEO" / "NOW WRITING", and some voices spell short all-caps words letter by letter. Set `semantics { text = AnnotatedString(text) }` (or a `contentDescription` with the original) and draw the uppercased string.

### N10. Smaller points
- `RootViewModel.kt:72-77`: two quick `save(p)` calls with the same new value both persist, because the guard reads state before the first save lands. iOS is synchronous. Harmless today. Phase 5 can set the state optimistically, or guard on a pending value.
- `LeoNavHost.kt:18`: `rememberNavController()` is created inside the gate branch, which is composed only after preferences load. Check with "Don't keep activities" that the back stack (History) survives process death once phase 7 lands.
- `ComponentsSnapshotTests` 200% gallery is 10800px tall, and about the last third is empty ground. Trim `heightDp` so diffs stay readable.
- `changes.md:13` says 44 PNGs. There are 34.
- Disabled primary button in dark mode (`gallery-dark-en.png`, "Continue"): the label is more legible than on iOS. Probably color-profile noise between renderers, so no action unless it shows on a device.

## Verified, no findings
- Composition: `AppContainer` is all `by lazy`, with one `GenerativeModel` shared by `MlKitLanguageModel` and `MlKitModelStatusSource`. Q8 cleanup is complete. No ML Kit, Room or DataStore imports under `features/`. No network code, and no `INTERNET` permission.
- `RootViewModel`: load at init, the equal-preferences guard, the `FIFTEEN` onboarding draft, and availability combined live (`stateIn(Eagerly)`). `LifecycleEventEffect(ON_RESUME)` refresh. UI collects with `collectAsStateWithLifecycle`.
- `LaunchIntro` pure object: every timing, curve, tolerance and rule matches `LaunchIntro.swift:9-123`, including the rise converted to px for the move. All 14 iOS tests are ported with the same cases. `ProgressDotsTests` has all 6. The `RootViewModelTests` subset matches the plan, with the deferrals noted.
- Gates: unavailable, onboarding and app, crossfade only on the onboarding/app swap (`RootView.swift:41`), and a live swap on `Available` (tested).
- Components: sizes, paddings, corners, alphas, colors per state, pressed states and TalkBack nodes match the cited Swift lines (LeoButton, AnswerRow, AgeRow, chips, segmented control and menu fallback, ProgressDots/DotRow, RoundResultDots, HistoryButton, SkeletonLines, Kicker, ReviewLabel, DottedLeader). B2 is the exception.
- Strings: all new keys are in en, es and pt-BR. The copied ones match the iOS `Localizable.xcstrings` translations ("Rondas pasadas", "Repasar", "Edad", "Texto %1$d de %2$d", ...). The Gemini Nano drafts follow the iOS Apple Intelligence wording.
- Snapshots, apart from B1, B2 and B5: no clipping or overlap. Dark colors are right in every image (ground, ink, accent, magenta, dividers). The age control's 200% segments fit their labels, and the 270dp menu fallback renders. The intro's launch, reduced-motion and rest frames look right in both themes.

## Verdict

**Changes needed.** Fix B1, B2 and B5 (layout bugs that later phases would copy) and B3 (launch flash), re-record the affected snapshots, and get the user's call on B4 (accept the splash cut, or build the "Leo" splash vector).

---

# Round 2

I re-reviewed the uncommitted working tree against round 1, the updated `changes.md` ("Review round 1 fixes") and the new plan Decisions ("Review round 1 follow-ups": build the splash glyph vector this phase, copy the original Material Symbols paths). I read every changed file again and opened all 34 re-recorded PNGs. For Unavailable I used a contact sheet of all 16 variants, dark included. I cropped the tall gallery images and compared them with the iOS references.

Commands: `./gradlew :domain:test testDebugUnitTest detekt ktlintCheck lintDebug --rerun-tasks` gave BUILD SUCCESSFUL, 71 tasks executed. That includes the Roborazzi verify, and the phase 1-3 snapshots are unchanged in git.

## Round 1 findings

| Finding | Status | Evidence |
|---|---|---|
| B1 TopOffset cap | **Resolved** | All 16 Unavailable images, light and dark, put the icon 140dp below the top, with the free space below the text, as in iOS `deviceNotEligible`. The new `SpacerColumn` (`ScrollingScreen.kt:127-196`) does SwiftUI's least-flexible-first split. Assessment below. |
| B2 `scaled(relativeTo)` | **Resolved** | `Accessibility.kt:77`. Every call site uses the iOS `relativeTo` (title3 20 for the history icon, largeTitle 34, and so on). `dots-narrow-fontScale2-*.png` now matches iOS `dots-narrow-ax`: both rows shrink to the same side, with room around the digits. New test `aLargeSizeGrowsByTheRatioOfItsTextStyle` (52.6dp ±1.5). |
| B3 launch flash | **Resolved for the slow-check case, new problem after the ceiling** | `RootUiState.availabilityChecked` gates `isReady` (`RootScreen.kt:34-35`), with tests for the gate and the ceiling. What happens after the ceiling fires is **R2-B1**. |
| B4 splash cut | **Resolved per the user's follow-up**, device check still open | `splash_icon.xml` holds the Source Serif 4 Semibold "Leo" outlines on the 288dp canvas, colored with `@color/leo_ink` (light #201E1D, night #EFEDED). Its stated alignment with the launch frame is within 1px at font scale 1. The intro wordmark has a fixed size, so other font scales stay aligned too. See R2-N4 for what only a device can show. |
| B5 leader row | **Resolved** | `DottedLeaderRow` (`DottedLeader.kt:50-84`). At 1x the dots run from the title to "Correct" at the trailing edge (light and dark), as in the iOS gallery. At 200% the title wraps and the leader drops to its 20dp minimum before "Correct". The empty space after "Mountain" comes from the wrapped title taking its full measured width, which SwiftUI does too. Small notes in R2-N3. |
| Icons (Q7 follow-up) | **Resolved** | I fetched the cited SVGs from `google/material-design-icons/symbols/web/...` myself (`check_wght500`, `close_wght500`, `history_wght500`, `auto_awesome_fill1`). The `d` attributes match the drawables character for character. The `translateY(960)` wrapper correctly maps the `0 -960 960 960` viewBox. `auto_awesome_fill1_24px.svg` really is published with a 24-unit path, and the drawable uses a 24 viewport for it, which is correct. The per-file comments and the notice now say "Material Symbols" and name the one hand-drawn shape (the 30% disc). Apache 2.0 text, Google LLC credit. Accurate. |
| N1 hierarchical icons | Resolved | 30% disc plus full mark. B row, C row and "9 to 11" now read like iOS in both themes. |
| N2 chip symbols | Resolved | About iOS size in the gallery at 1x and 200%. |
| N8 menu selection | Resolved | `selected` semantics plus a visible check. |
| N9 Kicker | Resolved | TalkBack reads the text as written. |
| N4, N5, N6 | Accepted as is | Unchanged. Still listed in changes.md. |
| N7, N10 | Open, Non-blocking | Unchanged, as stated. |

## New findings

### Blocking

**R2-B1. After the 2 s ceiling, `downloadIfNeeded()` acts on the placeholder and can lock in the wrong state**
`app/src/main/java/com/victorarana/leo/features/root/RootViewModel.kt:76-80`, with `GenAiModelAvailabilityProvider.kt:68,76-82,97-110`.

`withTimeoutOrNull` *cancels* the first `refresh()`. The provider's state is then still its placeholder, `Downloading(fraction = null)`, so `downloadIfNeeded()` sees "downloading" and calls `download()`. That takes the download mutex, so the late `ON_RESUME` refresh's answer is thrown away (`refresh()` returns early or discards while `downloading.isLocked`, and the `finishedDownloads` check discards a result read before the download ended). On a slow AICore:
- On a device that isn't eligible, `client.download()` fails, and the screen shows "Gemini Nano couldn't be downloaded" (`DOWNLOAD_FAILED`) instead of the not-eligible message. It stays that way until the next resume.
- On a device where the model is already available, the app starts a download request it never needed.

The new test `availabilityCheckGivesUpAfterTheCeiling` doesn't assert `downloadCount`, so it misses this. Phase 3's `prepare()` was safe only because its `refresh()` always finished, and turned errors into `Unavailable(OTHER)`.

Fix: stop *waiting* at the ceiling without *cancelling* the check:
```kotlin
viewModelScope.launch {
    val check = launch { availabilityProvider.refresh() }
    withTimeoutOrNull(AVAILABILITY_CHECK_CEILING_MILLIS) { check.join() }
    availabilityChecked.value = true
    check.join()
    availabilityProvider.downloadIfNeeded()
}
```
Then extend the ceiling test: after the ceiling `availabilityChecked` is true and `downloadCount == 0`; after the gate opens with `Downloadable`, `downloadCount == 1`.

### Non-blocking

**R2-N1. `ScrollingScope` has no `@LayoutScopeMarker`, so its `weight`/`align` leak into nested scopes**
`ScrollingScreen.kt:84-93`. Compose's own scopes (`ColumnScope`, `RowScope`, `BoxScope`) carry `@LayoutScopeMarker`, so you can't call an outer scope's modifiers by accident. Without it, inside a `Box { }` in the screen, `Modifier.align(Alignment.CenterHorizontally)` doesn't match `BoxScope.align(Alignment)` and resolves to `ScrollingScope.align`. Inside a `Box`, `Modifier.weight(1f)` resolves to `ScrollingScope.weight`. Either way it compiles, attaches `ScrollingChildData` to a child of the Box, and does nothing. Add `@LayoutScopeMarker` to the interface before phase 6 starts using it.

**R2-N2. `shareHeights` mishandles `max < min`**
`ScrollingScreen.kt:173-187`. With a cap below the minimum (e.g. `TopOffset(height = 16.dp)`, since `TopOffset` forces min 24), `sizes[index] = maxes[index]` *shrinks* the child below its minimum, and `left -= max - size` *adds* space back. Clamp at the top: `max = maxOf(max, min)`. The algorithm is otherwise sound. A child that gets capped mid-pass uses a stale `totalWeight` for the rest of that pass, which only under-shares, and the next pass makes up for it. The lost rounding remainders (a pixel or two) are fine.

On simplicity: about 100 lines of private layout for one SwiftUI behavior is justified. With `Column`, a capped weighted spacer leaves the space it doesn't use empty, as round 1 found. Placement, `heightIn(min = viewport)`, the bottom inset (applied after the min height, so the min excludes it) and scrolling all behave. Fixed children are measured with unbounded height, which is right inside `verticalScroll`.

**R2-N3. `DottedLeaderRow` details**
`DottedLeader.kt:63-82`.
- With unbounded width, `layout(constraints.maxWidth, …)` throws, for example inside a horizontal scroll or an intrinsic measurement. Use `if (constraints.hasBoundedWidth) maxWidth else title + gaps + minLeader + status`.
- `LeaderRise = 8.dp` is fixed. iOS ties the leader to the text's last baseline (`alignmentGuide(.lastTextBaseline) { $0[.bottom] + 4 }` in the `DottedLeader.swift` preview), so at 200% the dots sit a little high relative to the larger text. Consider `rise = 8.dp.scaled(relativeTo = 17f)`, or aligning on `LastBaseline` from the title/status placeables.

**R2-N4. Splash checks that only a device can do**
`splash_icon.xml`, `themes.xml`.
- On API 31+ the platform draws `windowSplashScreenAnimatedIcon` itself, with no icon background: the 288dp canvas is masked to a 192dp circle, and some OEMs scale the icon view. Compare the "Leo" position and size with the intro's first frame by screen recording on API 31+ and 34+.
- On API 29-30 the library's own splash view is used. Check that it centers in the whole window (behind the status bar) like the edge-to-edge intro, not in the content area.
- The comment above `Theme.Leo.Starting` in `themes.xml` still says "the ground and no icon … the library can't draw text". Update it.

**R2-N5. Font license for the glyph vector: fine**
Using a few outlines of an OFL font as artwork (a wordmark) is a use of the font, not a redistribution of the Font Software, so the Reserved Font Name and derivative rules don't apply (OFL FAQ: logos and artwork made with OFL fonts are allowed). The OFL notice already ships in `assets/licenses/OFL.md`, and the drawable's comment names its source. No action.

**Phase 3 code:** no regressions. `prepare()` keeps its behavior (`refresh()` then `downloadIfNeeded()`). `downloadIfNeeded()` is an interface default the stub inherits. `GenAiModelAvailabilityProvider` changed only by losing its constructor defaults (Q8). The provider tests pass unchanged.

## Verdict (round 2)

**Changes needed, one item:** R2-B1. It's a few lines in `RootViewModel` plus a stronger ceiling test. Everything else from round 1 is resolved or accepted. R2-N1 is worth doing before phase 6 builds screens on `ScrollingScope`. With R2-B1 fixed I'd approve. The device checks (splash on API 29/31+/34+, TalkBack after the intro, rotation, download states) stay with the user.

---

# Round 3

The user asked for a full pass. I re-read the whole phase-4 change from baseline `359e24b` to the current uncommitted tree against iOS: composition root, Root (view model, screen, nav, intro, Unavailable), all 15 components, theme helpers, drawables, strings, tests, and the phase-3 files touched by the Q8 cleanup. I didn't revisit anything settled in plan.md's Decisions. Then I checked the round 2 fixes ("Review round 2 fixes" in changes.md) and opened the two re-recorded 200% gallery images, light and dark, in full-resolution crops.

Commands: `./gradlew :domain:test testDebugUnitTest detekt ktlintCheck lintDebug --rerun-tasks` gave BUILD SUCCESSFUL, 71 tasks executed, including the Roborazzi verify. No device, so the runtime checks below stay with the user.

## R2-B1: verified, resolved
`features/root/RootViewModel.kt:77-83`
- **The first refresh runs uncancelled.** `val check = launch { refresh() }` is a sibling job. `withTimeoutOrNull(...) { check.join() }` cancels only the *join* at the ceiling, never `check`.
- **`availabilityChecked` flips at the ceiling or at the answer, whichever comes first.** It's set right after the timed join, so a fast answer flips it at once and a stuck one at 2 s.
- **`downloadIfNeeded()` waits for the real answer.** The second `check.join()` comes before it, so it never sees the `Downloading(null)` placeholder.
- **ViewModel cleared mid-check:** `viewModelScope` is cancelled, which cancels the outer coroutine and its child `check`. The provider's `readStatus()` rethrows `CancellationException`, so no state is written and no download starts. That matches the phase-3 rule "a cancelled task doesn't finish the work". There is no test for it, which is acceptable: it's structured concurrency, not logic.
- **Refresh on resume:** `RootScreen`'s `ON_RESUME` fires at launch and runs a second, independent `refresh()` alongside the init check. Both write the same real status, and `GenAiModelAvailabilityProvider.refresh` (`:76-82`) drops a result while a download holds the mutex or after one has finished. So whichever answers first, the state is right. If the resume refresh answers first, the screen updates, but the intro still waits for the init check or the ceiling. That's harmless: worst case it holds up to 2 s on a stuck init call that the resume call has already answered. The download decision always comes from the init check.
- **Tests** (`RootViewModelTests.kt:110-188`): they assert the right things. The ceiling test checks `availabilityChecked` false at 1999 ms and true at 2001 ms without draining the clock. `noDownloadStartsBeforeASlowCheckAnswers` checks `downloadCount == 0` after the ceiling and `1` once a late `Downloadable` arrives. The slow not-eligible and slow `Available` cases show the real status and never download. `StubModelAvailabilityProvider.refreshResult` is a minimal, hand-written addition, no mocking.

## R2-N1 to N4: verified
- `ScrollingScope` is `@LayoutScopeMarker` (`ScrollingScreen.kt:85`).
- The maximum is clamped to the minimum (`ScrollingScreen.kt:184`).
- `DottedLeaderRow` handles unbounded width, and its rise is `scaled(relativeTo = 17f)` (`DottedLeader.kt:72-90`).
- The `Theme.Leo.Starting` comment is updated.

**200% gallery (light and dark).** The leader dots now sit on the "Correct" line, slightly higher than in round 2. The title wraps as it does on SwiftUI at accessibility sizes. Answer rows, hierarchical icons, chips, dots, age rows, history buttons, Review label, skeleton and buttons have no clipping or overlap. Every dark-theme color is correct (ground, ink, accent, magenta, 30% discs, dividers).

## Full pass: no new Blocking findings
Everything round 1 listed under "Verified" still holds. The round 1 and round 2 fixes are in place and introduce no regressions: no ML Kit, Room or DataStore imports under `features/`, no network code, no `INTERNET` permission, strings in en/es/pt-BR, and every phase-3 test passing unchanged.

## Non-blocking

**R3-N1. The 1% snapshot tolerance is looser than this project needs** (`app/src/test/java/com/victorarana/leo/support/SnapshotHelpers.kt:33`, from phase 0).
- On parity it's right: iOS uses `precision: 0.99` (`LeoTests/Support/SnapshotHelpers.swift:62`), and that also lets 1% of pixels differ.
- The KDoc's reason is wrong, though. It says "like the iOS `perceptualPrecision` of 0.99", but iOS deliberately does *not* use `perceptualPrecision` (`:55-56`).
- The tolerance is also a fraction of the *image*, so it hides more as images get taller. 1% of the 1170x6600 200% gallery is about 77,000 px. A whole 22dp answer icon (about 66x66 = 4,400 px) could disappear and verify would still pass. The round 2 leader shift actually slipped through this way.
- iOS needed slack for simulator and GPU differences. Roborazzi on Robolectric with `GraphicsMode.NATIVE` is deterministic on the JVM: phases 0-4 re-verify byte-for-byte across `--rerun-tasks`.

Recommendation: a separate, deliberate change, because it applies to every phase's snapshots. Lower `changeThreshold` to 0 (or about 0.0005 if the CI JDK or OS renders text differently) and fix the comment. It's a project-wide setting from phase 0, so the user should decide. It doesn't block this phase.

**R3-N2. Formatting leftovers** that ktlint accepts but hurt readability: `DottedLeader.kt:75-77` (`.coerceAtLeast(\n0,\n)`) and `AgeSegmentedControl.kt:~222` (`if (group ==\n selection\n)`). Join them onto one line, or pull out a `val isSelected`.

**Still open, as already listed:**
- N7: the wordmark jumps instead of gliding back when the header moves.
- N10: `save()` can persist twice; check that `rememberNavController` survives process death once phase 7 lands.
- R2-N4 device checks: splash centering on API 29-30, mask and size on API 31+/34+ against the intro's first frame, TalkBack focus after the intro, rotation, download states. These need the user's device. `adb` isn't available here.

## Verdict (round 3)

**Approved.** No Blocking findings remain. R3-N1 (snapshot tolerance) is a project-wide decision for the user, best done as its own small change. R3-N2 is cosmetic. Before merging, the user should run the listed device checks on hardware with Gemini Nano.
