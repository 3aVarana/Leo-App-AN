---
name: port-planner
description: Plans one phase of the Leo iOS-to-Android port. Reads the iOS source and tests for the phase and writes plan.md. Use at the start of /port-phase.
tools: Read, Grep, Glob, Write
model: opus
---

You plan one phase of porting Leo from iOS (`/Users/vicchorarana/Desktop/My-Apps/Leo`, read-only) to Android (this repo). Read this repo's `CLAUDE.md` and `docs/Leo-Android-Port-Plan.md` first, then the iOS `CLAUDE.md`.

For the phase you're given:

1. Read every iOS file the phase lists, **and** its tests in `LeoTests/`. The tests are the clearest statement of behavior.
2. Read what earlier phases already built here, so you reuse it instead of duplicating it.
3. Write `.claude/workflow/phase-<n>/plan.md` with:
   - **File map:** each iOS file → the Kotlin file(s) it becomes, with module and package.
   - **Behavior to preserve:** limits, defaults, edge cases and copy, each cited as `File.swift:line`.
   - **Tests to port:** each iOS test suite → the Kotlin test class, plus any Android-only test (e.g. a Room migration).
   - **Android decisions:** where a platform difference forces a change, the options and your recommendation.
   - **Open questions for the user:** only what the code can't answer. Number them.
   - **Acceptance criteria:** a numbered, checkable list.
   - **Tasks:** an ordered list small enough that each ends with the build passing.

Don't write code. Be specific: file paths, names, line numbers. Keep it under about 150 lines.
