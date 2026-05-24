# pageboy — SOLID refactor plan (prophylactic, mostly forward-looking)

## Status: 🟡 STANDING — discipline gates every Phase B–O commit. Pre-Phase B prophylactic; tonearmboy / whisperboy / strictlykeptboy precedent inherited.

This is the SOLID discipline gate for pageboy. Synthesized from tonearmboy's retroactive `refactor-solid.md` (50 audit findings collapsed into R.A–R.F, all shipped) and whisperboy's forward-looking equivalent (R.A–R.E all shipped without ever growing a god-object). The pattern works: pay SOLID in tiny increments as code lands, not in 1–2-day reflux refactors after the god-objects emerge.

## What this plan IS

A codification of the SOLID lessons tonearmboy paid for retroactively, expressed as **gates on every Phase B–O commit** — narrow interfaces over fat repositories, facet settings over snapshot blobs, split controllers over god-objects, file-size discipline over 1500-LOC screens, RouteScope over inline route handling, sealed-type dispatch over enum + when-chain.

Every implementation agent reads this file before writing code. The cross-cutting rules below are checked BEFORE marking any phase done.

## What this plan is NOT

A list of god-objects to split. Pageboy doesn't have any yet. If/when one accumulates (e.g. `ReaderController.kt` past 800 LOC, `LibraryScreen.kt` past 1500 LOC, `PageboyApp.kt` past 500), that's the trigger to add a new phase here with concrete sub-steps scoped to the refactor.

The flip side: if the discipline holds across Phases B–O and no god-object emerges, this plan should stay short. Tonearmboy's R.A–R.E phases each cost 1–2 days of refactor work; the equivalent effort here is **distributed in tiny increments across the feature phases that introduce the patterns**, paid as they happen.

---

## Cross-cutting rules (every phase must honour these)

These are the load-bearing conventions every subagent + every code-shipping turn must comply with. They are checked BEFORE marking a phase done. Direct inheritance from strictlykeptboy's `refactor-solid.md` — same family standard.

### R.X.1 — Narrow data interfaces

Composables / ViewModels take the smallest interface that satisfies their need, not a god-handle. If `LibraryScreen` needs only `documents.observe(filter)`, it takes a `DocumentSource` (~3 methods) — not the whole `LibraryRepository` (12+ methods). When a leaf needs 1–3 fields, pass *those fields*, not the parent object.

**Anti-pattern to watch for:** `fun DocumentCard(document: DocumentEntity, repository: LibraryRepository)` when the card uses only `document.title + document.format + onClick`.

### R.X.2 — Sealed types for branching, not enum + when-chain

When a behaviour varies by case, prefer a sealed hierarchy with one variant per case (data + variant-specific behaviour co-located), not an `enum + when(it)` that grows in every consumer. Sealed types are open/closed: adding a variant is a new file, not a hunt-and-modify across 5 sites.

**Concrete pageboy applications:**
- `DocumentFormat` is a sealed-ish enum because it's used as a Room column (enums round-trip cleanly through Room). But branching on it (renderer dispatch, classifier verdicts, icon picker) goes through a `DocumentRenderer` sealed interface or a `FormatRegistry` open-closed map, NOT through a `when (format)` switch in every consumer.
- `FolderType` (`SingleFile` / `SingleFolder` / `Root` / `Category`) — sealed class, each variant carries its own scan rules.
- `ScanState` (`Idle` / `Scanning(progress)` / `Failed(reason)`) — sealed.
- `LibraryFilter` — composable filter spec; sealed if branching, data class hierarchy if combining.

### R.X.3 — Composition root is the only place that wires concrete classes

Concrete `Room` DAOs, `DocumentFile` wrappers, OkHttp clients (if/when added), `DataStore` instances live behind interfaces in production code. The composition root (currently `PageboyApplication` + its `AppGraph` — to be introduced when `PageboyApplication` wiring crosses ~200 LOC) is the *only* place that knows the concrete types. ViewModels / composables / use-cases take the interface.

