# pageboy — main build plan

## Status: 🟡 PRE-PHASE 0 (skeleton + plans only)

_Repo skeleton, README, CLAUDE.md, and this plan landed in the initial commit. No code yet. Phase 0 is the first phase that actually does anything. Phases B+ are **stub headers** awaiting per-format research (see [`format-research.md`](format-research.md)) — each one names the format-research plan it depends on, and the research agent who owns that plan is expected to fill in the sub-step checkboxes for the corresponding phase before any implementation lands._

## Stack (locked)

- **Language:** Kotlin
- **UI:** Jetpack Compose + **Material 3 Expressive** (see [`m3-expressive.md`](m3-expressive.md))
- **Data:** Room (document / bookmark / annotation / signature-placement cache), DataStore (preferences), SAF / `DocumentFile` (document files — no MediaStore, no `READ_EXTERNAL_STORAGE`)
- **Renderers:** per-format packages behind a `DocumentRenderer` interface. Library selection per format is the explicit subject of [`format-research.md`](format-research.md).
- **Build front-end:** Google's [Android CLI](https://developer.android.com/tools/agents/android-cli) (`android` command, launched April 2026). Wraps Gradle, SDK, install, run. **No Android Studio.**
- **Build back-end:** Gradle (driven via the Android CLI). The repo includes a Gradle wrapper.
- **Unit tests:** Robolectric (JVM-only, zero device). All format parsers run JVM-only.
- **UI tests:** [mobile-mcp](https://github.com/mobile-next/mobile-mcp) over ADB. Current target: headless AVD `medium_phone` (Android 16 / API 36) — see Phase 0.
- **Knowledge:** `android docs search` for live Android API guidance. `android-skills-mcp` for the official Android skills inside Claude Code.

## Plan-file discipline (global rule, restated)

Per the user's global CLAUDE.md (`~/.claude/CLAUDE.md`):

1. **Numbered phases** using typeable letters: `Phase A`, `Phase B`, … No `§` characters.
2. **Sub-step checkboxes** inside each phase: `- [ ] **A.1** …`. A phase without sub-step checkboxes is forbidden — if you can't articulate sub-steps, you don't have a plan yet; write the design first. The stub headers below (Phase B onward) explicitly defer their sub-steps to the next-round research agents and name the plan that produces them.
3. **Tick as you ship**: `- [x]` AND a "shipped in change `<jj-id>`" note on the phase header at the same time as the work lands. Do not batch ticks until the end. (In plain git repos, commit hashes are fine. Whichever VCS this repo lands in long-term, follow that rule.)
4. **Mark the plan DONE**: add `## Status: ✅ DONE` at the top of the file once every phase is ticked.
5. **Repo plans live in the repo**: anything that another agent / future-me / a sub-agent will pick up belongs at `<repo>/docs/plans/`. This file is that surface.

---

## Phase 0 — prerequisites (one-time, on the host)

Goal: a buildable host. These run once per developer machine. Tracked here so the environment is verifiable before agents go to work. (Same machine as tonearmboy / whisperboy / shutterboy, so most of these are no-ops on this user's box, but we tick them for completeness on a fresh checkout.)

- [ ] **0.1** Install Google's Android CLI: `curl -fsSL https://dl.google.com/android/cli/latest/linux_x86_64/android -o ~/.local/bin/android && chmod +x ~/.local/bin/android`. _(Expected no-op — already installed for the sibling apps.)_
- [ ] **0.2** `android sdk install platforms/android-34 build-tools/34.0.0` — installs to `~/Android/Sdk/`. _(Expected no-op — already present.)_
- [ ] **0.3** JDK 21 bundled by the Android CLI is sufficient for AGP 9. System Java only matters if a subagent invokes `./gradlew` directly without going through `android` — set `JAVA_HOME` to a system JDK 17+ in that case (see CLAUDE.md).
- [ ] **0.4** `mobile` MCP server registered at **project scope** (`pageboy/.mcp.json`) — already committed in the initial skeleton; verify with `claude mcp list` from inside the repo.
- [ ] **0.5** `android-skills` MCP server registered at **project scope** (`pageboy/.mcp.json`) — already committed in the initial skeleton; verify with `claude mcp list` from inside the repo.
- [ ] **0.6** Test target: **headless AVD `medium_phone`** (shared with the sibling apps). Created via `android emulator create --profile=medium_phone`. Started headlessly. Visible to ADB as `emulator-5554`. _(Expected no-op — already exists.)_

---

## Phase A — scaffold

Goal: a buildable, sideload-able APK that boots into a blank Compose screen with the **tonearmboy-derived shell** (vertical navigation rail + top bar). Everything that follows assumes this exists. Mirror whisperboy Phase A almost exactly — same template choice, same gradle setup, same package convention — and then apply the [`ui-shell.md`](ui-shell.md) overlay so the empty app already wears the family's chrome.

- [ ] **A.0** Browse `android create list` and pick the closest official template. Default expectation: `empty-activity` (the same template tonearmboy + whisperboy + shutterboy used).
- [ ] **A.1** `android create --name=pageboy --output=. <template>` from inside the repo root. Verify the generated layout: `app/`, `gradle/wrapper/`, `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`. Rename package from `com.example.pageboy` to `com.eight87.pageboy`. Rename theme to `PageboyTheme`. **Preserve the seed files** — `README.md`, `CLAUDE.md`, `LICENSE`, `.gitignore`, `.mcp.json`, `docs/` — via `rsync --ignore-existing` from a scratch scaffold dir (the pattern whisperboy A.1 used).
- [ ] **A.2** Apply the **M3 Expressive baseline** from [`m3-expressive.md`](m3-expressive.md): bump `material3` to `1.5.0-alpha18` (gotcha #1: stable 1.4.0 keeps the expressive APIs `internal`), wrap the theme entry in `MaterialExpressiveTheme(...)`, switch to `expressiveLightColorScheme()` + `darkColorScheme()` (gotcha #1 again: no dark expressive factory in alpha18), default page bg to `surface` / cards to `surfaceContainerHigh` on dark (gotcha #2).
- [ ] **A.3** Apply the **tonearmboy-derived UI shell** from [`ui-shell.md`](ui-shell.md): vertical navigation rail on the left with the four top-level destinations (Library / Recents / Pinned / Settings — names tentative, finalize when the format work shows what surfaces really matter); top app bar across the top with the search icon + overflow; the rest of the screen is the format-renderer host. **Copy the shape, not the code** — adapt from `tonearmboy/app/src/main/java/com/eight87/tonearmboy/ui/` directly.
- [ ] **A.4** Settings catalog DSL bootstrap: scaffold the four files referenced in [`ui-shell.md`](ui-shell.md) — `SettingsCardDsl.kt`, `SettingsCatalog.kt`, `SettingsPagesRender.kt`, `SettingsScreen.kt` — with a single placeholder section so the rest of Phase A can verify the navigation route lands somewhere. Real section entries land alongside the surfaces they configure.
- [ ] **A.5** About + Licenses scaffold: `AboutScreen.kt` + `LicensesScreen.kt` shaped per [`oss-licenses.md`](oss-licenses.md). Licensee plugin configured + first inventory generated (will be near-empty until format-research picks deps).
- [ ] **A.6** `AndroidManifest.xml` adds: nothing exotic in v1. `minSdk = 28` (matches whisperboy floor). `compileSdk = 36`, `targetSdk = 36`. **No `READ_EXTERNAL_STORAGE`** — pageboy is SAF-only. Foreground service permissions not needed (pageboy is a foreground-only app — there is no playback service equivalent). Add deep-link intent filters for `application/pdf`, `application/epub+zip`, `text/markdown`, `text/plain`, the OOXML MIME types, and the ODF MIME types — pageboy must be a first-class "Open with…" target.
- [ ] **A.7** Build verification: `./gradlew assembleDebug` succeeds. APK lands at `app/build/outputs/apk/debug/pageboy-debug.apk`.
- [ ] **A.8** Install verification: `android run --apks=app/build/outputs/apk/debug/pageboy-debug.apk` launches the placeholder activity on `emulator-5554`. Confirm via `dumpsys window | grep mCurrentFocus`. Screencap shows the rail + top bar already in place.

---

## Phase B — SAF document library scan _(stub — depends on its own sub-step writing pass)_

Goal: walk picked SAF tree roots, classify each entry by `DocumentFormat` via extension + magic-byte sniff, persist a `DocumentEntity` row per file, soft-delete missing files on rescan. Lifts the SAF scanner pattern wholesale from `whisperboy/data/library/` — same `CachedDocumentFile`, same SHA-256-of-(treeUri+relPath) ID, same diff-and-apply transaction. The format-specific bits (which formats to recognize, how to extract a title without rendering) finalize once [`format-research.md`](format-research.md) reports back. Sub-step checkboxes filled in by the agent that opens this phase.

---

## Phase C — reader screen (universal chrome) _(stub — depends on [`format-research.md`](format-research.md) for the renderer interface shape)_

Goal: the screen the user lands on when they tap a document. Top bar (back, title, find-in-doc, share, overflow), bottom inset-aware annotation toolbar when applicable, scroll position persistence per document, edge-to-edge insets done right. The renderer body is whichever `DocumentRenderer` the dispatch resolves. Sub-step checkboxes filled in once the renderer interface is locked.

---

## Phase D — Markdown renderer _(stub — depends on `docs/plans/format-markdown.md`)_

Goal: render Markdown with syntax-highlighted code blocks, GFM tables, task lists, footnotes. The research plan picks the parser + the Compose-side highlighter. Sub-steps land then.

---

## Phase E — plain text renderer _(stub — depends on `docs/plans/format-txt.md`)_

Goal: render plain text with reflow at the user's font size, encoding detection (UTF-8 / UTF-16 / Windows-1252), line-ending normalization. Smallest format; minimal deps. Sub-steps land with the research plan.

---

## Phase F — PDF renderer _(stub — depends on `docs/plans/format-pdf.md`)_

Goal: render PDF pages, scroll smoothly through 500+ page documents, find-in-doc, link clicks. Heaviest renderer in the app; library choice (Pdfium / MuPDF / `android.graphics.pdf.PdfRenderer` / `PdfBox-Android` / hybrid) is the biggest single decision in pageboy and gets a dedicated plan. Sub-steps land with the research plan.

---

## Phase G — PDF annotation _(stub — depends on `docs/plans/format-pdf.md`)_

Goal: highlight, underline, strike-through, sticky note, freehand ink. Annotations persist as overlays (own table in Room) and on save get serialized back into the PDF (PDF 1.7 annotation dictionaries) so the annotated file opens correctly in every other PDF viewer. The annotation-overlay-vs-burn-in tradeoff is a major design decision documented in the research plan.

---

## Phase H — PDF signing _(stub — depends on `docs/plans/format-pdf.md`)_

Goal: two complementary surfaces — (1) freehand signature stamp (the user signs once, the signature is saved as a transparent PNG, can be dropped onto any PDF page at a chosen location); (2) digital signature (cryptographic, PKCS#7 / CAdES / PAdES — pick one per the research plan, with a clear "self-signed for personal use" path and a "ship the user's existing PFX / smart-card flow" path). Both surfaces share the placement UI. Two of the research plan's questions are load-bearing here: which crypto library, and what key-store affordances Android gives us in 2026.

---

## Phase I — DOCX renderer _(stub — depends on `docs/plans/format-docx.md`)_

Goal: render DOCX with paragraph styles, bold / italic / underline, headings, bullet lists, tables, embedded images. The OOXML XML schema is large; the research plan picks how much of it to support (the "every Microsoft Word doc the user has ever seen" subset, not the spec) and which library does the ZIP-walk + XML parse.

---

## Phase J — XLSX renderer _(stub — depends on `docs/plans/format-xlsx.md`)_

Goal: render XLSX as a read-only paged spreadsheet — cell values, basic formatting, sheet tabs along the bottom, frozen-pane header support. Formulas evaluate to their cached values from the XML (no recompute engine). Charts are TBD per the research plan.

---

## Phase K — ODT renderer _(stub — depends on `docs/plans/format-odt.md`)_

Goal: same surface as DOCX, applied to OpenDocument Text. ODT and DOCX share enough structural intuition that the research plan should also address whether to ship one renderer with two parsers feeding a common intermediate model or two fully separate renderers. (Strong prior: separate renderers, common intermediate model — but it's the research agent's call.)

---

## Phase L — ODS renderer _(stub — depends on `docs/plans/format-ods.md`, sibling to format-xlsx)_

Goal: spreadsheet sibling to ODT, equivalent to XLSX. Likely shares plumbing with XLSX (research plan should call this).

---

## Phase M — EPUB renderer _(stub — depends on `docs/plans/format-epub.md`)_

Goal: render EPUB 2 + 3 spine, navigate by ToC, reflow at the user's font size, per-book bookmark + last-read position. CSS sanitization is the hairy part (an EPUB can ship arbitrary CSS that needs to be filtered before reaching the renderer). Research plan picks the rendering surface (Compose-native `Text` + custom block layout vs. embedded WebView vs. hybrid).

---

## Phase N — share-sheet ingest + recents _(stub — depends on Phase A.6 deep-link MIME filters landing)_

Goal: opening any supported file from another app (file manager, email client, browser) lands in pageboy via a `content://` intent, gets resolved to a `DocumentEntity` (creating one for the ad-hoc URI), and shows the recents list on the next cold start so the user can find what they opened last week. Sub-steps land when Phase A.6 is closed and the team has a clearer picture of how much state the recents surface should keep.

---

## Phase O — release pipeline _(stub — mirrors whisperboy Phase O)_

Goal: `scripts/build-release-apk.sh` + the self-disabling `.github/workflows/release.yml` fallback. Direct port of whisperboy's scripts with `whisperboy` → `pageboy` substitution. Sub-steps land when Phase A is green.

---

## Beyond Phase O

Open questions for a later planning pass — placeholders, not commitments:

- Per-document custom CSS for EPUB and Markdown (user-supplied "reading theme").
- Cross-document full-text search (FTS4 index over extracted plain text per format).
- Library categories / tags / shelves.
- Backup / restore of the annotations + signature placements DB (independent of any cloud sync; the user controls the backup file).
- Onboarding flow analogous to whisperboy Phase L.
- Widget showing "currently reading" on the home screen.
