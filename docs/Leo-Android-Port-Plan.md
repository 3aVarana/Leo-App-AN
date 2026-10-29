# Leo Android port plan

Goal: full parity with Leo iOS (about 5,700 lines of Swift in `../Leo/Leo`, plus its tests), on Gemini Nano through ML Kit's Prompt API, offline and private like the original.

Each phase is run with `/port-phase <n>`, ends green (build, unit tests, lint) and ships as its own pull request. The iOS files listed are the specification for that phase, together with their tests in `../Leo/LeoTests/`.

| # | Phase | iOS source | Done when |
|---|---|---|---|
| 0 | Foundation | `Features/Theme/Theme.swift`, `Resources/Fonts/`, `.swiftlint.yml` | `:domain` module exists; approved deps in the version catalog; detekt, ktlint, Lint and Roborazzi wired; Source Serif 4 + Broadsheet colors/type (light, dark) in `theme/`; theme snapshot test passes; no `INTERNET` permission |
| 1 | Domain | `Domain/*.swift` (13 files) | Every type ported to `:domain`; every test in `LeoTests/Domain/` ported and passing |
| 2 | Storage | `Repositories/PreferencesRepository.swift`, `RoundHistoryRepository.swift`, `SwiftData/RoundHistorySchema.swift` | DataStore preferences with the same JSON shape and defaults (`decodeIfPresent` → default values); Room history; ported `Data/` tests pass |
| 3 | On-device model | `Data/FoundationModels/*`, `ExerciseRepository`, `TopicReviewRepository`, `ModelAvailabilityProvider` | `@Generable` exercise and topic review; repair + validation rules; prompts ported verbatim with golden tests; availability + download handling; live tests pass on a supported device (or are left for the user) |
| 4 | App shell | `App/MyApp.swift`, `Features/Root/*`, `Features/Components/*` | `AppContainer`, launch intro, unavailable screen, navigation graph, every shared component with snapshots |
| 5 | Preferences | `Features/Preferences/*` | Onboarding, topics editor (with custom-topic review), settings, texts-per-round stepper; ViewModel tests + snapshots |
| 6 | Quiz | `Features/Quiz/*` | Welcome, loading, read-then-answer exercise with reading timer, generation failed, results, review; background generation and up to 3 replacements; ViewModel tests + snapshots |
| 7 | History | `Features/History/*` | Grouped by week/month, round detail, swipe to delete, filtered by age group |
| 8 | Polish | `Localizable.xcstrings`, accessibility rules in iOS `CLAUDE.md` | es and pt-BR complete; TalkBack, 200% font, dark mode, high contrast, tablets and landscape checked; README written |
| 9 | Exercise quality on Nano | `scripts/exercise-eval/`, `docs/Leo-Exercise-Quality-Plan.md` | Eval harness ported as an instrumented runner; before/after table for any prompt change tuned for Gemini Nano |

## Known differences to decide during the port

- **Device support.** Gemini Nano runs on a device allowlist through AICore, not on emulators. The unavailable screen must explain this the way iOS explains Apple Intelligence.
- **Model download.** `DOWNLOADABLE` needs the app to start the download and show progress; iOS waits for the system.
- **Locale phrase.** iOS starts instructions with Apple's "The person's locale is es_ES." Whether that helps Gemini Nano is measured in phase 9, not assumed.
- **Content languages.** Gemini Nano's supported languages may differ from Apple's; `ContentLanguage` falls back to English for anything unsupported.