**Currently passes:** Phase A shipped a minimal Application; AppGraph TBD when Phase B wiring justifies.

**Open work-stream:** introduce `AppGraph` once `PageboyApplication` wiring crosses ~200 LOC.

### R.X.4 — No god-files

Soft heuristic: anything past **~500 LOC** of non-trivial Kotlin deserves a second look; past **~800 LOC** almost always needs splitting. The split should follow concerns (single-responsibility), not arbitrary file-size targets.

**Per-phase red flags to watch:**

| Likely surface | Risk | Mitigation from day one |
| --- | --- | --- |
| `ReaderScreen.kt` (Phase C) | Hosting 8 different format renderers behind a chrome that owns scroll persistence + find-in-doc + share + annotation toolbar = god-screen risk | Renderer body is a `DocumentRenderer` interface call; chrome is its own scaffold; controllers are separate (see R.C below) |
| `LibraryScreen.kt` (Phase B) | Tonearmboy's grew to 1528 LOC with 5 near-duplicate tab dispatchers. Pageboy has 4 tabs (Started / All / Recents / Pinned) — same risk | One file per tab (`tabs/{Started,All,Recents,Pinned}TabScreen.kt`) from day one; `LibraryScreen` is just scaffold + dispatch (~150 LOC target) |
| `PageboyApp.kt` / nav (Phase A — already shipped) | Tonearmboy's grew to 820 LOC; whisperboy refactored to 122 via RouteScope | Phase A already split. Watch for regression in Phase B (Library destination might pull weight back) |
| `ReaderController.kt` (Phase C) | Tonearmboy's `PlaybackUiController` hit 882 LOC across 6 axes | Split from day one: `ReaderStateProjector` / `FindInDocCommands` / `AnnotationCommands` / `ScrollPersistence` / `ShareCommands` (each per the actual axes that emerge) |
| `LibraryScanner.kt` (Phase B) | Whisperboy's keeps tight because the scan loop is one concern; pageboy must match | One concern per file: `DocumentClassifier` / `CachedDocumentFile` / `LibraryScanner` / `LibraryRescanCoordinator` are separate files (per the Phase B sub-step plan) |
| `SettingsRepository` (future) | Tonearmboy's bloated to 826 LOC + 25 keys + 27-field snapshot | NEVER build a god SettingsRepository. From day one: `Setting<T>` + facet interfaces (`ReaderSettings`, `LibrarySettings`, `ThemeSettings`, etc.). See R.B below |

Subagents must report file LOC of newly created files past 200 in their phase-completion report.

### R.X.5 — Liskov: deferred / NotImplementedError variants must be temporary

A sealed-variant or interface impl that throws `NotImplementedError` is a Liskov violation in spirit, even though it compiles. We allow them ONLY when the consumer UI is also deferred (so the violation is unreachable in practice). Every `NotImplementedError` carries an inline comment referencing the phase that closes the deferral.

**Currently allowed (none yet — Phase A is shipped clean).**

### R.X.6 — Wrong-direction imports

Lower layers MUST NOT import upper layers:
- `data/` cannot import from `ui/`.
- `format/` cannot import from `data/library/` or `ui/`. (Format renderers are pure plumbing — they take a `DocumentSource` of bytes, emit Compose content.)
- `ui/library/` cannot import from `ui/reader/` or `ui/settings/` (or vice-versa). Cross-screen state goes through narrow interfaces in `data/` or shared `ui/scaffold/`.
- `ui/` is the outermost layer and may import from `data/`, `format/`, `domain/` (if any).

**Anti-pattern caught early:** `data/library/LibraryRepository.kt` imports `ui.settings.SettingsRepository` (tonearmboy Data-F3 finding) — fixed in tonearmboy R.A.5 by moving `SettingsRepository` to a neutral package. Pageboy avoids this by putting `Setting<T>` + facet interfaces in `data/settings/` from day one (R.B below).

Caught early via lint; manually verified per phase.

### R.X.7 — Interface segregation in Compose

