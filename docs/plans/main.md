# pageboy — main build plan

## Status: 🟢 Phase C shipped; Phase D next

_Phase 0 verified, Phase A shipped (buildable Compose APK with family chrome — vertical nav rail, top bar, settings catalog DSL, AboutScreen, LicensesScreen, Licensee inventory). Phase B shipped (multi-root SAF document library — Room schema, four folder modes, magic-byte + extension classifier covering all 8 formats, scanner + rescan coordinator + repository, LibraryScreen with four whisperboy-style tabs + filters + search + sort, folders management screen, scan progress banner, Library settings section). Phases C+ are **stub headers** awaiting per-format research (see [`format-research.md`](format-research.md)) — each one names the format-research plan it depends on, and the research agent who owns that plan is expected to fill in the sub-step checkboxes for the corresponding phase before any implementation lands._

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

## Phase 0 — prerequisites (one-time, on the host) — _Shipped: 0.1–0.6 confirmed in commit `da82f9d` (skeleton)_

Goal: a buildable host. These run once per developer machine. Tracked here so the environment is verifiable before agents go to work. (Same machine as tonearmboy / whisperboy / shutterboy, so most of these are no-ops on this user's box, but we tick them for completeness on a fresh checkout.)

- [x] **0.1** Install Google's Android CLI: `curl -fsSL https://dl.google.com/android/cli/latest/linux_x86_64/android -o ~/.local/bin/android && chmod +x ~/.local/bin/android`. _(Confirmed: `~/.local/bin/android` present, version `0.7.15326717`.)_
- [x] **0.2** `android sdk install platforms/android-34 build-tools/34.0.0` — installs to `~/Android/Sdk/`. _(Confirmed: `~/Android/Sdk/platforms/{android-34,android-36}` + `build-tools/{34.0.0,36.0.0}` present.)_
- [x] **0.3** JDK 21 bundled by the Android CLI is sufficient for AGP 9. System Java only matters if a subagent invokes `./gradlew` directly without going through `android` — set `JAVA_HOME` to a system JDK 17+ in that case (see CLAUDE.md). _(Confirmed: `/usr/lib/jvm/java-26-openjdk` present and used for the Phase A `./gradlew assembleDebug` invocation.)_
- [x] **0.4** `mobile` MCP server registered at **project scope** (`pageboy/.mcp.json`) — already committed in the initial skeleton; verify with `claude mcp list` from inside the repo. _(Confirmed in the seed commit.)_
- [x] **0.5** `android-skills` MCP server registered at **project scope** (`pageboy/.mcp.json`) — already committed in the initial skeleton; verify with `claude mcp list` from inside the repo. _(Confirmed in the seed commit.)_
- [x] **0.6** Test target: **headless AVD `medium_phone`** (shared with the sibling apps). Created via `android emulator create --profile=medium_phone`. Started headlessly. Visible to ADB as `emulator-5554`. _(Confirmed: `emulator-5554` visible via `adb devices`, API 36, `sdk_gphone64_x86_64`.)_

---

## Phase A — scaffold — _Shipped: A.0–A.8 in commit `753e6a0`_

Goal: a buildable, sideload-able APK that boots into a blank Compose screen with the **tonearmboy-derived shell** (vertical navigation rail + top bar). Everything that follows assumes this exists. Mirror whisperboy Phase A almost exactly — same template choice, same gradle setup, same package convention — and then apply the [`ui-shell.md`](ui-shell.md) overlay so the empty app already wears the family's chrome.

- [x] **A.0** Browse `android create list` and pick the closest official template. _(`empty-activity` is the only template the CLI ships at version `0.7.15326717` — tagged `compose,activity,agp-9`; this is the same template tonearmboy + whisperboy + shutterboy used.)_
- [x] **A.1** `android create --name=pageboy --output=. <template>` from inside the repo root. Verify the generated layout: `app/`, `gradle/wrapper/`, `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`. Rename package from `com.example.pageboy` to `com.eight87.pageboy`. Rename theme to `PageboyTheme`. **Preserve the seed files** — `README.md`, `CLAUDE.md`, `LICENSE`, `.gitignore`, `.mcp.json`, `docs/` — via `rsync --ignore-existing` from a scratch scaffold dir (the pattern whisperboy A.1 used).
- [x] **A.2** Apply the **M3 Expressive baseline** from [`m3-expressive.md`](m3-expressive.md): bump `material3` to `1.5.0-alpha18` (gotcha #1: stable 1.4.0 keeps the expressive APIs `internal`), wrap the theme entry in `MaterialExpressiveTheme(...)`, switch to `expressiveLightColorScheme()` + `darkColorScheme()` (gotcha #1 again: no dark expressive factory in alpha18), default page bg to `surface` / cards to `surfaceContainerHigh` on dark (gotcha #2). Per-row CategoryAccent avatars wired into `SettingsRow` with the auto-derive-from-id fallback baked in from the start (gotcha #3).
- [x] **A.3** Apply the **tonearmboy-derived UI shell** from [`ui-shell.md`](ui-shell.md): vertical navigation rail on the left with the four top-level destinations (Library / Recents / Pinned / Settings — names tentative, finalize when the format work shows what surfaces really matter); top app bar across the top with the search icon + overflow; the rest of the screen is the format-renderer host. **Copy the shape, not the code** — adapt from `tonearmboy/app/src/main/java/com/eight87/tonearmboy/ui/` directly. _(Adapted shape only; the music-player overlay sheet / mini-player / palette locals are NOT carried across since a document reader has no now-playing surface.)_
- [x] **A.4** Settings catalog DSL bootstrap: scaffold the four files referenced in [`ui-shell.md`](ui-shell.md) — `SettingsCardDsl.kt`, `SettingsCatalog.kt`, `SettingsPagesRender.kt`, `SettingsScreen.kt` — with a single placeholder section so the rest of Phase A can verify the navigation route lands somewhere. Real section entries land alongside the surfaces they configure. _(Ships with one `Root` section containing the About entry; per-feature sections — Appearance / Library / Reader / Annotations / Signing — land in later phases.)_
- [x] **A.5** About + Licenses scaffold: `AboutScreen.kt` + `LicensesScreen.kt` shaped per [`oss-licenses.md`](oss-licenses.md). Licensee plugin configured + first inventory generated (will be near-empty until format-research picks deps). _(60-entry `artifacts.json` lands; every dep is `Apache-2.0` — the family allowlist `Apache-2.0 / MIT / BSD-2-Clause / BSD-3-Clause / MPL-2.0` is configured and report-only as planned. Canonical SPDX texts pre-staged at `app/src/main/assets/licenses/{Apache-2.0,MIT,BSD-2-Clause,BSD-3-Clause,MPL-2.0}.txt`.)_
- [x] **A.6** `AndroidManifest.xml` adds: nothing exotic in v1. `minSdk = 28` (matches whisperboy floor). `compileSdk = 36`, `targetSdk = 36`. **No `READ_EXTERNAL_STORAGE`** — pageboy is SAF-only. Foreground service permissions not needed (pageboy is a foreground-only app — there is no playback service equivalent). Add deep-link intent filters for `application/pdf`, `application/epub+zip`, `text/markdown`, `text/plain`, the OOXML MIME types, and the ODF MIME types — pageboy must be a first-class "Open with…" target. _(`allowBackup=false`; all eight MIME filters wrap `action.VIEW` + `category.{DEFAULT,BROWSABLE}` + `scheme content` / `scheme file`; Markdown gets two MIME aliases plus `.md` / `.markdown` path-pattern fallbacks for file managers that hand us a generic text/plain intent.)_
- [x] **A.7** Build verification: `./gradlew assembleDebug` succeeds. APK lands at `app/build/outputs/apk/debug/pageboy-debug.apk`. _(Green; APK ≈ 60.8 MB — larger than whisperboy's ~39 MB Phase A baseline because pageboy pulls in `material-icons-extended` at Phase A for the nav rail / settings glyphs whereas whisperboy added it later. Acceptable for a debug build; R8 will tree-shake non-referenced icons in release builds.)_
- [x] **A.8** Install verification: `android run --apks=app/build/outputs/apk/debug/pageboy-debug.apk` launches the placeholder activity on `emulator-5554`. Confirm via `dumpsys window | grep mCurrentFocus`. Screencap shows the rail + top bar already in place. _(Installed via `adb -s emulator-5554 install -r`; `am start -n com.eight87.pageboy/.PageboyActivity` launched; `dumpsys` confirmed `mCurrentFocus=Window{… com.eight87.pageboy/com.eight87.pageboy.PageboyActivity}`. Screencap at `/tmp/pageboy-A8.png` confirms the dark-mode shell — nav rail with Library/Recents/Pinned/Settings, top app bar with title + search + overflow, Library placeholder body in the centre. Drill-down Settings → About → Licenses also verified manually via screencaps; LicensesScreen renders the 60-entry LazyColumn correctly.)_

---

## Phase B — SAF document library scan + library UI — _Shipped: B.1–B.18 in commit `54a7dc7`_

Goal: walk picked SAF tree roots, classify each entry by `DocumentFormat` via extension + magic-byte sniff, persist a `DocumentEntity` row per file, soft-delete missing files on rescan. Lifts the SAF scanner pattern wholesale from `whisperboy/data/library/` — same `CachedDocumentFile`, same SHA-256-of-(treeUri+relPath) ID, same diff-and-apply transaction. Stack the whisperboy-pattern tabbed library UI on top: four tabs (Started / All / Recents / Pinned), format + collection filter chips, search, sort. The user explicitly asked for this shape: *"adding whisperboy like location folders, filters and so on. multiple taps for started book, just browsing everything etc."*

- [x] **B.1** Room database setup. `LibraryDatabase` with `documents`, `recents`, `pinned`, and `library_fingerprints` tables. KSP-generated DAOs. Schema export to `app/schemas/` per Room migration convention. (`library_roots` lives in DataStore via `AndroidPersistedUriPermissionStore` instead of a Room table — same shape whisperboy uses; `read_progress` collapses onto the `documents` row as `lastReadPositionMs` + `lastOpenedAt` for the single-source-of-truth pattern.)
- [x] **B.2** Multi-root SAF persistence. `LibraryRoot` data class (tree URI + display label + folder mode). `AndroidPersistedUriPermissionStore` writes to DataStore Preferences keyed by URI string; on add it takes the persistable URI permission via `ContentResolver.takePersistableUriPermission(FLAG_GRANT_READ_URI_PERMISSION)`. Permissions survive reboot via the OS-side persistable grants.
- [x] **B.3** Folder-mode classification — sealed type `FolderType`:
  - **`SingleFile`** — user picked one specific file; one document.
  - **`SingleFolder`** — flat: every supported file directly in this folder is a document, no recursion. Collection name = folder name.
  - **`Root`** — recursive walk. Each top-level subfolder is a collection; subfolders nest into the same collection. Files at the root level land in a "root" collection bucket.
  - **`Category`** — top-level subfolders are category labels; documents inside each get tagged with that category as their `collection`.
- [x] **B.4** Document classification by extension + magic-byte sniff. `DocumentFormat` enum (`Markdown` / `Txt` / `Pdf` / `Epub` / `Docx` / `Xlsx` / `Odt` / `Ods` / `Unknown`). `DocumentClassifier` reads first 4 KiB of the file (enough to sniff the ZIP central directory headers for the Office formats); primary discriminant is the magic header, fallback is the extension. Signatures: `%PDF-` for PDF; ZIP magic `PK\x03\x04` then check for an early `mimetype` member (EPUB → `application/epub+zip`, ODT → `application/vnd.oasis.opendocument.text`, ODS → `application/vnd.oasis.opendocument.spreadsheet`) or central-directory entries (`word/document.xml` → DOCX, `xl/workbook.xml` → XLSX); extensions `.md` / `.markdown` → Markdown; anything text-like with `.txt` → Txt.
- [x] **B.5** `CachedDocumentFile` performance wrapper. Direct adaptation of whisperboy's `CachedDocumentFile` — lazy `name` / `length` / `lastModified` / `isDirectory` / `isFile` / `type` / `children`. Cache is per-instance, fresh wrapper per rescan.
- [x] **B.6** `SafLibraryScanner` — the walker. Per root, walks the tree using the folder-mode rules; for each candidate file, classifies + reads light metadata (file size, mtime, derived title from filename) + emits a `ScannedDocument`. Soft-deletes via `isMissing = 1` flag on rescan (never hard-delete so per-document state like `lastReadPositionMs` / `pinned` survives a temporary unmount or rename).
- [x] **B.7** `LibraryRescanCoordinator` — schedules + debounces scans. Triggers: manual user-request, root-added (via observing the `PersistedUriPermissionStore.observeRoots()` flow with `distinctUntilChanged`). Exposes `Flow<ScanState>` (`Idle` / `Scanning` / `Failed`). WorkManager + foreground service NOT needed (pageboy has no playback service — scans are foreground and bounded).
- [x] **B.8** `LibraryRepository` — single API the UI talks to behind narrow interfaces (`DocumentSource` for the UI, `ScanWriter` for the coordinator). Methods include `observeDocuments(): Flow<List<DocumentEntity>>`, `observeCollections(): Flow<List<String>>`, `addRoot()` / `removeRoot()` / `requestRescan()`, `pinDocument()`, `recordOpen()`.
- [x] **B.9** **Library UI with tabs.** `LibraryScreen` Composable with a `TabRow` along the top. Four tabs: **Started** (documents with `lastReadPositionMs > 0`), **All** (every non-missing document sorted per LibrarySorting; default Title A-Z), **Recents** (documents ordered by `lastOpenedAt DESC`, capped at 30), **Pinned** (documents the user has explicitly pinned).
- [x] **B.10** **Filters + search + sort.** Filter chip row above the tab content: filter-by-format (multi-select chips for the 8 formats), filter-by-collection (multi-select chips). Search box (top-app-bar search icon expands to text field) case-insensitive substring match across title + filename + collection. Sort menu in app bar: Title A-Z / Title Z-A / Date added / Last opened / Format. Persisted in `LibraryUiPrefs` (DataStore).
- [x] **B.11** **Document cards.** Each card shows: format icon + title + collection chip + read-progress indicator (small linear bar on Started tab) + overflow menu (Pin/Unpin). Tap navigates to `ReaderRoute(docId)` which routes to a placeholder `ReaderScreen` (real reader is Phase C+).
- [x] **B.12** **Multi-root management screen.** `LibraryFoldersScreen` accessible from Settings → Library → Source folders. Lists each root with display label + folder mode + path + remove button. "Add folder…" button launches the SAF tree-URI picker then prompts for folder mode via a modal bottom sheet.
- [x] **B.13** **Scan progress banner.** When scanning, `LibraryScanProgressBanner` appears at the top of LibraryScreen (below the filter row) with cancel disabled (no cancel yet — scan is fast for typical document libraries). "X documents found" snackbar on completion when `newDocuments > 0`.
- [x] **B.14** **Empty states.** Per tab: Started "Open something to see it here", All "No documents yet — add a folder in Settings → Library", Recents "Nothing recent", Pinned "Pin documents from the overflow menu".
- [x] **B.15** **Settings integration.** Add a `Library` section to the settings catalog DSL with entries: Source folders (routes to B.12 screen), Re-scan now (button-action), Show hidden files (boolean toggle — defaults false). Auto-scan-on-start is implicit (the rescan coordinator fires on root-added and on app-start automatically).
- [x] **B.16** **Tests:**
  - `DocumentClassifierTest` — JVM. Feeds known byte prefixes for each format; asserts correct `DocumentFormat`.
  - `LibraryFilterSortSearchTest` — JVM. Verifies tab filtering, format/collection chip filtering, search substring match, sort order.
  - `LibraryScreenSmokeTest` — Robolectric Compose test. Renders LibraryScreen with a fake `DocumentSource`; asserts all four tab labels render; asserts at least one document card renders.
  - `LibraryFoldersScreenSmokeTest` — Robolectric Compose test. Renders empty + non-empty folder list; asserts the "Add folder" button exists.
- [x] **B.17** **Build green** — `./gradlew assembleDebug` + `./gradlew testDebugUnitTest` both succeed.
- [x] **B.18** **Smoke on `emulator-5554`** — install rebuilt APK, launch, screencap the library empty-state + tab row. Screencaps land at `/tmp/pageboy-B-library-empty.png` and `/tmp/pageboy-B-library-tabs.png`.

---

## Phase C — reader chrome + DocumentRenderer + per-axis controllers — _Shipped: C.1–C.11 in commit `c7e1dfb`_

Goal: the screen the user lands on when they tap a document. Top bar (back, title, find-in-doc, share, overflow), scroll position persistence per document, edge-to-edge insets handled. The renderer body is dispatched through the `DocumentRenderer` open/closed interface (R.X.9) via a `FormatRegistry`; until per-format renderers ship (Phase D+), every format falls back to `PlaceholderRenderer`. Reader-side controllers split along their natural axes from day one per R.C — no god `ReaderController`.

- [x] **C.1** `DocumentRenderer` + `DocumentBytesSource` + `DocumentHandle` interfaces in `format/api/`. Narrow 3-method renderer (open / Body / extractTitle); per-format `DocumentHandle` subtypes; `AutoCloseable`.
- [x] **C.2** `FormatRegistry` + `CompiledFormatRegistry` in `format/registry/`. Open/closed dispatch — adding a format is one map entry in `AppGraph`, no `when (format)` switches in the reader.
- [x] **C.3** `PlaceholderRenderer` in `format/placeholder/`. Renders a polite "not yet implemented" message + view-info + back-to-library affordance. Backstop for every unimplemented format.
- [x] **C.4** Per-axis reader controllers in `ui/reader/control/`: `ReaderStateProjector` (sealed `ReaderState`), `ScrollPersistence`, `FindInDocCommands`, `ShareExportCommands`. Each in its own file. `AnnotationCommands` not shipped — Phase G+ closes that surface.
- [x] **C.5** Reader chrome split per R.D: `ReaderScreen` orchestrator + `ReaderTopBar` + `ReaderFindPanel` + `ReaderBody` + `ReaderErrorState`. None past 250 LOC; orchestrator under 200.
- [x] **C.6** Nav wired — `LibraryScreen` card-tap routes through `ReaderRoute(documentId)`; back returns to library. Title resolves from `DocumentHandle.title` (no longer passed through the route).
- [x] **C.7** `AppGraph` exposes `formatRegistry`, `readerStateProjector`, `scrollPersistence`, `findInDocCommandsFactory`, `shareExportCommands`.
- [x] **C.8** `Setting<T>` value type introduced in `data/settings/` (first land in pageboy, mirrors whisperboy's R.B.1). `ReaderSettings` facet with one placeholder entry (`continuousScrolling: Boolean`, default true). Reader section added to the settings catalog. R.B.1 + R.B.2 ticked.
- [x] **C.9** Tests: `FormatRegistryTest`, `PlaceholderRendererTest`, `ReaderStateProjectorTest`, `ScrollPersistenceTest`, `FindInDocCommandsTest`, `ReaderScreenSmokeTest`, `ReaderTopBarTest`.
- [x] **C.10** Build green — `:app:assembleDebug` + `:app:testDebugUnitTest` both pass. Pre-merge checklist (8 items) PASS.
- [x] **C.11** Smoke on `emulator-5554` — reader chrome + placeholder body confirmed via screencap at `/tmp/pageboy-C-reader-placeholder.png`.

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

## Phase N — share-sheet ingest + recents _(spec'd in [`open-with.md`](open-with.md); intent-filter declarations landed in Phase A.6)_

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
