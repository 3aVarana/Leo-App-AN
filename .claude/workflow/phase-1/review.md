# Phase 1: Domain, review

Reviewer: independent pass over the uncommitted working tree on `port/phase-1-domain`, diffed against `05192842` (`baseline.sha`). Spec: `../Leo/Leo/Domain/*.swift`, `../Leo/LeoTests/Domain/*`, `LeoTests/Support/Fixtures.swift`, `Leo/Localizable.xcstrings`. The plan's "Decisions" section was treated as authoritative (lowercase UUIDs, 24 deferred tests, explicit `TopicNames`, labels in `:app`).

## What was checked

- **All 13 Swift files** side by side with their Kotlin counterparts: raw values, ranges, `acceptedWordCount` integer math, wpm table, `readingTime` formula and 30 s floor, `Exercise` precondition, `RoundRecord` id-only equality, `RoundSettings` 3..9 / 6, `ReaderPreferences` defaults, limits 3 / 20, topic length 2..60, decoding (required `ageGroup`, optional rest, clamp on decode and again in `roundSettings`), `enabledTopics` order and top-up, `match` (suggested first, then custom), `check` order, and every mutation edge case (`setEnabled(true)` with no entry, empty set left in place, `resetSuggestedTopics` removes the key, `removeCustomTopic` in another group, `insertTopic` rejecting an enabled duplicate). All match.
- **Topic list:** a script parsed both `Topic.swift` and `Topic.kt`: 85 entries, identical id, English name, prompt and kind, in the same order per group.
- **Goldens:** `savedFormat.1.json` and `savedIds.1.txt` are byte-identical to the iOS files.
- **Strings:** a script mapped every `values/strings.xml` entry back to its `Localizable.xcstrings` key (`%1$d`/`%2$d` ↔ `%lld`) and compared en / es / pt-BR values: 96 strings per language, 0 mismatches. En dash, middle dot and apostrophe escaping are correct.
- **Tests:** every iOS test in `LeoTests/Domain/` is either ported with the same cases (`AgeGroupTests`, `WordCountTests`, `RoundRecordTests` + `RoundSummaryTests`, `ReaderPreferencesTests`, `ReaderPreferencesTopicEditingTests`, `DefaultTopicsTests`, `ExerciseTests.nonRandomInitKeepsValues`), moved to `DomainLabelsTests` (label tests, per D2), or listed by name in `changes.md` as deferred to phase 3 (19 + 2 + 3 = 24, names verified against the Swift file).
- **Module purity:** no `android.*`, `java.awt` or `println` under `domain/src`.
- **Commands run by the reviewer:**
  - `./gradlew assembleDebug :domain:test testDebugUnitTest detekt ktlintCheck lintDebug --continue --console=plain -q`: pass, no output.
  - `./gradlew :domain:test testDebugUnitTest`: pass. Test results: `:domain` 77 tests, `:app` 32 tests, 0 failures, 0 skipped.
  - `./gradlew :domain:dependencies --configuration runtimeClasspath`: kotlinx-serialization-json 1.11.0, stdlib 2.2.10 → 2.3.20.

## kotlinx-serialization 1.12.0 → 1.11.0

**Justified.** Verified in the Gradle cache: `kotlinx-serialization-core-jvm` 1.12.0's POM / module metadata requires `kotlin-stdlib` 2.4.10, and 1.11.0 requires 2.3.20. The project compiles with Kotlin 2.2.10, which reads metadata only up to one minor version ahead (2.3), so 1.12.0 cannot be compiled against and 1.11.0 is the newest that works. The alternative, raising Kotlin to 2.4.x, would also move the KSP, Compose compiler and AGP alignment and is out of scope for a domain phase. 1.11.0 is the same approved library. Two follow-ups:

- CLAUDE.md pins approved dependencies "at the versions in `gradle/libs.versions.toml`", so the user should approve this edit explicitly when they confirm the commit. `changes.md` (deviation 1) already says why. The summary report should name it too.
- Add a comment next to `kotlinxSerialization` in `libs.versions.toml` ("1.12+ needs Kotlin 2.4") so a later dependency bump doesn't bring it back by accident.

## Findings

### Blocking

None.

### Non-blocking

1. **Fixture date differs from iOS.** `domain/src/testFixtures/kotlin/com/victorarana/leo/domain/Fixtures.kt:96` defaults `finishedAt` to `Instant.ofEpochSecond(0)` (1970-01-01). iOS uses `Date(timeIntervalSinceReferenceDate: 0)` (2001-01-01T00:00:00Z) at `LeoTests/Support/Fixtures.swift:73`. Nothing reads it yet, but History snapshots and date-formatting tests in later phases will. Fix: `Instant.parse("2001-01-01T00:00:00Z")`.

2. **`null` values are rejected where iOS uses the default.** `domain/src/main/kotlin/com/victorarana/leo/domain/ReaderPreferencesSerializer.kt:14-18`. Swift's `decodeIfPresent` (`ReaderPreferences.swift:24-31`) treats `"isReadingTimerOn": null` like a missing key. kotlinx throws on `null` for a non-null property with a default. iOS never writes `null`, so this is an edge case. Fix: add `coerceInputValues = true` to `LeoJson`. `ageGroup` has no default, so an unknown or `null` value still throws, as on iOS. Add a test to `ReaderPreferencesSerializationTests` for `{"ageGroup":"9-11","isReadingTimerOn":null,"exercisesPerRound":null}`.