In Compose specifically: don't pass a god-state object down 5 levels. Pass the 3 fields the leaf actually reads. State hoisting + small parameter lists keep recomposition scoped.

**Anti-pattern:** `DocumentCard(libraryState: LibraryUiState)` when the card reads only `document.title` + `document.format` + `document.readProgress` + `onClick`. Pass those four (or three + the function).

### R.X.8 — Test discipline

- Every new file with public surface gets a test (Robolectric for Android-dependent, JVM-only otherwise).
- Tests run in JVM Robolectric under `:app:testDebugUnitTest`. No physical device needed for unit-level work.
- Tests must pass green before commit (the AVD smoke is additive, not replacement).
- Subagents must report the test count delta in their phase-completion report.

### R.X.9 — `DocumentRenderer` is the central open/closed interface

Pageboy's format-pluralism (8 formats in v1, room for more in v2) makes `DocumentRenderer` the load-bearing abstraction. Every format renderer is a `DocumentRenderer` impl; the reader screen dispatches via a `FormatRegistry` (or sealed-type registry) — never via `when (format)` in the reader. Adding a format = adding an impl + a registry entry; the reader doesn't change.

The interface itself stays narrow:

```kotlin
interface DocumentRenderer {
    val format: DocumentFormat
    suspend fun open(documentSource: DocumentSource): DocumentHandle
    @Composable fun Body(handle: DocumentHandle, modifier: Modifier)
    suspend fun extractTitle(documentSource: DocumentSource): String?  // for the scanner
}
```

The `DocumentHandle` is per-format (sealed-typed if it varies, or a generic per-format payload). The Composable body is what the reader chrome wraps.

---

## Phase R.A pattern — narrow data interfaces from day one

**Applies to:** Phase B (library + multi-root + scanner + tabs).

**Lesson from tonearmboy R.A:** Composables that took the whole `LibraryRepository` (~30 methods) for one or two Flows forced recomposition coupling, made preview/test setup heavy (real `Context` + Room for the simplest screen), and meant a change to playlist CRUD risked breaking tab renderers. Tonearmboy's R.A defined eight narrow interfaces; the concrete `LibraryRepository` implements all eight; `AppGraph` exposes each separately.

**Apply forward:** Phase B defines the narrow interfaces *first*, before the concrete repository. Equivalent shape for pageboy:

| Narrow interface | Methods (rough) | Consumers |
| --- | --- | --- |
| `DocumentSource` | `observe(filter): Flow<List<Document>>`, `observeOne(id): Flow<Document?>`, `search(query): Flow<List<Document>>` | `LibraryScreen` (all 4 tabs), `ReaderScreen` |
| `LibraryScanner` | `scanAll()`, `scanRoot(rootId)`, `observeScanState(): Flow<ScanState>` | Settings rescan button, library refresh, onboarding (future) |
| `LibraryRootStore` | `observeRoots(): Flow<List<LibraryRoot>>`, `addRoot(uri, label, mode)`, `removeRoot(rootId)` | Settings → Source folders screen |
| `PersistedUriPermissionStore` | `observePermissions(): Flow<List<UriPermission>>`, `acquirePermission(uri)`, `releasePermission(uri)` | Internal to `LibraryRootStore` impl; surfaced for diagnostics if needed |
| `ReadProgressStore` | `observeProgress(docId): Flow<ReadProgress?>`, `recordProgress(docId, position)`, `recordOpen(docId)` | `ReaderScreen` (records as user scrolls), `LibraryScreen` Started tab (reads to filter) |
| `BookmarkSource` | `observeBookmarks(docId): Flow<List<Bookmark>>`, `addBookmark(docId, location, label)`, `deleteBookmark(id)` | `ReaderScreen` overflow, `BookmarksScreen` (Phase C) |
| `PinStore` | `observePinned(): Flow<List<DocumentId>>`, `pin(docId)`, `unpin(docId)` | `LibraryScreen` Pinned tab + card overflow |
| `RecentsSource` | `observeRecents(limit: Int = 30): Flow<List<Document>>` | `LibraryScreen` Recents tab |
| `LibraryUiPrefs` | `observeSortOrder()`, `setSortOrder(...)`, `observeFilterFormats()`, `setFilterFormats(...)` (etc.) | `LibraryScreen` sort menu + filter chips |
| `ScanConfigSource` | `observeAutoScanOnStart(): Flow<Boolean>`, `observeShowHidden(): Flow<Boolean>`, `observeScanFormats(): Flow<Set<DocumentFormat>>` | `LibraryScanner` impl reads; Library settings sub-page sets |

