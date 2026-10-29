# Phase 0: Foundation, iOS → Android

**Layer rules → Gradle modules.** iOS keeps `Domain/` free of SwiftUI with SwiftLint custom rules, which check text. Here [domain/build.gradle.kts](../../domain/build.gradle.kts) applies only `kotlin("jvm")`, so Android classes aren't on its classpath and an import of one won't compile. The rules detekt still enforces inside `:app` live in [config/detekt/detekt.yml](../../config/detekt/detekt.yml) (`ForbiddenImport` under `features/`).

**Package.resolved → version catalog.** [gradle/libs.versions.toml](../../gradle/libs.versions.toml) names every library and plugin once, and modules refer to them as `libs.x`. Plugins are declared `apply false` in the root [build.gradle.kts](../../build.gradle.kts) and applied per module. Gradle runs on its own JVM, pinned in `gradle/gradle-daemon-jvm.properties` (21 here, because detekt 1.x crashes on 25). Xcode bundles its toolchain; Gradle doesn't.

**Colorsets → Kotlin values + CompositionLocal.** Asset catalogs resolve light, dark and high contrast for you. In Compose, [Theme.kt](../../app/src/main/java/com/victorarana/leo/features/theme/Theme.kt) holds plain `LeoColors` values, and `LeoTheme {}` picks one set and provides it through `LocalLeoColors`, the Compose equivalent of a SwiftUI `@Environment` value. Views read `LeoTheme.colors.ink`. The choice of set is a pure function (`leoColors`), so a JUnit test covers every combination without a device.

**Accessibility traits → system services you observe.** iOS updates `colorSchemeContrast` in the environment automatically. Android exposes contrast only through `UiModeManager` (API 34+), and changing it is not a configuration change, so `rememberSystemContrast()` registers a listener in `DisposableEffect`. `DisposableEffect` is Compose's `onAppear`/`onDisappear` pair: its `onDispose` block removes the listener. Code that needs API 34 is guarded with `Build.VERSION.SDK_INT` because minSdk is 29.

**Dynamic Type → `sp`.** iOS scales each style along its own curve (`relativeTo:`). Android has a single `fontScale`, applied to every `sp` value. [LeoTextStyle.kt](../../app/src/main/java/com/victorarana/leo/features/theme/LeoTextStyle.kt) uses `sp` for size and line height and `em` for tracking. The fixed launch wordmark converts `dp` to `sp` so it never grows.

**`UIAppFonts` → `res/font/`.** Resource file names must be lowercase with underscores (`source_serif_4_semibold.otf`), and you reference them through the generated `R.font` class rather than by a font name string. There's no Info.plist registration step.

**Info.plist → manifest merging.** Every library ships its own manifest, and the build merges them into one. [AndroidManifest.xml](../../app/src/main/AndroidManifest.xml) uses `tools:node="remove"` to strip `INTERNET` even if ML Kit adds it later. The `verify<Variant>NoInternetPermission` task in [app/build.gradle.kts](../../app/build.gradle.kts) checks the *merged* result, because that's what ships.

**Backups are opt-out.** iOS backs up the app container to iCloud unless you exclude files. Android also backs up by default. `data_extraction_rules.xml` (API 31+) separates `<cloud-backup>` from `<device-transfer>`, and `backup_rules.xml` covers API 29–30.

**Launch screen → window background.** Android has no LaunchScreen storyboard. `android:windowBackground` in `res/values/themes.xml` and `values-night/themes.xml` paints the Broadsheet color before Compose draws its first frame. `values-night/` works like an asset's dark appearance, applied to a whole resource folder.

**Simulator snapshots → JVM snapshots.** Roborazzi renders Compose on the JVM through Robolectric, a fake Android framework, with no emulator. Robolectric resource qualifiers (`w390dp-h844dp-xxhdpi`) play the role of the iOS device config, and `DeviceConfigurationOverride` switches dark mode and font scale the way the system would. See [SnapshotHelpers.kt](../../app/src/test/java/com/victorarana/leo/support/SnapshotHelpers.kt). Reference images are in `app/src/test/snapshots/`, and `testDebugUnitTest` compares against them.