3. **Case folding is narrower than Foundation's.** `domain/src/main/kotlin/com/victorarana/leo/domain/TextMatching.kt:12-13`. `.caseInsensitive` (`ReaderPreferencesTopicEditing.swift:42`) uses full case folding ("straße" == "STRASSE"). `lowercase(Locale.ROOT)` doesn't. It is unlikely to matter for en / es / pt-BR topic names. Cheapest fix: `.uppercase(Locale.ROOT).lowercase(Locale.ROOT)`. Alternative: `java.text.Collator.getInstance(Locale.ROOT).apply { strength = Collator.PRIMARY }.equals(a, b)`, which also ignores accents and is the Android-idiomatic option.

4. **Word count misses supplementary-plane letters.** `domain/src/main/kotlin/com/victorarana/leo/domain/WordCount.kt:18`. `Char.isLetterOrDigit()` is false for each half of a surrogate pair, so a segment made only of CJK Extension B characters (or similar) isn't counted. Swift's `.byWords` counts it. Fix: `substring(start, end).codePoints().anyMatch(Character::isLetterOrDigit)`. Separately, the plan's instrumented check of Android's ICU `BreakIterator` against `WordCountTests` belongs in phase 3. Note it in that plan's checklist.

5. **`characterCount` depends on JDK ≥ 20 for extended grapheme clusters.** `TextMatching.kt:16-23`, test `TextMatchingTests.kt:29` (family emoji). This works today only because `gradle/gradle-daemon-jvm.properties` pins JDK 21 and `:domain` has no separate test toolchain. Add a one-line comment, or `kotlin { jvmToolchain(21) }` for the test task, so a later toolchain change can't silently break it.

6. **`ResourceTopicNames` holds whatever `Context` it is given.** `app/src/main/java/com/victorarana/leo/features/components/ResourceTopicNames.kt:14-16`. Once a ViewModel holds it (phase 5), an Activity context leaks across rotation. Fix: store `context.applicationContext`. Also check in phase 5 that a per-app language change (Android 13+ `LocaleManager`) reaches the resources it reads.

7. **Vacuous assertion.** `domain/src/test/kotlin/com/victorarana/leo/domain/ReaderPreferencesTests.kt:311` has `assertNotNull(ReaderPreferences.TOPIC_NAME_LENGTH)`, which can't fail. `checkLengthBoundaries` already asserts `2..60`. Remove the line, or make it `assertEquals(2..60, ...)`.

8. **`roundSettings` test drops one iOS expectation.** `ReaderPreferencesTests.kt:276-285` vs `ReaderPreferencesTests.swift:159-160` (`copy.roundSettings == preferences.roundSettings` before the change). It is trivial with an immutable copy. Add `assertEquals(preferences.roundSettings(names), preferences.copy().roundSettings(names))` for case parity.

9. **Duplicated defaults.** `ReaderPreferencesSerializer.kt:27-33` repeats `ReaderPreferences`' defaults (`emptyMap()`, `true`, `DEFAULT_EXERCISE_COUNT`). If one side changes, decoding drifts silently. Consider a `const val DEFAULT_READING_TIMER_ON = true` used by both. `ReaderPreferencesTests.decodesVersion1Data` does catch the drift today.

10. **The test uses a transitive dependency.** `app/src/test/java/com/victorarana/leo/features/components/DomainLabelsTests.kt:6` imports `androidx.test.core.app.ApplicationProvider`, which arrives only through Robolectric / compose-ui-test. Prefer `org.robolectric.RuntimeEnvironment.getApplication()`, which comes from a declared dependency.

## Architecture and idiom

- Layering is as in CLAUDE.md: `:domain` is pure JVM. Labels and the resource-backed `TopicNames` sit in `:app` `features/components/`. Nothing imports ML Kit or Room.
- The Kotlin is idiomatic: immutable `data class` with copy-returning extension functions (D6), `sealed interface` for Swift enums with payloads, `IntRange`, `kotlin.time.Duration`, a `fun interface` for `TopicNames`, a custom `KSerializer` over a private surrogate to reproduce `init(from:)`. Exposing `@StringRes` properties alongside the `Context` functions is the right shape for Compose (`stringResource(group.summaryRes, …)`).
- Naming the extension `check` collides visually with `kotlin.check`, but it resolves correctly and `changes.md` notes it. Acceptable for side-by-side parity with Swift.
- Not applicable this phase: lifecycle, coroutines, Compose UI, TalkBack, snapshots.

## Verdict

**Approve.** No blocking issues. Parity with the Swift domain, its tests, the topic list, both goldens and all three string tables is exact, and build, tests and lint pass. The serialization downgrade is technically required; the user should sign off on it explicitly. Findings 1 and 2 are worth fixing before merge, since they are cheap and affect parity. The rest can wait for the phases that touch that code.
