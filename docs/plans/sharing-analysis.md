# pageboy — cross-app shared-code analysis

## Status: OBSOLETE — superseded by independent app implementations; all apps shipped without shared code extraction (per the standing "only share if the win is biiiiig" rule)

## Why this file exists

Same role this file plays in `whisperboy/docs/plans/sharing-analysis.md` — a placeholder for the cost/benefit memo on extracting shared code between pageboy and the siblings (especially whisperboy, since both have library + metadata + SAF folder-picker patterns; secondarily tonearmboy for the settings catalog DSL infrastructure).

The user's standing rule: **only share if the win is biiiiig — the cost of a subrepo, separate versioning, and change-coordination overhead is real, and "shared" must clear that bar comfortably.** Default verdict at every checkpoint until proven otherwise: **do not share**.

## When to revisit

Revisit this file after pageboy ships Phase A scaffold (main.md A.0–A.8 all ticked). At that point:

- The SAF folder-library code in pageboy will mostly be a port of whisperboy's `data/library/` (`CachedDocumentFile`, `LibraryRepository`, `SafLibraryScanner`, `PersistedUriPermissionStore`, `LibraryRescanCoordinator`). That's the largest single candidate for extraction. Worth a real cost/benefit pass at that point, against the five criteria below.
- The settings catalog DSL (the four `SettingsCardDsl.kt` / `SettingsCatalog.kt` / `SettingsPagesRender.kt` / `SettingsSearchScreen.kt` files, plus the per-section `*Entries.kt` files) will be a port of tonearmboy's catalog. Worth a smaller pass.
- The Material 3 Expressive theme entry + the `CategoryAccent` data class will be ported from tonearmboy. Likely too small to extract.

## Decision criteria (same as whisperboy's sharing-analysis)

A piece of code should be promoted to a shared library only if **all five** are true:

1. **Both apps need it.** Not "could use it" — actually need it, in production code paths.
2. **The shape is the same in both.** Not just "named the same" — the inputs, outputs, and contract align without per-app branching.
3. **It's atomic.** A single concern, not a "framework". The user explicitly ruled out a shared *framework*. Atomic = "you can describe what it does in one sentence without 'and'".
4. **It's substantial.** Roughly: more than ~600 LOC, OR demanding to write correctly (parsers, audio DSP, security-relevant code), OR likely to evolve and benefit from one source of truth.
5. **The cost of duplication exceeds the cost of a subrepo.** Subrepo cost = separate versioning, separate build, change-coordination overhead, reviewer surface, release pipeline, slower agent context loading. Real cost. Not zero.

When any of the five fails, the code stays in-app and gets copied if the second app needs it. **Three similar lines of code is better than a premature abstraction.**

## Pre-Phase-A guess (not a decision)

Without having ported anything yet, the candidate-by-candidate guess for what'll come out of the post-A revisit:

- **`CachedDocumentFile` + SAF wrapper** — borderline. Same shape in both apps; ~150–300 LOC each; passes criteria 1, 2, 3. Probably fails 4 (not quite substantial enough). Default: copy.
- **SAF folder-library coordinator (scanner + diff-and-apply + rescan triggers)** — strong shape match but the per-format extraction logic diverges enough that the *coordinator* might share, the *scanner internals* probably can't. Defer to the post-A pass.
- **Settings catalog DSL** — interesting candidate. Same shape across tonearmboy + whisperboy + (eventually) pageboy. ~600–1000 LOC. Passes 1, 2, 3, 4. The risk is 5 — pulling this into a shared lib forces all three apps to coordinate on DSL changes, which slows individual app velocity. Best answer is probably "copy + occasionally rebase by hand"; default-no.
- **`AppGraph` pattern** — pattern, not code. Stays unshared.
- **Build scripts (`scripts/build-release-apk.sh`, `.github/workflows/release.yml`)** — copy as templates per app, do not share. Failing criterion 3 (they're bash + YAML, not library code) is decisive.

These are guesses to anchor the post-A pass; do not act on them without re-running the analysis with real code in hand.

## Revisit log

- _2026-05-24_ — file created at seed time, no analysis yet.
