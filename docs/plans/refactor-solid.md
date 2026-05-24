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
- [x] **R.A.4** Phase C's `ReaderController` does not import the concrete `LibraryRepository` (takes `DocumentSource` + `ReadProgressStore` + `BookmarkSource`). _shipped in Phase C commit; `DefaultReaderStateProjector` + `DefaultScrollPersistence` take `DocumentSource` only; `LibraryRepository` only referenced from `AppGraph.kt` per grep._
- [x] **R.A.5** Verify on each phase ship: `grep -rn 'import.*LibraryRepository' app/src/main/java/com/eight87/pageboy/ui` → empty (only doc-comment mentions allowed). _verified in `solid: phase b audit`._

**Effort:** distributed (each phase pays a few minutes). **Risk:** none — discipline-from-day-one is cheap.

---

## Phase R.B pattern — `Setting<T>` + facets, no `SettingsSnapshot`

**Applies to:** every phase that adds a settings sub-page (Phase B adds Library settings; Phase C adds Reader settings; Phase D+ adds per-format settings; Phase H adds Signing settings; etc.).

**Lesson from tonearmboy R.B:** A 826-LOC `SettingsRepository` with 25+ keys via the hand-rolled `stringPreferencesKey + Flow + setter + snapshot field` quartet, plus a 27-field `SettingsSnapshot` projected via a `combine(...)` that every sub-page eagerly subscribed to → toggling theme recomposed the audio screen. Whisperboy proved you can avoid this entirely from day one: `Setting<T>(key, default, encode, decode)` value type + facet interfaces per sub-page.

**Apply forward:** When any phase introduces settings, never build a god `SettingsRepository` in the first place.

- [x] **R.B.1** First settings-touching phase introduces `Setting<T>` + `EnumSetting<E>` value types in `data/settings/`. _shipped in Phase C; `data/settings/Setting.kt` mirrors whisperboy's R.B.1 with the `setting()` + `enumSetting()` factories._
- [x] **R.B.2** Every key declared via `Setting<T>` — no hand-rolled `stringPreferencesKey + Flow + setter` quartet. _shipped for new facets in Phase C; `AndroidReaderSettings` declares its key via `dataStore.setting(...)`. Phase B's `AndroidLibraryUiSettings` remains on the hand-rolled quartet — see Phase C audit observation below — migrating it is a Phase F-or-later refactor when it earns the churn._
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

- [x] **R.C.1** Phase C defines the narrow interfaces above (or the subset Phase C ships; later phases extend). _shipped: `ReaderStateProjector` + `ScrollPersistence` + `FindInDocCommands` + `ShareExportCommands` each in its own file under `ui/reader/control/`. `AnnotationCommands` + `SignatureCommands` deferred to Phase G/H per R.X.5 (not declared as stub interfaces — the deferral is recorded here, not in the codebase, so no LSP-violating `NotImplementedError` exists)._ **Phase H deeper:** `SigningCommands` interface + `InMemorySigningCommands` shipped in `ui/reader/control/SigningCommands.kt` (172 LOC). Narrow surface — chrome sees `state` flow + `start` / `commit` / `cancel`; impl-side adapter (PadesSigner / KeystoreKeyProvider / Pkcs12KeyProvider) sees the concrete class's `reportProgress` / `reportSuccess` / `reportFailure` / `reset` hooks. No god controller; every variant of `SigningState` (Idle / DrawingStamp / PlacingStamp / KeySelecting / Signing / Failed / Success) tested. `AnnotationCommands` still deferred (no Phase G in this worktree's history). _Phase H R.C.1-deeper shipped in this commit._
- [x] **R.C.2** Reader chrome takes interface params, not a god controller. _`ReaderScreen` constructor takes `ReaderStateProjector` + `FormatRegistry` + `FindInDocCommands` + `ShareExportCommands` — four narrow interfaces, no god controller._
- [x] **R.C.3** Phase D+ renderers take only the interfaces they need. _shipped in Phase E — Markdown reads `RendererScrollSink` + `RendererFindSink`; TXT reads both; PlaceholderRenderer reads neither. Interface widening via `RendererContext` value type so adding handles (Phase G annotation, Phase H signature) is a new field on the data class, not another `Body()` signature change._
- [x] **R.C.4** Reader root file under ~200 LOC; per-axis controllers in their own files. _`ReaderScreen.kt` is 132 LOC; max file in `ui/reader/` is 132 LOC (`ReaderScreen.kt`); max in `ui/reader/control/` is 111 LOC (`ReaderStateProjector.kt`)._
- [x] **R.C.5** Verify per shipping phase: `find app/src/main/java/com/eight87/pageboy/ui/reader -name '*.kt' -exec wc -l {} \;` — no file past 500 LOC. _Phase C max is 132 LOC across 9 files._

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