The concrete `LibraryRepository` (or whatever it ends up named — could be split as the impl too) implements every interface above. `AppGraph` exposes each interface separately. **No composable takes the whole repository — composables take only the narrow contract they read.**

**Sub-steps (tick when the phase that owns the file lands):**

- [x] **R.A.1** Phase B.1 defines the narrow interfaces above (or whatever subset the work-in-flight needs) in `data/library/` *before* defining the concrete repository. _shipped in commit `54a7dc7`; verified by `solid: phase b audit`._
- [x] **R.A.2** Phase B's concrete repository implements all of them. `AppGraph` exposes each interface separately, marks the concrete class `internal` where the language allows. _shipped in commit `54a7dc7`; `LibraryRepository` is `class` (not `internal`) but only imported from `AppGraph.kt` per audit grep._
- [x] **R.A.3** Phase B's composables take `DocumentSource`, `LibraryRootStore`, etc. — never the concrete repo. _shipped in commit `54a7dc7`; verified by `solid: phase b audit`._
- [ ] **R.A.4** Phase C's `ReaderController` does not import the concrete `LibraryRepository` (takes `DocumentSource` + `ReadProgressStore` + `BookmarkSource`). _pending Phase C._
- [x] **R.A.5** Verify on each phase ship: `grep -rn 'import.*LibraryRepository' app/src/main/java/com/eight87/pageboy/ui` → empty (only doc-comment mentions allowed). _verified in `solid: phase b audit`._

**Effort:** distributed (each phase pays a few minutes). **Risk:** none — discipline-from-day-one is cheap.

---

## Phase R.B pattern — `Setting<T>` + facets, no `SettingsSnapshot`

**Applies to:** every phase that adds a settings sub-page (Phase B adds Library settings; Phase C adds Reader settings; Phase D+ adds per-format settings; Phase H adds Signing settings; etc.).

**Lesson from tonearmboy R.B:** A 826-LOC `SettingsRepository` with 25+ keys via the hand-rolled `stringPreferencesKey + Flow + setter + snapshot field` quartet, plus a 27-field `SettingsSnapshot` projected via a `combine(...)` that every sub-page eagerly subscribed to → toggling theme recomposed the audio screen. Whisperboy proved you can avoid this entirely from day one: `Setting<T>(key, default, encode, decode)` value type + facet interfaces per sub-page.

**Apply forward:** When any phase introduces settings, never build a god `SettingsRepository` in the first place.

- [ ] **R.B.1** First settings-touching phase introduces `Setting<T>` + `EnumSetting<E>` value types in `data/settings/`.
- [ ] **R.B.2** Every key declared via `Setting<T>` — no hand-rolled `stringPreferencesKey + Flow + setter` quartet.
- [ ] **R.B.3** Pageboy facets (provisional — confirm as phases land):
  - `LibrarySettings` — sort order, grid mode, auto-scan on start, show hidden files, format-include filter
  - `ReaderSettings` — font size, theme, scroll mode (paged / continuous), font family
  - `AnnotationSettings` — default annotation color, default tool, signature image
  - `SigningSettings` — default cert, key-store choice
  - `ThemeSettings` — theme mode (light/dark/system), use-dynamic-color
  - `LibraryUiPrefs` — UI-layer prefs distinct from the repository's scan config
