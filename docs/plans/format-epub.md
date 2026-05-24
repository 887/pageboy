# pageboy — EPUB renderer plan

## Status: 🟡 RECOMMENDED — review pending

## Recommendation

**Parser + renderer:** [Readium Kotlin Toolkit](https://github.com/readium/kotlin-toolkit) v3.2.0 (May 13 2026), specifically `readium-shared` + `readium-streamer` + `readium-navigator`. SPDX [`BSD-3-Clause`](https://spdx.org/licenses/BSD-3-Clause.html). Maven Central group `org.readium.kotlin-toolkit`.

**Architectural fork verdict:** **WebView-based rendering via Readium Navigator for v1.** Compose-native rendering is a documented "if and when" follow-up, not a v1 goal — see the rationale section below. EPUB content is HTML+CSS+SVG+(optionally)MathML; the long tail of real-world EPUBs uses enough rare CSS that a hand-rolled Compose layout will fumble for the bottom 5–10% of the user's library, which is the part that hurts most when it breaks (the books the user actually cares about are usually the weird ones — academic PDFs-exported-to-EPUB, fiction with elaborate chapter ornaments, comics-flavoured fixed-layout, etc.).

Readium is:

- The reference open-source EPUB toolkit, used by Aldiko, Bluefire, Thorium, and the W3C's own conformance work.
- Active in 2026 (3.2.0 shipped 2026‑05‑13, develop branch sees commits regularly).
- Apache-friendly license (BSD-3 — on pageboy's allowlist).
- Comes with a baked-in WebView renderer (`EpubNavigatorFragment`) that already implements: CFI locators, ToC navigation, reflowable + fixed-layout, font-size / font-family / theme / background-color preferences, RTL + vertical text, page-turn animations, decoration overlays (the substrate for highlights), and JS-disabled content sandboxing.

The library carries non-trivial dependency weight (`androidx.media3-*` because Readium also covers audiobooks; `androidx.legacy-support-core-ui`; `org.jsoup`) — addressed in the APK-size section. The wins (correctness on the long tail, CFI persistence, fixed-layout, font-customization that interleaves with publisher CSS the way EPUB-3 spec describes) decisively outweigh the cost vs DIY.

## Alternatives considered

### `epublib` (`nl.siegmann.epublib:epublib-core` / `psiegman/epublib`) — REJECTED

- **License:** [LGPL-2.1-or-later](https://spdx.org/licenses/LGPL-2.1-or-later.html). **Disqualifying.** Pageboy's allowlist is MIT / Apache-2.0 / BSD-2 / BSD-3 / MPL-2.0 only (`oss-licenses.md`). LGPL imposes relinking obligations that are awkward in an Android APK distribution model.
- Even if license were OK: psiegman/epublib is not on Maven Central proper (hosted on the maintainer's own `mvn-repo` GitHub repo), parser-only (no renderer), and maintenance is sporadic.

### `FolioReader-Android` (`com.folioreader:folioreader`) — REJECTED

- License: BSD-3-Clause (would be allowed).
- Last release **0.5.4 on 2019-01-11**. Six years stale, 248 open issues. Internally depends on the *pre-3.x* Readium 2 (`r2-streamer-kotlin`, `r2-navigator-kotlin`), which is itself archived. Bringing this in is a maintenance trap.

### `Book's Story` (`Acclorite/book-story`) — REJECTED as a library; useful as a reference

- License: **GPL-3.0**. Disqualifying for vendoring.
- It's an *app*, not a published library. Compose-native rendering of EPUB exists in the wild here — interesting prior art if pageboy ever ports to Compose-native, but cannot be linked.

### Hand-rolled minimal parser + DIY WebView — PARTIALLY ADOPTED AS FALLBACK PATH

EPUB is well-specified: container.xml → OPF package document → `<manifest>` (resources) + `<spine>` (reading order) + `<guide>` (legacy) + nav doc. The minimum-viable parser is ~200–400 LoC of Kotlin around `java.util.zip.ZipFile` + `kotlinx.serialization-xml` (or just a SAX cursor).

This is a credible fallback if Readium's deps shrink the APK budget too much under R8 — pageboy could ship its own `MinimalEpubParser` feeding a stock `WebView` configured by hand. But:

- CFI position math is non-trivial and error-prone to roll.
- Fixed-layout pagination is hard.
- Font obfuscation (EPUB-3 IDPF + Adobe schemes) requires SHA-1 over the publication UID, which is fiddly but doable.
- Theming user prefs on top of publisher CSS is the most subtle part, and it's exactly the place Readium has spent years tuning.

**Decision:** start with Readium; keep the seam (`EpubRenderer` behind `DocumentRenderer`) clean enough that swapping to `MinimalEpubParser` later costs one weekend, if the APK budget forces it.

### Pure Compose-native rendering — DEFERRED to post-v1

Pros (theme tokens flow, smaller RSS, no WebView attack surface, deterministic typography) are real but the long-tail correctness problem is severe. Revisit once pageboy has user data on what fraction of the user's library renders fine under a curated CSS subset.

## APK-size budget

Per-format budget for EPUB: **2–3 MB** (within the seed's 1–3 MB acceptable range).

Composition (rough, post-R8, arm64-v8a):

| Component | Size |
|---|---|
| `readium-shared` AAR (incl. jsoup transitive ~400 KB) | ~700 KB |
| `readium-streamer` AAR | ~300 KB |
| `readium-navigator` AAR (incl. WebView JS/CSS assets, ~500 KB raw) | ~1.4 MB |
| `androidx.webkit` (likely already present elsewhere) | ~50 KB net |
| `androidx.media3-*` transitives — **see mitigation** | ~600–900 KB if not excluded |

**Mitigation — exclude unused transitives:** `readium-navigator` pulls in `androidx.media3-session`, `media3-common-ktx`, and `media3-exoplayer` because the Navigator umbrella covers audiobooks. Pageboy only needs the EPUB navigator. The Gradle dependency declaration should look like:

```kotlin
implementation("org.readium.kotlin-toolkit:readium-shared:3.2.0")
implementation("org.readium.kotlin-toolkit:readium-streamer:3.2.0")
implementation("org.readium.kotlin-toolkit:readium-navigator:3.2.0") {
    exclude(group = "androidx.media3")
    // Audiobook nav we don't use; keep an eye on R8 to confirm it really tree-shakes.
}
```

Verify post-R8 the media3 classes are absent via `:app:dependencies` + `bundletool dump apk` size diff. If R8 keeps them, file an issue upstream (Readium has discussed splitting navigator artifacts).

**`org.jsoup:jsoup` (1.22.2)** is a hard requirement — ~400 KB, used by Readium for HTML walking. No reasonable swap. Counts toward both EPUB and any other format that wants HTML parsing (Markdown HTML pass-through, DOCX pivot, etc.) so the marginal cost amortizes.

**`androidx.legacy-support-core-ui`** is a small concern (drags some old AppCompat surface). R8 strips most. Net: +~50 KB.

**Net realistic EPUB delta with media3 excluded and R8 enabled: ~2.5 MB.** Within budget. Universal APK stays well under the 50 MB ceiling.

## JVM-vs-native

**Pure JVM.** No JNI. Readium is Kotlin + a thin layer of HTML/CSS/JS assets that ship inside the AAR and load into Android's system WebView at render time. The WebView itself is the system component (~50 MB shared system process, doesn't count against APK).

No per-ABI splitting needed for EPUB. (PDF will need per-ABI; EPUB does not.)

## Performance characteristics

- **Open a 500-page (~5 MB) reflowable EPUB:** Readium opens lazily — it parses container.xml + OPF + nav doc on `open()` (sub-second on the AVD baseline), then loads spine items into the WebView one at a time as the user navigates. First-page render ~200–500 ms on `medium_phone` AVD, dominated by WebView warm-up (which Android caches between activities; second open of pageboy ≈ 100 ms).
- **Per-spine-item paint:** ~50–200 ms depending on item size and embedded fonts.
- **Streaming model:** yes. `Publication` exposes resources via a `Container` abstraction; the navigator pulls them on demand. Pageboy never has to hold the whole book in memory.
- **Memory:** the system WebView dominates (~30–60 MB process RSS once warm). The Readium overhead on top is small (~5–10 MB for parsed manifest + nav). Acceptable for an e-reader.
- **Fixed-layout EPUBs** are pre-rendered as a `WebView` swipe stack — heavier but still streaming.

## Android-version compatibility

- Readium 3.2.0 declares `minSdk 23`. Pageboy's `minSdk 28` is comfortably above this.
- **Core library desugaring** must be enabled in the `:app` module's Gradle config (`compileOptions { coreLibraryDesugaringEnabled = true }` + `coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:…")`). This is a hard Readium requirement and one of the easier configuration mistakes to make — call it out in the implementation checklist.
- `compileSdk` should be 34+; Readium 3.2.0 builds against compileSdk 36.

No compatibility shims required beyond enabling desugaring.

## Maintenance status

- **Active.** 3.2.0 on 2026-05-13 (10 days before this research). Develop branch shows regular commits. 43 open issues (typical for an active library). Backed by [readium.org](https://readium.org), an industry consortium (EDRLab is the primary maintainer).
- BSD-3-Clause — well-understood, no surprises.
- Security: no current advisories. WebView surface is the security gate (see spec gotchas).

Red-flag check: no. Green-flag check: yes — this is the de facto reference implementation, and it's still under active development by a funded org.

## Spec gotchas

Per the seed's "three most likely places a wrong-shaped document will crash" rule, ranked by likelihood:

### 1. Fixed-layout EPUBs (comics, illustrated books)

EPUB 3 fixed-layout uses `<meta property="rendition:layout">pre-paginated</meta>` in the OPF + per-spine `<itemref properties="rendition:page-spread-…">`. Readium handles this in its FXL navigator pathway, but pageboy must *route* fixed-layout publications to the right navigator presentation (paginated, no reflow, swipe spread). Mis-routing → text-sized comic pages or vice versa.

**Pageboy v1 posture:** detect fixed-layout (Readium exposes `publication.metadata.presentation.layout`), render via Readium's FXL pathway, document in release notes that font-size preferences silently no-op for FXL books. Do **not** declare FXL out of scope — it's free with Readium and users will throw comics at the reader.

### 2. Font obfuscation (`META-INF/encryption.xml`)

EPUB 2/3 specs allow obfuscated fonts: the first 1040 bytes of an OTF/WOFF are XOR'd against a key derived from either:
- the IDPF scheme (SHA-1 of the unique-identifier from OPF, mangled per algorithm), or
- the Adobe scheme (similar, different algorithm).

Readium **handles both transparently** via the streamer's resource transformer. Pageboy gets this for free. *Decision: NOT in scope for v1 to re-implement; rely on Readium.*

### 3. EPUB-3 JavaScript and remote resources — security gate

EPUB 3 spec permits inline `<script>` and remote resource loading (CSS @import, iframe). Readium's WebView Navigator **defaults to JS disabled** and isolates the WebView per spine item. Pageboy must:

- Verify `WebSettings.setJavaScriptEnabled(false)` is the effective default (Readium sets it; assert in a Robolectric test that it stays off).
- Block remote network loads with a `WebViewClient.shouldInterceptRequest()` that returns 403 for any non-`publication://` URL. Readium provides a hook; wire it.
- Disable file:// access (`setAllowFileAccess(false)`, `setAllowContentAccess(false)`).
- These are the load-bearing security gates. Document them in a Phase-M sub-step.

### Honourable mentions

- **EPUB 2 ToC** lives in `toc.ncx` (`application/x-dtbncx+xml`). **EPUB 3 ToC** lives in an XHTML nav doc (`<nav epub:type="toc">`). Readium normalizes both to its own `LocatorCollection`. Pageboy's ToC drawer consumes the normalized form; no special-casing needed.
- **`epub:type` attribute** semantics (bodymatter, chapter, footnote, etc.) Readium exposes them but doesn't impose UX. Pageboy can use `epub:type="footnote"` to drive a popover UX, eventually; v1 just renders inline.
- **CFI (Canonical Fragment Identifier).** Spec at [w3c/epub-specs](https://w3c.github.io/epub-specs/epub33/epubcfi/). Readium computes CFIs in its `Locator` model. Persist `locator.toJSON()` (string) per book in Room; restore on open via `EpubNavigatorFragment.go(locator)`.
- **Embedded fonts.** Render with the publication's fonts if present; Readium handles this. User prefs to override (force system font) → set `fontFamily` preference; Readium injects user CSS at top of cascade.
- **DRM (Adobe ACS, Kobo, Apple FairPlay).** Out of scope for v1. Documented. (Readium *does* support LCP DRM via `readium-lcp` + `liblcp` blob — but `liblcp` is a proprietary closed-source binary distributed by EDRLab, **not on pageboy's license allowlist**, so do not depend on it.)
- **Mathematics (MathML).** EPUB 3 permits MathML. System WebView renders MathML on Android 13+ via Chromium engine; on Android 9–12 it renders as fallback (boxes). Acceptable for v1, document.

## Architectural integration

```kotlin
// app/src/main/java/com/eight87/pageboy/format/epub/EpubRenderer.kt
class EpubRenderer(
    private val streamer: Streamer,                  // org.readium.r2.streamer.Streamer
    private val publicationOpener: PublicationOpener,
) : DocumentRenderer {

    override suspend fun open(source: DocumentFile): RenderResult { ... }

    // open() returns a EpubRenderResult.Ready(publication, navigatorFragmentFactory)
    // The UI layer hosts the EpubNavigatorFragment inside a Compose AndroidView /
    // FragmentContainerView; pageboy supplies the Compose chrome (top bar, ToC drawer,
    // settings sheet) around it.
}
```

The Composable wrapper hosts the Readium fragment, the way Material 3 Expressive apps embed legacy fragments in 2026. The settings sheet writes `EpubPreferences` JSON (font, theme, etc.) and pushes it through `EpubPreferencesEditor`.

Persistence: `EpubReadingPositionEntity(documentId: String, locatorJson: String, updatedAt: Instant)` in Room. Bookmarks/highlights piggyback on the same locator model in a separate overlay table, matching the pattern `format-pdf.md` is being written to use. CFI is opaque from the UI's perspective — only the renderer and the locator codec touch it.

## User-facing features mapped to Readium APIs

| Feature | Readium API |
|---|---|
| Font size | `EpubPreferences.fontSize` |
| Font family override | `EpubPreferences.fontFamily` (+ `publisherStyles=false`) |
| Background / dark mode | `EpubPreferences.theme = Theme.DARK` or custom `backgroundColor` |
| ToC navigation | `Publication.tableOfContents` → `Navigator.go(locator)` |
| Reading position | `Navigator.currentLocator` (StateFlow) → persist; restore via `Navigator.go(locator)` |
| Bookmarks | persist `Locator`; restore on tap |
| Highlights | `Decoration` API (`navigator.applyDecorations(...)`) |
| Search | `Publication.search(query)` returns `LocatorCollection`; FTS index optional |

## Phase M — implementation

- [ ] **M.1** Add Maven Central dependencies for `readium-shared`, `readium-streamer`, `readium-navigator` v3.2.0; exclude `androidx.media3-*` transitives; enable core library desugaring in `:app` (`coreLibraryDesugaringEnabled = true` + `desugar_jdk_libs`). Verify post-R8 APK delta is in the 2–3 MB band; record in `format-epub.md` under "APK delta — measured".
- [ ] **M.2** Create `format/epub/` package: `EpubRenderer` (implements `DocumentRenderer`), `EpubReadingPosition` (Room entity + DAO), `EpubPreferencesStore` (DataStore wrapper that round-trips `EpubPreferences` to JSON).
- [ ] **M.3** Wire `FormatDetector` to route `.epub` extension + magic-byte `PK\x03\x04` + `META-INF/container.xml` presence to `EpubRenderer`.
- [ ] **M.4** Implement `EpubRenderer.open(source: DocumentFile)`: open SAF URI as a `Resource`, hand to Readium `PublicationOpener.open(asset)`, return `RenderResult.Ready(publication)` or `RenderResult.Unsupported(reason)` for fixed-layout-only books if FXL support slips.
- [ ] **M.5** Build the Compose host: `EpubReaderScreen` that embeds `EpubNavigatorFragment` via `AndroidView { FragmentContainerView(...) }` with `FragmentManager` from the activity. Top bar, ToC drawer, settings sheet are pageboy-native Compose surfaces (no Readium UI dependencies).
- [ ] **M.6** Implement reading-position persistence: observe `navigator.currentLocator` `StateFlow`, debounce 500 ms, persist `locator.toJSON()` to Room. On reopen, restore via `navigator.go(locator)`.
- [ ] **M.7** Implement ToC drawer: read `publication.tableOfContents`, render as a `LazyColumn`, tap → `navigator.go(link.locator)`.
- [ ] **M.8** Implement preferences sheet (font size slider, font family picker — system + 2–3 bundled fonts, theme toggle Light/Dark/Sepia, background-color picker). Write through `EpubPreferencesEditor`, persist `EpubPreferences` JSON in DataStore keyed by document id.
- [ ] **M.9** Security hardening: assert `setJavaScriptEnabled(false)` is effective via a Robolectric test; wire a `WebViewClient.shouldInterceptRequest` that 403s any non-`publication://` URL; disable file:// and content:// access on the WebView; add a unit test that loading an EPUB with an embedded `<script>` does not execute the script.
- [ ] **M.10** Fixed-layout detection: in `EpubRenderer.open()`, branch on `publication.metadata.presentation.layout == EpubLayout.FIXED` to surface a `RenderMode.FixedLayoutSpread` to the UI host; the UI suppresses font-size controls in that mode.
- [ ] **M.11** Bookmarks overlay: `EpubBookmarkEntity(documentId, locatorJson, label, createdAt)` Room table. Add bookmark via floating action in the top bar; list view in the drawer.
- [ ] **M.12** Highlights overlay (depends on M.11 schema): `EpubHighlightEntity(documentId, locatorJson, color, note?, createdAt)`; render via Readium's `Decoration` API.
- [ ] **M.13** ToC + spine smoke test on AVD: ship 3 fixtures into `app/src/androidTest/assets/epub/` — one EPUB 2 (epublib's `bookmarktest.epub`-style), one EPUB 3 reflowable, one EPUB 3 fixed-layout (a 4–6 page sample). Robolectric tests parse + open + extract first spine item; instrumentation test on `emulator-5554` opens each in pageboy and screenshots the first page.
- [ ] **M.14** Document scope-exclusions in the README + settings About: no DRM (Adobe ACS, Kobo, LCP, FairPlay), no in-EPUB JavaScript execution, MathML rendering depends on system WebView version.
- [ ] **M.15** Update `oss-licenses.md` with the Readium BSD-3-Clause entry and jsoup MIT entry; verify Licensee plugin picks them up.
- [ ] **M.16** Mark this plan `## Status: ✅ DONE` and tick the corresponding `main.md` Phase M header once M.1–M.15 are all shipped, with the jj change ID noted on the phase header.

## Sources

- [Readium Kotlin Toolkit (GitHub)](https://github.com/readium/kotlin-toolkit) — license, modules, current activity
- [`readium-shared` on Maven Central](https://central.sonatype.com/artifact/org.readium.kotlin-toolkit/readium-shared) — v3.2.0, deps, license
- [`readium-navigator` on Maven Central](https://central.sonatype.com/artifact/org.readium.kotlin-toolkit/readium-navigator) — v3.2.0, deps including androidx.webkit + media3
- [Readium Navigator preferences guide](https://readium.org/kotlin-toolkit/latest/guides/navigator/preferences/) — font, theme, background, fixed-layout settings
- [EPUB CFI 1.1 spec (W3C)](https://w3c.github.io/epub-specs/epub33/epubcfi/) — canonical fragment identifier model
- [BSD-3-Clause SPDX](https://spdx.org/licenses/BSD-3-Clause.html)
- [LGPL-2.1-or-later SPDX](https://spdx.org/licenses/LGPL-2.1-or-later.html) — why epublib is disqualified
- [psiegman/epublib (GitHub)](https://github.com/psiegman/epublib) — LGPL parser, considered + rejected
- [FolioReader-Android (GitHub)](https://github.com/FolioReader/FolioReader-Android) — stale since 2019, rejected
- [Acclorite/book-story (GitHub)](https://github.com/Acclorite/book-story) — GPL-3 Compose-native EPUB app, useful prior art, not linkable
- [Librera Reader (GitHub)](https://github.com/foobnix/LibreraReader) — reference reader; EPUB engine sits behind a closed `libPro` module, not directly informative for licensing