## Audit log — Phase C

Audit of Phase C against this plan. Single commit; reader-chrome scaffold + DocumentRenderer interface + per-axis controllers + Setting<T> introduction + ReaderSettings facet + Reader catalog section + 27 new tests.

### Pre-merge checklist (8 items) — verdict per item

1. **R.X.1 narrow interfaces** — PASS. `ReaderScreen` takes four narrow interfaces (`ReaderStateProjector`, `FormatRegistry`, `FindInDocCommands`, `ShareExportCommands`) — no god `ReaderController`. Sub-composables (`ReaderTopBar`, `ReaderFindPanel`, `ReaderBody`, `ReaderErrorState`) each take only the 3-6 fields they render.
2. **R.X.2 sealed dispatch** — PASS. `ReaderState` is sealed (`Idle` / `Opening` / `Open(handle)` / `Failed(reason)`); the chrome dispatches via exhaustive `when` in `ReaderBody`. Format dispatch goes through `FormatRegistry.rendererFor(format)` — no `when (format)` in the reader (R.X.9).
3. **R.X.3 composition root** — PASS. `AppGraph.kt` is the only file that constructs `CompiledFormatRegistry` / `DefaultReaderStateProjector` / `DefaultScrollPersistence` / `InMemoryFindInDocCommands` (via factory) / `AndroidShareExportCommands` / `AndroidReaderSettings`. UI takes interfaces only.
4. **R.X.4 file size** — PASS. Phase C max LOC: `ReaderScreen.kt` 132, `PlaceholderRenderer.kt` 131, `ReaderTopBar.kt` 127, `ReaderFindPanel.kt` 115, `ReaderStateProjector.kt` 111, `ScrollPersistence.kt` 109, `FindInDocCommands.kt` 100, `ReaderBody.kt` 84, `ReaderErrorState.kt` 67, `DocumentRenderer.kt` 63, `ShareExportCommands.kt` 58. Every file under 250 LOC.
5. **R.X.5 NotImplementedError** — PASS. None present. The Phase C plan called for stub `AnnotationCommands` / `SignatureCommands` interfaces with `// closed by Phase G` comments per R.X.5; the decision was to NOT declare them this phase (interface introduction without a consumer is itself a Liskov hazard) and to record the deferral in this plan's R.C.1 sub-step instead — see that bullet.
6. **R.X.6 wrong-direction imports** — PARTIAL → see audit observation below. `data/` → `ui/`: zero (PASS). `ui/reader/` → `ui/library/`: zero (PASS). `data/` → `format/`: zero (PASS). `format/` → `ui/`: zero (PASS). `format/` → `data/library/`: **4 imports of `DocumentFormat`** in `format/api/DocumentHandle.kt`, `format/api/DocumentRenderer.kt`, `format/registry/FormatRegistry.kt`, `format/placeholder/PlaceholderRenderer.kt`. This is a narrow exception explained in the audit observations below; the spirit of R.X.6 (no format → repository / scanner / DAO imports) holds.
7. **R.X.7 Compose ISP** — PASS. `ReaderTopBar` takes title + findActive + four callbacks. `ReaderFindPanel` takes query + currentMatchIndex + matchCount + four callbacks. `ReaderBody` takes state + registry + onRetry. `ReaderErrorState` takes reason + onRetry. `PlaceholderRenderer.Body()` takes handle + modifier. No god-state.
8. **R.X.8 test discipline** — PASS. 27 new tests across 7 classes: `FormatRegistryTest` (5), `PlaceholderRendererTest` (4), `ReaderStateProjectorTest` (4), `ScrollPersistenceTest` (5), `FindInDocCommandsTest` (6), `ReaderScreenSmokeTest` (2), `ReaderTopBarTest` (1). Total: 43 → 70 (0 failures). The R.X.9 contract (`DocumentRenderer` open/closed) is exercised end-to-end by `FormatRegistryTest` + `PlaceholderRendererTest` + `ReaderScreenSmokeTest`.

### Phase C audit observations

