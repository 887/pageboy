# pageboy — DOCX renderer plan

## Status: 🟡 RECOMMENDED — review pending

_Sibling plan: [`format-xlsx.md`](format-xlsx.md). The two share library landscape (Apache POI dominates OOXML) so they were researched together. A parallel ODT/ODS pair owns [`format-odt.md`](format-odt.md) + [`format-ods.md`](format-ods.md); the shared-plumbing opinion at the bottom of this file is the OOXML side's proposal — consolidation is later._

## Recommendation

**Apache POI 5.5.1 via [`centic9/poi-on-android`](https://github.com/centic9/poi-on-android) `poishadow-all`** (POI itself: [Apache-2.0](https://spdx.org/licenses/Apache-2.0.html); the shadow project is also Apache-2.0). Use the `poi-ooxml` + `poi-scratchpad` surface, with [`com.fasterxml:aalto-xml`](https://github.com/FasterXML/aalto-xml) (Apache-2.0) providing the StAX implementation Android does not ship. Set the three `org.apache.poi.javax.xml.stream.*` system properties at process start before any POI class touches an `XMLInputFactory`.

- **APK delta with R8** (pageboy-side estimate, to confirm during Phase I.1): **~3.5–4.5 MB compressed** added to the universal-debug APK after aggressive shrinking. Unshrunk the shadow jar is on the order of 16–18 MB (poi-ooxml + poi-ooxml-lite schemas + xmlbeans + aalto + a sliver of commons-compress already pulled in for ZIP work). Most of the savings come from R8 stripping XmlBeans schema fragments that DOCX-only rendering never references (chart schemas, SpreadsheetML, presentation, formula, etc.). If R8 cannot meaningfully shrink it (it has historically struggled with XmlBeans-generated code that uses reflection), the worst-case settled APK delta is **~10 MB**, which still fits inside the 50 MB universal ceiling but eats the per-format moderate budget. **Validate at I.1 with `./gradlew :app:bundleRelease` and the APK analyzer; if delta > 6 MB, fall back to the hand-rolled extractor (Plan B below).**
- **Pure JVM** — no native code, no per-ABI binaries, ships in the universal APK identically across all four ABIs.

The decision is not "POI is good"; the decision is "POI is the only library with sustained 2026 maintenance that covers DOCX's long tail of inline shapes, drawing-XML alternates, OLE placeholders, and `mc:AlternateContent` markup-compatibility blocks without re-inventing five years of bug-fixing." The hand-rolled extractor (Plan B) is the fallback if the APK delta lands unacceptable.

## Alternatives considered

### docx4j 11.x — **rejected**

[`docx4j`](https://www.docx4java.org/) (current: 11.5.13, May 2026, [Apache-2.0](https://spdx.org/licenses/Apache-2.0.html)) is the other mature Java DOCX library. The Plutext author maintains [`plutext/Docx4j4Android4`](https://github.com/plutext/Docx4j4Android4) as a proof of concept that has historically been one major Android version behind, and the runtime drags in **JAXB** plus a slew of XML stacks (Saxon-HE optional, jakarta.xml.bind, Apache FOP fragments) that Android's stripped `javax.xml.*` does not expose. Android still does not ship the JAXB classes natively in 2026, and the workaround jars are larger than the POI shadow. Net APK delta would land north of POI's at no win on the feature surface pageboy cares about (read-only render of paragraphs, runs, tables, images). Rejected.

### `fast-excel` (dhatim/fastexcel) — **not applicable to DOCX**

XLSX-only. Mentioned here only so it's not re-raised — see [`format-xlsx.md`](format-xlsx.md) where it IS a serious candidate.

### `poi-android` (SUPERCILEX fork) — **rejected**

[`SUPERCILEX/poi-android`](https://github.com/SUPERCILEX/poi-android) last released **November 2022** targeting POI 3.17. Five years stale, no security backports. Apache-2.0 license is fine; the library state is not. The actively-maintained shadow is [`centic9/poi-on-android`](https://github.com/centic9/poi-on-android) at POI 5.5.1.

### Hand-rolled minimal extractor — **rejected as primary, kept as Plan B**

DOCX is a ZIP of XML; the rendering subset pageboy needs (paragraphs, runs with bold/italic/underline, headings via `w:pStyle`, bullet lists, tables, embedded image references, fall-through on alternate-content + drawing-xml + OLE) is on the order of **1,500–2,500 LOC of Kotlin** using `kotlinx-serialization-xml` or a SAX cursor on top of `java.util.zip`. The price is correctness against malformed-in-the-wild documents (which POI has fifteen years of fixes for) and against the `mc:AlternateContent` MCE choice/fallback selection logic — the latter is documented but easy to get subtly wrong (silently dropping fallback content when no choice matches). Plan B if POI's settled APK delta lands > 6 MB after R8.

### Headless LibreOffice on-device — **rejected**

LibreOffice viewer-core (Collabora's mobile flavour) is the only viable on-device LO embedding and ships 60+ MB of native code per ABI plus a stripped office runtime. Pageboy's 50 MB universal ceiling makes this a non-starter. Documented so it's not re-asked.

## License compatibility

| Library | SPDX | Allowlist hit |
|---|---|---|
| Apache POI 5.5.1 (`poi-ooxml`, `poi-ooxml-lite`, `poi-scratchpad`, `xmlbeans`) | [Apache-2.0](https://spdx.org/licenses/Apache-2.0.html) | yes |
| `centic9/poi-on-android` shadow project | Apache-2.0 (inherits) | yes |
| `com.fasterxml:aalto-xml` 1.4.0 | [Apache-2.0](https://spdx.org/licenses/Apache-2.0.html) | yes |
| `stax:stax-api` 1.0.1 (transitive of the shadow) | Apache-2.0 / CDDL-1.0 dual | Apache-2.0 leg satisfies allowlist |
| `commons-compress` (transitive, ZIP I/O) | Apache-2.0 | yes |
| `log4j-api` (POI uses it for diagnostics) | Apache-2.0 | yes |

No AGPL / GPL / LGPL / EPL contamination in the dependency closure. All pass [`oss-licenses.md`](oss-licenses.md).

## APK-size budget

Working numbers, to confirm at I.1 with the APK analyzer once the renderer is actually wired:

- `poishadow-all` jar before shrinking: **~16–18 MB** (POI + poi-ooxml-lite XmlBeans schemas + aalto + transitive bits).
- After R8 with aggressive shrinking + the keep-rules below: **target ~3.5–4.5 MB compressed delta** to the APK.
- Hard local ceiling for DOCX alone: **6 MB**. If unshrunk landings exceed this, switch to Plan B.
- The OOXML pair (DOCX + XLSX) shares POI between them — the **second** format only adds the schema fragments specific to its content type, so the marginal cost of XLSX on top of DOCX is small (~1–1.5 MB). Budget the pair as one item.

Keep-rules will need to:
- Keep `org.apache.poi.xwpf.**` (XWPF — Word OOXML).
- Keep `org.openxmlformats.schemas.wordprocessingml.**` plus the schemas POI's XWPF actually loads at runtime.
- Strip `org.apache.poi.hssf.**` (binary XLS) — pageboy does not support .doc/.xls.
- Strip `org.apache.poi.hslf.**`, `org.apache.poi.xslf.**` (PowerPoint).
- For DOCX-only build flavour, also strip `org.apache.poi.xssf.**` — but with XLSX in the same APK (Phase J), keep XSSF and add the XSSF-specific schemas.

R8 keep-rules for POI live in `app/proguard-rules.pro`; reference the [`centic9/poi-on-android` proguard module](https://github.com/centic9/poi-on-android/tree/master/poishadow) as a starting point and tighten from there.

## JVM vs native

Pure JVM. No JNI. No per-ABI binaries. The XML parsing path is aalto-xml (Java); ZIP is `java.util.zip`. This is the major win over docx4j (same shape, larger transitive closure) and over headless LibreOffice (massive native deltas per ABI).

## Performance characteristics

POI's XWPF reader builds an in-memory document model up front. For "every Word doc the user has ever seen" the model is small enough to fit comfortably (typical letter-format docs are < 100 KB on disk, model overhead is single-digit MB on heap). For pathological large docs (200+ pages with embedded images), the in-memory model can grow to 50–100 MB on heap; pageboy mitigates this by:

- Parsing on a `Dispatchers.IO` coroutine, never on Main.
- Holding the parsed `XWPFDocument` in a per-document `androidx.lifecycle.ViewModel` scope so it's released when the user leaves the reader.
- Rendering page-at-a-time into a `LazyColumn` from a flattened block list — POI's tree is walked once into a Kotlin `RichTextDocument` (see "Shared with ODT/ODS?" below), and that flat list is what the Compose layer consumes.

Latency target: **a 50-page DOCX opens to first paint in < 800 ms on the AVD `medium_phone`**. To validate at I.5.

There is no streaming reader for DOCX in POI — XWPF is fully in-memory by design. (XLSX has `SXSSF` for streaming-write and `xlsx-streamer` for streaming-read; DOCX does not have a streaming equivalent because Word's `document.xml` is one big serialized tree without the XLSX sheet-shard convenience.) If pathological-doc heap pressure becomes a real problem in the wild, the long-term fix is to drop to a hand-rolled SAX cursor.

## Android-version compatibility (minSdk 28)

- **StAX:** Android does not ship `javax.xml.stream.*` factories. The fix is well-trodden: bundle `com.fasterxml:aalto-xml` and set three system properties **before any POI code runs**, ideally in `PageboyApplication.onCreate()`:
  ```kotlin
  System.setProperty(
    "org.apache.poi.javax.xml.stream.XMLInputFactory",
    "com.fasterxml.aalto.stax.InputFactoryImpl",
  )
  System.setProperty(
    "org.apache.poi.javax.xml.stream.XMLOutputFactory",
    "com.fasterxml.aalto.stax.OutputFactoryImpl",
  )
  System.setProperty(
    "org.apache.poi.javax.xml.stream.XMLEventFactory",
    "com.fasterxml.aalto.stax.EventFactoryImpl",
  )
  ```
  POI 5.x respects these `org.apache.poi.javax.xml.stream.*` overrides explicitly so the Android stripped runtime never gets asked for the missing factories. ([centic9/poi-on-android README](https://github.com/centic9/poi-on-android/blob/master/README.md))
- **`java.awt.*`:** Android does not provide it. POI's XWPF surface for *reading* and laying out text avoids `java.awt` for the most part, but anything that touches `Color`, `Font`, `BufferedImage`, or `Graphics2D` will `NoClassDefFoundError`. The shadow project replaces a handful of these; coverage is documented as "incomplete." Pageboy's renderer must be defensive: catch `NoClassDefFoundError` at the per-block render boundary and skip that block with a placeholder, not crash the document.
- **JAXB:** poi-ooxml does not strictly need JAXB; XmlBeans handles the schemas. (This is the major reason POI is more Android-tractable than docx4j.)
- **Reflection / `MethodHandles`:** XmlBeans uses runtime reflection on its generated classes. R8 keep-rules must preserve the schema classes — naive shrinking removes the schema implementations the runtime tries to instantiate.
- **minSdk 28:** confirmed compatible. The shadow project documents `minSdkVersion 26`; we go higher.

## Maintenance status (as of May 2026)

- **Apache POI:** very active. Latest release line is 5.4.x / 5.5.x ([Maven Central poi-ooxml 5.5.1](https://mvnrepository.com/artifact/org.apache.poi/poi-ooxml/5.5.1)). Security responses ship regularly (CVE-bound XmlBeans schema-poisoning fixes were the most recent in this lineage).
- **centic9/poi-on-android:** maintained at a slower cadence but tracks POI majors. Latest release **5.2.5-4** (Apr 2024) targets POI 5.2.5; the `master` `build.gradle` for `poishadow` currently references POI **5.5.1** — meaning a fresh `gradle build` against the repo will pull current POI. ([poi-on-android poishadow/build.gradle](https://github.com/centic9/poi-on-android/blob/master/poishadow/build.gradle)) **Action item:** vendor the shadow build into pageboy's `buildSrc` so we control the POI version pin and do not depend on the upstream cutting a release.
- **`SUPERCILEX/poi-android`:** stale since Nov 2022. Not recommended.
- **`com.fasterxml:aalto-xml`:** active. Current stable 1.4.0.

## Spec gotchas (the three most likely production crashes)

1. **`mc:AlternateContent` (Markup Compatibility Extensions).** Word writes new-and-old markup in pairs: `<mc:AlternateContent><mc:Choice Requires="wps14">…modern shape…</mc:Choice><mc:Fallback>…legacy shape or text…</mc:Fallback></mc:AlternateContent>`. Naive parsers either choose both (duplicated content) or choose neither (silent content drop). Pageboy's rule: **always select the `mc:Fallback` branch in v1.** POI's XWPF does this correctly when accessed via the `XWPFParagraph` / `XWPFRun` API; if dropping to raw schemas, the selection logic is the renderer's responsibility. ([OpenOffice OOXML/MCE notes](https://wiki.openoffice.org/wiki/OOXML/Markup_Compatibility_and_Extensibility))
2. **Drawing-XML embedded vector graphics (`<w:drawing>` → DrawingML).** Inline shapes, charts embedded as drawings, SmartArt, WordArt. POI exposes them as `XWPFPicture` only when the drawing is a simple picture; everything else is a sub-tree pageboy cannot render in v1. **Decision: render any non-picture drawing as a `[shape]` placeholder block.** Never attempt to rasterize in v1. If the drawing wraps an EMF/WMF metafile, render the `<w:txbxContent>` text caption if present; otherwise placeholder.
3. **Embedded OLE objects (`<o:OLEObject>`).** Old "embed an Excel spreadsheet in a Word doc" relics. POI surfaces them as `XWPFOleObject`-shaped opaque blobs. **Decision: always display as `[embedded <type>]` placeholder.** Never instantiate, never crash, never attempt to invoke the embedded format's renderer cross-module in v1.

Secondary / documented decisions:
- **Tracked changes (`w:ins`, `w:del`, `w:moveFrom`, `w:moveTo`):** display "final without markup" in v1 — strip `w:del` content, keep `w:ins` content as if it were normal text. Tracked-changes rendering (strike-through deletions, underline insertions, author colors) is a future phase.
- **Comments (`comments.xml` part):** show a single margin indicator dot in v1; comment text is not rendered. Phase I.7 may upgrade to a sidebar.
- **Headers / footers / page numbers:** render headers at the top of the first block; render footers at the end. Do not attempt to repeat-per-page (pageboy reflows, page boundaries do not exist in the rendered output). Page numbers in `{ PAGE }` fields render as nothing.
- **Fields (`{ DATE }`, `{ TOC }`, etc.):** render the cached result text in `w:fldSimple` / between `w:fldChar` boundaries. Do not re-evaluate. TOC field renders as cached link list.
- **Embedded fonts:** ignore in v1; use the system font. Embedded-font extraction is a future phase.
- **RTL + complex script (Arabic / Hebrew / Indic):** Compose `Text` handles bidi by default. Do not introduce a custom shaper.

## Shared with ODT/ODS?

The ODT/ODS agent owns the symmetric opinion from their side. The OOXML-side proposal:

**Yes, share an intermediate document model for the text-document pair (DOCX + ODT).** Two parsers, one renderer. Sketch:

```kotlin
package com.eight87.pageboy.format.text

// Flat block list — Compose renders it as a LazyColumn.
// Parsers (DOCX, ODT) produce this; the renderer never sees XML.
data class RichTextDocument(
  val title: String?,
  val blocks: List<Block>,
)

sealed interface Block {
  data class Paragraph(val runs: List<Run>, val style: ParaStyle = ParaStyle.Body) : Block
  data class Heading(val runs: List<Run>, val level: Int /* 1..6 */) : Block
  data class BulletList(val items: List<List<Run>>, val ordered: Boolean) : Block
  data class Table(val rows: List<List<TableCell>>) : Block
  data class Image(val sourceRef: String /* part name or odf entry */, val widthEm: Float?) : Block
  data class Placeholder(val kind: PlaceholderKind, val label: String) : Block
  data object PageBreak : Block
}

data class Run(
  val text: String,
  val bold: Boolean = false,
  val italic: Boolean = false,
  val underline: Boolean = false,
  val strikethrough: Boolean = false,
  val link: String? = null,
)

data class TableCell(val blocks: List<Block>, val colSpan: Int = 1, val rowSpan: Int = 1)
enum class ParaStyle { Body, Quote, Code }
enum class PlaceholderKind { Drawing, OleObject, Chart, EmbeddedDoc, UnknownXml }
```

Compose renderer lives in `format/text/render/` and consumes `RichTextDocument` agnostically. `format/docx/DocxParser.kt` and `format/odt/OdtParser.kt` each produce one. The `DocumentRenderer` instances for DOCX and ODT are thin shells that wire parser to renderer.

The renderer is the load-bearing shared code — it's also where the bidi, link-tap, image-load, and selection logic lives, all of which are non-trivial and we do not want to write twice. Two parsers feeding one renderer is the right SRP split.

The spreadsheet pair (XLSX + ODS) gets the same treatment: see [`format-xlsx.md`](format-xlsx.md) for the `SpreadsheetModel` proposal.

## Phase I — implementation

- [ ] **I.1** Wire `org.apache.poi:poi-ooxml:5.5.1` + `org.apache.poi:poi-scratchpad:5.5.1` + `com.fasterxml:aalto-xml:1.4.0` + `stax:stax-api:1.0.1` into `app/build.gradle.kts` via the `centic9/poi-on-android` shadow recipe. Vendor the shadow build into `buildSrc/` so we control the POI pin. Reference: [poi-on-android `poishadow/build.gradle`](https://github.com/centic9/poi-on-android/blob/master/poishadow/build.gradle). **Verify APK delta with the APK analyzer immediately** — abort to Plan B (hand-rolled extractor) if delta > 6 MB shrunk.
- [ ] **I.2** Set the three `org.apache.poi.javax.xml.stream.*` system properties in `PageboyApplication.onCreate()` **before** any POI class loads. Robolectric test in `format/docx/PoiStaxBootstrapTest.kt` asserts `XMLInputFactory.newInstance()` returns the Aalto impl after bootstrap.
- [ ] **I.3** Define the `RichTextDocument` / `Block` / `Run` data classes in `format/text/model/` per the sketch in "Shared with ODT/ODS?" above. **Owned jointly with the ODT agent** — coordinate on `Block` sealed-class shape before either parser lands. Unit test: round-trip a hand-built `RichTextDocument` through a JSON snapshot.
- [ ] **I.4** Write `DocxParser.kt` in `format/docx/`. Open the SAF `InputStream` as `OPCPackage`, walk `XWPFDocument`'s body elements, emit `Block`s. Handle: `XWPFParagraph` → `Paragraph` / `Heading` (detect via `w:pStyle` referencing `Heading1`..`Heading6`), `XWPFTable` → `Table`, `XWPFNumbering` → `BulletList`, picture-shaped `XWPFRun` embedded image → `Image`, anything else → `Placeholder`. **`mc:AlternateContent` is selected by POI internally to the `mc:Fallback` branch when accessed via XWPF; verify with a fixture.**
- [ ] **I.5** Compose renderer `format/text/render/RichTextDocumentRenderer.kt`. `LazyColumn`-driven. Each `Block` type maps to one composable. Bidi handled by Compose `Text` default. Image loads via Coil from a `ContentResolver`-backed `ImageLoader` keyed on the document URI + part name. **Target: 50-page DOCX opens to first paint < 800 ms on AVD `medium_phone`** — measure with `Trace.beginSection`/`endSection` from open to first `LazyColumn.firstVisibleItemIndex == 0` paint.
- [ ] **I.6** Implement `DocxDocumentRenderer : DocumentRenderer` in `format/docx/`. Thin adapter: SAF `Uri` → `DocxParser` → `RichTextDocument` → `RichTextDocumentRenderer`. Returns `RenderResult.Unsupported(reason)` (per the LSP rule in [`CLAUDE.md`](../../CLAUDE.md)) when POI throws or the document is encrypted (POI's `EncryptedDocumentException`). **Never let a parse exception escape into Compose.**
- [ ] **I.7** R8 / ProGuard keep-rules in `app/proguard-rules.pro`. Keep `org.apache.poi.xwpf.**`, `org.openxmlformats.schemas.wordprocessingml.**`, `org.apache.poi.openxml4j.opc.**`, `org.apache.poi.poifs.**`, `schemaorg_apache_xmlbeans.system.**` (the runtime-loaded XmlBeans schema bundles), `com.fasterxml.aalto.**`. Strip `org.apache.poi.hssf.**`, `org.apache.poi.hslf.**`, `org.apache.poi.xslf.**`. Co-located with XLSX rules from Phase J (keep `xssf` then). Verify with the APK analyzer that the shrunk dex actually drops the stripped packages.
- [ ] **I.8** Robolectric fixtures in `app/src/test/resources/format/docx/`: (a) `minimal.docx` (one paragraph, one heading), (b) `tables-and-images.docx`, (c) `alternate-content.docx` (a `mc:AlternateContent` with a Word-2010-shape choice + text fallback — assert the fallback wins), (d) `ole-and-drawing.docx` (assert both render as `Placeholder`, not crashes), (e) `tracked-changes.docx` (assert `w:ins` is kept, `w:del` is dropped, "final without markup" mode).
- [ ] **I.9** Catalog DOCX in the Licenses screen per [`oss-licenses.md`](oss-licenses.md). Confirm Licensee picks up POI, XmlBeans, aalto, stax-api as Apache-2.0 / dual-licensed (stax-api).
- [ ] **I.10** UI verification on AVD `emulator-5554` per the [`CLAUDE.md`](../../CLAUDE.md) UI-verification rule. Open `tables-and-images.docx` via the SAF picker; `adb exec-out screencap` shows the document rendered, table cells visible, image placeholder boxes in place. Confirm `mc:AlternateContent` and OLE-placeholder fixtures render without crash via the same loop.
- [ ] **I.11** Tick the Phase I header in [`main.md`](main.md) with the jj change ID (or git SHA — see [`CLAUDE.md`](../../CLAUDE.md)) once I.1–I.10 are all ticked. Mark this file `## Status: ✅ DONE`.