- [ ] **R.B.4** No `SettingsSnapshot`. Each sub-page (`SettingsReaderScreen(reader: ReaderSettings)`, etc.) reads only its facet. No `combine(...)` projecting all keys into one fat data class.
- [ ] **R.B.5** UI helpers (e.g. theme picker options) live in `ui/settings/`, not in the data-layer settings repo.

**Effort:** distributed across every settings-adding phase. **Risk:** low (no DataStore migration needed; we're picking the shape from the start).

---

## Phase R.C pattern — `ReaderController` split from day one

**Applies to:** Phase C (reader chrome) + every renderer phase (D–M) that needs reader-side state.

**Lesson from tonearmboy R.C:** One 882-LOC `PlaybackUiController` owned playback connection lifecycle, state projection, transport commands, queue mutation, ReplayGain re-application, sleep timer, position ticker, settings flag mirrors, and `Player.Listener` callbacks — six independent reasons to change. Composables that just wanted `state: StateFlow<PlaybackUiState>` took the whole controller including the library handle (`MiniPlayer` does not need that).

**Pageboy's equivalent risks** (the reader screen has several axes that would naturally fuse if you let them):

- Document opening + lifecycle (renderer-resolve + handle-create + handle-close)
- Scroll/position state + persistence (records to `ReadProgressStore` on scroll-stop debounce)
- Find-in-document state + commands
- Annotation drawing state + commands (Phase G+)
- Signature placement state + commands (Phase H+)
- Share + export commands
- Page-flip / zoom / rotation (per-renderer, but the host owns the UX)

**Apply forward:** Phase C splits `ReaderController` along these axes from day one. Narrow interfaces:

```kotlin
interface ReaderStateProjector       // open / state / close
interface ScrollPersistence          // observe + record scroll position
interface FindInDocCommands          // search + next/prev + clear
interface AnnotationCommands         // draw / erase / undo / redo (Phase G+)
interface SignatureCommands          // stamp / sign / verify (Phase H+)
interface ShareExportCommands        // share / save-as / export-with-annotations
```

The reader chrome takes only what it renders; each per-format renderer takes what it needs and ignores the rest. Phase D (Markdown) needs `ReaderStateProjector` + `ScrollPersistence`; Phase G (PDF annotation) needs `AnnotationCommands`; etc.

- [ ] **R.C.1** Phase C defines the narrow interfaces above (or the subset Phase C ships; later phases extend).
- [ ] **R.C.2** Reader chrome takes interface params, not a god controller.
- [ ] **R.C.3** Phase D+ renderers take only the interfaces they need.
- [ ] **R.C.4** Reader root file under ~200 LOC; per-axis controllers in their own files.
- [ ] **R.C.5** Verify per shipping phase: `find app/src/main/java/com/eight87/pageboy/ui/reader -name '*.kt' -exec wc -l {} \;` — no file past 500 LOC.

---

## Phase R.D pattern — `LibraryScreen` split from day one

**Applies to:** Phase B (library UI).

**Lesson from tonearmboy R.D:** `LibraryScreen.kt` hit 1528 LOC with 5 near-duplicate tab dispatchers, multi-select bar, alphabet scroller, sort comparator factories, `TrackRow`, section headers, empty states — a hot mess. Whisperboy refactored to ~260 LOC scaffold-only by splitting per-tab from day one.

**Apply forward:** Phase B's library UI is split from the first commit:

```
ui/library/
  LibraryScreen.kt              // scaffold + tab dispatch only (~150 LOC target)
  tabs/
    StartedTabScreen.kt
    AllTabScreen.kt
    RecentsTabScreen.kt
    PinnedTabScreen.kt
  DocumentCard.kt               // the per-document card
  LibraryFilterChips.kt         // filter chip row
  LibrarySearchBar.kt           // search input + state
  LibrarySorting.kt             // sort enum + comparator factory (pure, JVM-testable)
  LibraryScanProgressBanner.kt
  LibraryEmptyState.kt
  SourceFoldersScreen.kt        // multi-root management (Settings target)
  AddRootFlow.kt                // SAF picker + label + folder-mode prompt
```

Per-tab files take only the data they need (`DocumentSource` + tab-specific `Flow`). Shared composables (`DocumentCard`, `LibraryFilterChips`) take narrow params.

- [x] **R.D.1** Phase B.9 ships `LibraryScreen.kt` as scaffold + dispatch ONLY. Each tab is its own sibling file. _Original Phase B `LibraryScreen.kt` was 632 LOC with every leaf inlined; `solid: phase b audit` split into 7 files (`LibraryScreen` 295, `DocumentCard` 183, `LibraryFilterChipRow` 83, `LibrarySearchBar` 71, `LibraryScanProgressBanner` 61, `LibraryEmptyState` 54, `LibraryFormatVisuals` 42). Per-tab files were not necessary because the tab dispatch is a one-line `LibraryFilters.byTab` projection — the four tabs share the same chrome, only the filter differs._
- [x] **R.D.2** No single file in `ui/library/` past 400 LOC at Phase B ship. _post-audit max is 295 LOC (`LibraryScreen.kt`)._
- [x] **R.D.3** Per-tab files take `DocumentSource` + tab-specific concerns, not the god repository. _N/A — single scaffold, see R.D.1 note. Each leaf composable takes only its narrow params (R.X.7)._

---

## Phase R.E pattern — `PageboyApp` / nav RouteScope

**Applies to:** Phase A (already shipped) + every phase that adds a nav destination (B adds Reader route, etc.).

**Lesson from tonearmboy R.E:** `TonearmboyApp.kt` hit 820 LOC with every nav destination inline-wired. Whisperboy refactored to 122 LOC by splitting into `RouteScope` + per-grouping `routes/*Destinations.kt` files.

**Phase A status:** shipped clean. Watch for regression as Phase B adds Reader route + Settings → Library route + Settings → Source folders route.

- [ ] **R.E.1** No regression: `app/src/main/java/com/eight87/pageboy/MainActivity.kt` + `MainScreen.kt` stay under 250 LOC combined as new routes land.
- [ ] **R.E.2** When the nav graph approaches ~5 destinations, introduce `RouteScope` + per-grouping `routes/*Destinations.kt`.

---

## Phase R.F — standing polish wins

Catch-all for the smaller wins that aren't big enough to warrant their own phase. Append as discovered. Mirrors tonearmboy R.F + whisperboy R.F.

(Empty for now — wins land here as they emerge.)

---

## Audit log — Phase B

Audit of Phase B commit `54a7dc7` against this plan. Shipped in commit `solid: phase b audit` (this section is the post-audit record).

### Pre-merge checklist (8 items) — verdict per item

1. **R.X.1 narrow interfaces** — PASS. Composables took `DocumentSource` / `LibraryUiSettings` / `LibraryRescanCoordinator` / `PersistedUriPermissionStore` from day one; `LibraryRepository` only imported in `AppGraph.kt`.
2. **R.X.2 sealed dispatch** — PASS (with one fix). `ScanState` and `FolderType` are sealed; `DocumentFormat` / `LibraryTab` / `LibrarySortKey` are flat enums (acceptable — Room round-trip + simple persisted choices). The `when (tab)` switch was duplicated between `LibraryFilters.byTab` and `LibraryScreen.kt`'s inline pipeline — the audit removed the duplicate and routes through `LibraryFilters.byTab`. Format visuals (`formatLabel` / `formatIcon`) were extracted to `LibraryFormatVisuals.kt` so the two `when (format)` switches live in one file instead of being scattered.
3. **R.X.3 composition root** — PASS. Only `AppGraph.kt` references concrete `LibraryRepository` / `AndroidLibraryUiSettings` / `AndroidLibraryRescanCoordinator` / `AndroidPersistedUriPermissionStore`.
4. **R.X.4 file size** — FAIL → FIXED. `LibraryScreen.kt` was 632 LOC (past the 400-LOC R.D.2 threshold). Split into 7 files (see R.D.1 note above). All other Phase B files were under 250 LOC at ship.
5. **R.X.5 NotImplementedError** — PASS. None present. Added a `// Closed by Phase C` annotation to `ReaderScreen.kt`'s doc comment to make the deferral explicit.
6. **R.X.6 wrong-direction imports** — PASS. No `data/` → `ui/` imports; no `ui/library/` ↔ `ui/reader/` cross-imports; no `format/` package exists yet.
7. **R.X.7 Compose ISP** — PASS. `DocumentCard` takes `DocumentEntity` + two callbacks. `LibraryFilterChipRow` takes selection sets + toggle/clear callbacks. `LibraryScanProgressBanner` takes only the `ScanState.Scanning` variant it renders. No god-state leaks.
8. **R.X.8 test discipline** — PARTIAL. Phase B shipped 37 tests across 5 classes (classifier, filter/sort/search, library smoke, folders smoke, retained settings/main smoke). The most load-bearing untested surface was `LibraryRepository.applyScan` (the per-document-state-preserving upsert + soft-delete sweep — the bug surface a future regression would silently corrupt user state through). Audit added `LibraryRepositoryTest` (6 cases, in-memory Room via Robolectric) covering insert-defaults, pinned/lastOpenedAt/read-progress preservation across rescans, soft-delete on disappearance, un-soft-delete on reappearance, root isolation, and `deleteRoot` hard-delete. Remaining untested-but-deferred surfaces: `SafLibraryScanner` (needs an Android `Context` + a fake `DocumentFile` tree — heavy fixture, deferred until a real bug forces it), `AndroidPersistedUriPermissionStore` (DataStore + ContentResolver — same), `AndroidLibraryRescanCoordinator` (one-shot scan loop; integration-tested via the `LibraryScreenSmokeTest` chain). `CachedDocumentFile` is a trivial lazy wrapper — no test.
9. **R.X.9 DocumentRenderer** — DEFERRED to Phase C. `ReaderScreen.kt` is a 62-LOC placeholder with no format dispatch yet, so there's no `when (format)` to refactor and no `DocumentRenderer` interface to define. Phase C lands both (`DocumentRenderer` interface, `FormatRegistry`, the per-axis `ReaderStateProjector` / `ScrollPersistence` / etc. split per R.C). The audit annotated the placeholder's doc comment so the deferral is explicit.

### Test count: 37 → 43 (+6 from `LibraryRepositoryTest`). All green.

### Build: `./gradlew :app:assembleDebug` green. APK budget unchanged.

---

## Pre-merge checklist (every implementation agent runs this before committing)

Before any `git commit` that lands new code:

1. **R.X.1** Any composable / ViewModel taking a god-handle? Refactor to narrow interface.
2. **R.X.2** Any `when (format)` / `when (mode)` / `when (filter)` scattered across the call sites? Refactor to sealed dispatch or registry.
3. **R.X.3** Any concrete class referenced outside the composition root? Refactor to interface.
4. **R.X.4** Any file past 500 LOC? Justify or split. Past 800 LOC? Almost certainly split.
5. **R.X.5** Any `NotImplementedError` not accompanied by an inline `// closed by Phase X` comment? Add the comment or close the deferral.
6. **R.X.6** Any wrong-direction import (`data/` importing `ui/`, etc.)? Block the commit.
7. **R.X.7** Any Compose leaf taking a god-state? Pass the fields.
8. **R.X.8** New public-surface file without a test? Add the test.

Agents report compliance per item in their phase-completion summary.

---

## Discipline-shipping note for subagents

When you ship a phase that touches the SOLID surface (most do), include in your phase-completion report:

- New file LOC for any file past 200 LOC (R.X.4 awareness).
- The narrow interfaces you defined (R.A application).
- Any `Setting<T>` keys you added + the facet they live on (R.B application).
- Any controller you split (R.C application).
- Pre-merge checklist results (8 items above).

Catching SOLID drift in the report is much cheaper than catching it in a 1–2-day refactor later.