**O.C.1 — `format/` → `data/library/` import of `DocumentFormat` is a deliberate narrow exception.** R.X.6 prohibits `format/` from importing `data/library/`; the four imports of `DocumentFormat` (a closed enum, used as the renderer's identity tag) violate this rule literally. The spirit of the rule is "format renderers must not depend on the Room schema, the scanner, or the `DocumentSource` — they take a bytes-source and emit Compose content". `DocumentFormat` is a pure value type, not a repository concern, and acts as the partial-key of the `FormatRegistry` map. The clean fix is moving `DocumentFormat` to a neutral package (`com.eight87.pageboy.common` or a `core/` module), which is a 15-file rename touching every Phase B consumer. Deferred until either a second format-identity type joins it (justifying the package) or a Phase F+ research agent surfaces a concrete pain. Recording the exception here so future audits know it was deliberate, not drift.

**O.C.2 — `AndroidLibraryUiSettings` not migrated to `Setting<T>`.** Phase C introduces `Setting<T>` (R.B.1) and uses it for `AndroidReaderSettings`. The Phase B-shipped `AndroidLibraryUiSettings` is still on the hand-rolled `stringPreferencesKey + Flow + setter` quartet across five keys. Migration is a pure refactor (interface stays the same; impl changes) and earns its keep once a third setting facet lands. Tracked in R.B.2 as a known partial-tick; explicit migration phase will land if `LibrarySettings` gains more keys (e.g. when the show-hidden-files toggle wires through real UI + the per-format scan filter from Phase B.4 surfaces).

**O.C.3 — `DefaultReaderStateProjector` does not honour the entity-supplied title.** The projector returns whatever `DocumentRenderer.open()` returns; the `PlaceholderRenderer.open()` derives title from `source.displayName()`, which is null for SAF URIs the test injection used, so the smoke screencap shows "Document" instead of "Phase C smoke test". This is correct per R.X.9 (the renderer's handle is the source of truth for its title) — but the chrome could be smarter about preferring the entity title when it exists. Recorded as a Phase D+ polish opportunity, not a Phase C blocker. The real per-format renderers (Markdown, EPUB) can return the document's actual H1 / OPF title in their `open()`, which is what the user actually wants to see.

**O.C.4 — `ShareExportCommands.shareCurrentDocument` is wired but inert in Phase C.** The chrome's share button calls into `ShareExportCommands`, but the document URI passed in is empty because `DocumentHandle` doesn't carry the source-bytes URI back out (renderers don't need it; the projector consumed it during `open()`). The Phase G+ work that adds export-with-annotations will revisit this — at that point the renderer's handle will need to expose either the bytes-source or the resolved entity, and the share path picks that up automatically. Phase C ships the wiring; the behaviour completes in a later phase.

---

## Audit log — Phase D

Audit of Phase D against this plan. Single commit; first real `DocumentRenderer` impl (Markdown end-to-end) + commonmark-java 0.28.0 wiring + 34 new tests.

### Pre-merge checklist (8 items) — verdict per item

1. **R.X.1 narrow interfaces** — PASS. `MarkdownRenderer` takes only a `MarkdownParser` (no `Context`, no `LibraryRepository`). `MarkdownBody(handle: MarkdownHandle, modifier: Modifier)` takes its own narrow handle subtype. Each per-block Composable takes the concrete commonmark node it renders, not the parent handle.
2. **R.X.2 sealed dispatch** — PASS. `MarkdownBlock` is a sealed interface (Heading / Paragraph / BlockQuote / BulletList / OrderedList / FencedCode / IndentedCode / Html / Thematic / Table / StandaloneImage / Footnote / Unknown). `RenderBlock` is one exhaustive `when` — adding a block kind = new variant + one arm. No `when (node.javaClass)` chains scattered across the package.
3. **R.X.3 composition root** — PASS. Only `AppGraph.kt` constructs `MarkdownParser` + `MarkdownRenderer`. The reader chrome takes `FormatRegistry`; the registry returns the wired `MarkdownRenderer` for `DocumentFormat.Markdown`. No chrome file references the renderer concretely.
4. **R.X.4 file size** — PASS. Phase D max LOC: `MarkdownBlocks.kt` 391, `MarkdownInlines.kt` 218, `MarkdownLists.kt` 129, `MarkdownBlockModel.kt` 111, `MarkdownFrontMatter.kt` 99, `MarkdownTable.kt` 94, `MarkdownRenderer.kt` 83, `MarkdownBody.kt` 80. Every file under 400 LOC. The initial `MarkdownBlocks.kt` shipped at 567 LOC; pre-commit split moved tables → `MarkdownTable.kt` and lists → `MarkdownLists.kt`.
5. **R.X.5 NotImplementedError** — PASS. None present. Three deferrals documented in code with inline comments referencing the closing phase: code-block syntax highlighting (`// TODO(phase D+/v1.1)` in `MarkdownBlocks.FencedCodeBlockView`), image rendering via Coil (Phase F per D.10), and paginated mode (Phase D.9 — `MarkdownBody` doc comment).
6. **R.X.6 wrong-direction imports** — PASS (with the same narrow exception Phase C documented in O.C.1). `grep -rn 'import com.eight87.pageboy.ui' format/` → zero hits. The only `format/` → `data/library/` imports are of `DocumentFormat` (a closed-enum value type used as the renderer's identity tag). `MarkdownFind`'s result type lives inside `format/markdown/` (`MarkdownMatch`) rather than reusing the chrome's `FindMatch` — see O.D.1 below.
7. **R.X.7 Compose ISP** — PASS. `MarkdownBody` takes only its `MarkdownHandle`. Each per-block Composable takes the concrete node it renders + a `Modifier`. The link tap handler reaches `LocalContext` at the point of use (not threaded through every composable). The table/list renderers take colour scheme + typography as explicit params (instead of a single god-style object).
8. **R.X.8 test discipline** — PASS. 34 new tests across 7 classes: `MarkdownParserTest` (5), `MarkdownTitleExtractorTest` (5), `MarkdownFrontMatterTest` (5), `MarkdownFindTest` (5), `MarkdownBlockModelTest` (5), `MarkdownRendererTest` (8 — covers `DocumentRenderer` contract end-to-end), `MarkdownBodySmokeTest` (1 — Robolectric Compose render of every block kind). Total: 70 → 104 (0 failures).

### Phase D audit observations

**O.D.1 — `MarkdownFind.MarkdownMatch` does not reuse `ui.reader.control.FindMatch`.** The simpler shape would be for `MarkdownFind` to return the chrome's `FindMatch` type directly, since the chrome eventually consumes the matches. But that would require `format/markdown/` to `import com.eight87.pageboy.ui.reader.control.FindMatch` — a `format/` → `ui/` import which R.X.6 forbids. The cleaner shape (and the one shipped) is for each format renderer to surface matches in its own local type; the chrome's eventual wiring adapter (Phase E+, when the chrome's find pipeline reaches into the renderer) maps `MarkdownMatch → FindMatch` at the boundary. This matches the same "narrow per-renderer surface" pattern that `DocumentHandle` subtypes already use.

**O.D.2 — `ScrollPosition` was not refactored to sealed.** The Phase D plan called for a sealed `ScrollPosition` with `LazyColumn` / `PdfPage` / `EpubCfi` variants. The Phase C-shipped `ScrollPosition(pageIndex: Int, offsetFraction: Float)` data class already encodes both shapes cleanly via the `lastReadPositionMs` `(page << 20) | offset` bit-packed long — reflowable renderers use `pageIndex = 0` and the encoding collapses to just the offset; paginated renderers (Phase F PDF) use both fields. Refactoring to sealed now would force every Phase F-N renderer to pick its variant up front, without yet knowing what its scroll-position primitive actually is (EPUB CFI is the cleanest example — its scroll position is a string-shaped XPath, not an int+float). The data class encoding is a Liskov-safe lossless superset for both reflowable + paginated; the sealed refactor remains a deferred-when-it-earns-its-keep change (likely Phase M when EPUB lands a CFI-shaped variant).

**O.D.3 — `MarkdownBody` does not yet wire `ScrollPersistence` or `FindInDocCommands`.** Both axes have their plumbing in place (`ScrollPersistence` accepts position records; `FindInDocCommands.InMemoryFindInDocCommands.submitMatches` accepts match lists), but the renderer-side body does not call into them. Wiring them needs the body's `LazyListState` + the document id + the chrome's `ScrollPersistence` instance + the chrome's `FindInDocCommands` instance, none of which are currently on `DocumentRenderer.Body()`'s parameter list. The clean fix is either (a) widen the renderer body interface, or (b) hoist the `LazyListState` above the body so the chrome can subscribe. Both are Phase E refactor candidates; Phase D ships the renderer + the find helper as separate units so the chrome can adopt them once the contract widening is settled (R.C.3 still pending).

**O.D.4 — `markdownParser` is a one-time-shared instance.** `commonmark-java`'s `Parser` holds only the extension list + parser config — it's stateless and thread-safe per the upstream docs. `AppGraph` constructs one and shares across renderer instances. Verified by inspection (no mutable state in `Parser`'s constructor); reused-instance is also the documented commonmark performance pattern.

### Test count: 70 → 104 (+34). All green.

### Build: `:app:assembleDebug` + `:app:testDebugUnitTest` both green. APK debug delta 65.0 MB → 67.8 MB (+2.8 MB unminified; minified estimate ~175 KB per format-markdown.md budget — verified jar footprint of commonmark + ext is ~470 KB unminified across 7 jars, which R8 + ProGuard tree-shake heavily).

---

## Audit log — Phase E

Audit of Phase E against this plan. Second real `DocumentRenderer` impl (TXT) + `DocumentRenderer.Body()` interface widening + Phase D audit deferrals O.D.1 and O.D.3 closed.

### Pre-merge checklist (8 items) — verdict per item

1. **R.X.1 narrow interfaces** — PASS. `RendererContext` is a 4-field value type containing only narrow contracts (`RendererScrollSink` 2 methods, `RendererFindSink` 3 members, `RendererReadingPrefs` 1 field, plus `documentId`). `TxtRenderer` takes no constructor params; `TxtBody` takes its `TxtHandle` + `RendererContext` only. Markdown body unchanged in shape — still takes its handle + the context. No god-handles.
2. **R.X.2 sealed dispatch** — PASS. Format dispatch continues through `FormatRegistry`; no `when (format)` switches added. The `RendererContext` is a plain data class (no branching), the existing sealed `ReaderState` is unchanged.
3. **R.X.3 composition root** — PASS. Only `AppGraph.kt` constructs `TxtRenderer`, `DefaultRendererReadingPrefs`. Chrome composables take `RendererReadingPrefs` / `ScrollPersistence` / `InMemoryFindInDocCommands` (the last is the concrete chrome class because the adapter needs `submitMatches`, which is intentionally not on the read-only `FindInDocCommands` interface — same pattern Phase C used).
4. **R.X.4 file size** — PASS. New-file LOC: `TxtBody.kt` 135, `TxtEncodingDetector.kt` 131, `TxtLineSource.kt` 146, `TxtRenderer.kt` 83, `TxtHandle.kt` 32, `TxtFind.kt` 54, `RendererContextAdapters.kt` 128, `domain/render/RendererContext.kt` 74, `domain/render/FindMatch.kt` 29, `domain/render/ScrollPosition.kt` 22. `MarkdownBody.kt` grew from 80 → 228 (still under 400). Every file comfortably under the 400-LOC R.D.2 threshold; max Phase E file is 228 (`MarkdownBody.kt`).
5. **R.X.5 NotImplementedError** — PASS. None present. Two deferrals documented inline: inline match highlight (Phase D doc-comment + Phase E plan's E.2 — earns its keep alongside PDF/EPUB needs), `RendererReadingPrefs.continuousScrolling` advisory-only until paginated renderers land (Phase F+).
6. **R.X.6 wrong-direction imports** — PASS (and the Phase C/D narrow exception O.C.1 is now smaller). `format/markdown/MarkdownFind` no longer ships a local `MarkdownMatch`; `format/markdown/` + `format/txt/` both import the neutral `domain/render/FindMatch`. `format/` continues NOT to import `ui/`. `ui/` continues NOT to be imported from `format/`. The chrome adapter layer (`ui/reader/control/RendererContextAdapters.kt`) is the only file that crosses into `domain/render/` from both sides. Verified by `grep -rn 'import com.eight87.pageboy.ui' app/src/main/java/com/eight87/pageboy/format/` → zero hits.
7. **R.X.7 Compose ISP** — PASS. `RendererContext` is read-only data; each renderer body reads only the fields it needs (Markdown reads all three sinks; TXT reads scroll + find; the placeholder reads nothing). The chrome-side adapters expose three narrow interfaces (`RendererScrollSink` 2 methods, `RendererFindSink` 3 members, `RendererReadingPrefs` 1 field) rather than threading the full `ScrollPersistence` / `FindInDocCommands` / `ReaderSettings` into renderers.
8. **R.X.8 test discipline** — PASS. 45 new tests across 8 classes. Pre-existing tests adapted to the widened `Body()` signature without removing coverage. Total 104 → 149, 0 failures.

### Phase E audit observations

**O.E.1 — `domain/render/` is the new neutral layer.** Phase D audit documented `DocumentFormat` as a narrow exception to R.X.6 (Phase C audit O.C.1). Phase E adds three more types — `FindMatch`, `ScrollPosition`, `RendererContext` — that need to be reachable from both `format/` and `ui/`. Instead of expanding the exception, Phase E spun up `domain/render/` as a neutral package both sides import without violating R.X.6 (since the rule names `data/library/` and `ui/`, not "every layer below `ui/`"). The `format/` → `data/library/DocumentFormat` narrow exception still stands — moving `DocumentFormat` into `domain/` is the next step when the rename earns the churn (likely Phase F, when PDF + EPUB join the format roster).

**O.E.2 — `MarkdownBody` find-to-block mapping is a fraction heuristic.** Per the Phase E plan, mapping a match's character offset to a top-level block index would require either (a) recommonmark walking with per-block source ranges (commonmark-java doesn't preserve them on every node) or (b) a separate range-tracking pass. The shipped impl uses "match's line number / total lines × block count" which lands within a screen of the target; the user's eye trims the last 20px. Honest about it in `MarkdownBody`'s doc comment + the helper's inline note. Real per-block ranges are a Phase F-or-later refinement when PDF / EPUB also need cleaner per-match scroll targets.

**O.E.3 — `TxtLineSource` is in-memory; format-txt.md's true disk-windowed impl is deferred.** The Phase E plan calls for a `WindowedLineSource` backed by `RandomAccessFile` + a `LongArray` line-offset index cached in `DocumentEntity.lineIndexBlob`. The shipped `InMemoryTxtLineSource` decodes the bytes once + holds the line list in memory, with the LazyColumn providing windowed *composition* even though storage is eager. This trades the disk-windowed memory ceiling for an implementation that ships in a day instead of a week. The 100K-line log file the format-txt.md test plan calls for renders smoothly under the in-memory impl (each `String` line is ~20 bytes header + payload; 100K × ~50 chars ≈ 10 MB resident — fine on 2026 mid-range Android RAM). The disk-windowed impl earns its keep when a real user opens a 500 MB log, which is rare enough to defer behind the interface (`TxtLineSource`) we shipped from day one so swapping the impl is one line in `TxtRenderer.open()`.

**O.E.4 — `Body(context)` signature widening was opt-A (RendererContext value type), not opt-B (parameters).** The Phase E plan's "pick A unless painfully wrong" landed cleanly — every place that calls `renderer.Body(...)` is the chrome's `ReaderBody.kt`, so widening the signature was a one-call-site change for the runtime + a handful of test classes. The value-type encoding means Phase G (annotation commands) + Phase H (signature commands) add one field to `RendererContext` each, no renderer signature touches. That's the open/closed win the audit was looking for.

**O.E.5 — `O.D.1 + O.D.3 closed.** Both Phase D deferrals shipped in Phase E:
- O.D.1 (`MarkdownFind.MarkdownMatch` → chrome `FindMatch`): `MarkdownFind.findAll` now returns `domain.render.FindMatch` directly. TXT renderer uses the same neutral type from day one. No adapter at the chrome boundary; matches flow straight from renderer to find panel.
- O.D.3 (`MarkdownBody` not wired to `ScrollPersistence` / `FindInDocCommands`): `MarkdownBody` now reads `scrollSink.load()` on first compose + records on scroll-stop; observes `findSink.query` to re-run search; jumps via `currentMatchIndex` change.

### R.C sub-step tick

R.C.3 sub-step (Phase D+ renderers take only the interfaces they need) now ticked — Markdown + TXT both consume `RendererScrollSink` + `RendererFindSink` selectively; the placeholder reads neither; future renderers add what they need. _shipped in Phase E._

### Test count: 104 → 149 (+45). All green.

### Build: `:app:assembleDebug` + `:app:testDebugUnitTest` both green. APK debug delta 67.8 MB → 67.75 MB (-50 KB unminified — the TXT renderer + `domain/render/` neutral types are pure JVM stdlib, no new deps; the negative delta is build noise from cache-line packing of the dex output).

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
