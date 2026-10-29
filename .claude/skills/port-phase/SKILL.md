---
name: port-phase
description: Port one phase of Leo from iOS to Android end to end (plan, approval, implement, review, QA, learning note). Use when the user runs /port-phase <n> or asks to port the next phase.
---

# Port a phase of Leo to Android

Argument: the phase number from `docs/Leo-Android-Port-Plan.md`. With no argument, pick the first phase without a `qa.md` marked all PASS in `.claude/workflow/`.

1. **Prepare.** Check `git status` is clean and earlier phases are merged; if not, tell the user and stop. Create branch `port/phase-<n>-<slug>` (ask before creating it). Save the base commit in `.claude/workflow/phase-<n>/baseline.sha`.
2. **Plan.** Run the `port-planner` agent for phase `<n>`. Read its `plan.md` yourself.
3. **Approve.** Show the user a short summary of the plan and its open questions, and ask them (AskUserQuestion when there are choices). Record the answers under "Decisions" in `plan.md`. **Don't continue without the user's approval.**
4. **Implement.** Run the `android-implementer` agent with the approved plan.
5. **Review.** Run the `parity-reviewer` agent. If it reports Blocking findings, send them back to `android-implementer`, then review again. After two rounds with Blocking findings left, stop and show them to the user.
6. **QA.** Run the `android-qa` agent. A FAIL goes back to step 4 once; a second FAIL goes to the user.
7. **Teach.** Write `docs/learning/phase-<n>.md` (rules in `CLAUDE.md` → Teaching).
8. **Report.** Tell the user in a few lines: what was ported, deviations from iOS, QA status (including what's NOT RUN and why), and the learning note. Offer to commit and open the pull request; do it only when they confirm.
