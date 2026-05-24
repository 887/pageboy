# pageboy — ODS renderer plan

## Status: 🟡 RECOMMENDED — review pending

## TL;DR

Ship a **hand-rolled minimal ODS parser** as v1, mirroring the ODT decision. ODS files are
ZIP archives of the same XML family as ODT; the spreadsheet content lives in
`content.xml` under `<office:spreadsheet>` → `<table:table>` → `<table:table-row>` →
`<table:table-cell>`. The read-only subset pageboy needs — extract **cached cell values**
(never evaluate formulas), render the grid windowed, honour merged cells and frozen
panes — is ~500–700 LOC over the same `XmlPullParser` + `ZipInputStream` plumbing the
ODT parser uses. The reference library (Apache ODF Toolkit) is overkill for the same
reasons documented in [format-odt.md](format-odt.md) (~17 MB dep tree, Xerces dex
conflict history, Jena + BouncyCastle for capabilities a viewer never uses).

## Recommendation

**Hand-rolled parser** in `format/ods/` over the platform `XmlPullParser` +
`java.util.zip.ZipInputStream`, **reusing** `ZipDocumentSource` from
`format/common/` (introduced by either the ODT or DOCX phase — whichever lands first).

- **APK delta:** **~12 KB** (Kotlin source only).
- **License:** N/A (own code, repo MIT).
- **JDK / minSdk:** API 28+ trivially.
- **Coverage targets (v1):** cell values (string / number / date / boolean) read from
  the `office:value` attribute (the **cached** value — formulas are ignored), merged
  cells (`table:number-columns-spanned`, `table:number-rows-spanned`), frozen panes
  (`table:table-header-rows` / `table:table-header-columns`), multi-sheet workbooks
  with a sheet switcher in the reader chrome, named ranges surfaced as a "Go to…"
  navigation list.
- **Not v1:** formula evaluation, charts (`<chart:chart>` → placeholder),
  conditional formatting (rendered as the cached style only; recomputation is out of
  scope), pivot tables, data-validation dropdowns, embedded OLE / images at cell
  scope (placeholder), macros.
- **Render target:** the parser emits a `SpreadsheetModel` (see "Shared with
  DOCX/XLSX?") that a Compose `SpreadsheetGridView` consumes — shared with XLSX.

## Alternatives considered

### Apache ODF Toolkit (`org.odftoolkit:odfdom-java:0.13.0`) — REJECTED

Full analysis lives in [format-odt.md](format-odt.md#apache-odf-toolkit-orgodftoolkitodfdom-java0130---rejected).
TL;DR for ODS:

- **License:** [Apache-2.0](https://spdx.org/licenses/Apache-2.0.html) — ✅ allow-listed.
- **APK cost:** ~17 MB unshrunk / 8–12 MB R8'd. Per the prompt's hard 50 MB whole-app
  ceiling and the per-format moderate budget of 2–4 MB, this blows the budget alone.
- **Maintenance (2026):** active under TDF, 0.13.0 shipped 2026-01-23.
- **Spreadsheet-specific API:** odfdom's `OdfSpreadsheetDocument` exposes the table
  model via DOM `org.w3c.dom.Node` walks. Not stream-friendly for the windowed
  rendering pageboy wants for 100k+ row sheets — we'd have to roll a SAX layer on top
  of odfdom anyway, at which point odfdom is just dead weight.
- **Bonus rejection reason:** odfdom does not evaluate formulas either — it just hands
  you the cached `office:value`. So we'd pay the full APK cost for *exactly the same
  capability* the hand-roll gets us. No upside.

### Apache POI (`org.apache.poi:poi-ooxml`) — NOT APPLICABLE

POI does not read ODS (it's the OOXML / OLE-CFB sibling). Mentioned only because it
will appear in the XLSX research and the parallel agent may wonder about reuse. None.

### JOpenDocument — REJECTED

Historical spreadsheet support is decent, but the project is dormant and the licensing
(LGPL / GPL-w-classpath on the andiwand fork, license unclear on the asplinsol fork)
fails the [oss-licenses.md](oss-licenses.md) gate (MIT / Apache-2.0 / BSD-2 / BSD-3 /
MPL-2.0 only).

### Hand-roll over `XmlPullParser` — RECOMMENDED

- **The pitch:** spreadsheet XML is structurally simpler than text XML — it's
  rows-of-cells with a small fixed attribute vocabulary. No nested paragraph runs, no
  list-style chains. Easier to parse than ODT.
- **Cost:** ~3–4 days for v1 coverage (smaller than ODT). Heavy on test fixtures
  because the long tail of real-world ODS quirks (column repetition,
  cell-style inheritance) needs explicit cases.
- **What we lose:** no formula evaluation. We display whatever LibreOffice / Calc
  computed and cached at last save (`office:value` attribute). For a viewer, this is
  the correct call — see the "Formula vs cached value" gotcha below.

## APK-size budget

| Approach | Universal APK delta | Notes |
|---|---|---|
| Hand-rolled parser (recommended) | **~12 KB** | pure Kotlin, shares ZipDocumentSource |
| odfdom-java 0.13.0 (R8'd) | **8–12 MB** | unused if both ODT + ODS use it; charged once |
| odfdom-java 0.13.0 (no R8) | ~17 MB | |

If the ODT decision ever flipped to odfdom, ODS would ride for free (same JAR). But
the ODT recommendation is also hand-roll, so the marginal cost of pulling odfdom
*just* for ODS is the full 8–12 MB — unjustifiable.

## Performance characteristics

- ODS documents are usually small (typical < 1 MB compressed). Pathological cases:
  scientific or financial workbooks with 100k+ rows × 50 cols.
- **Windowed rendering is mandatory.** Pageboy parses *cell metadata* (sheet bounds,
  named ranges, merged-cell map) eagerly, but renders only the visible viewport's
  cells. A `LazyVerticalGrid` (Compose) over an indexed cell store (sparse
  `LongLongMap` keyed on `(row shl 32) or col`) keeps memory bounded.
- **Two-pass parse:**
  1. Fast pass — scan `content.xml` for sheet bounds and named ranges (~tens of ms).
  2. Lazy pass — when the user scrolls into an un-parsed range, parse that row band
     and populate the sparse store. Each `<table:table-row>` is one SAX event window;
     `table:number-rows-repeated` (the ODF shortcut for "this row is empty / repeats N
     times") lets us skip giant empty bands cheaply.
- **Open-to-first-paint latency** on a 10-sheet × 50-row sheet: < 150 ms.
- **100k-row sheet** lazy-windowed open: < 500 ms to first paint, parsing continues
  in the background `IO` dispatcher.

## JVM-vs-native

Pure JVM. No NDK. Shares the same `XmlPullParser` / `ZipInputStream` plumbing as ODT
+ EPUB. One inflater per open document.

## minSdk 28 compatibility

Same as ODT: `XmlPullParser` and `java.util.zip` since API 1. `LongLongMap` from
`androidx.collection` (already a transitive dep of Compose Foundation) for the
sparse cell store. No desugaring needed.

## Maintenance status

- **Hand-roll:** in-repo; ODF 1.2 spec is stable since 2015. The `office:value` /
  `table:table-cell` vocabulary doesn't change.
- **odfdom:** alive under TDF; available as a future escape hatch if pageboy ever
  wants formula evaluation or editing.

## Spec gotchas

The three most likely production crash sites, plus the rest of the prescribed list:

1. **Cell column repetition (`table:number-columns-repeated`).** ODF compresses runs
   of identical empty (or identically-valued) cells into a single element with a
   repeat count. A naive parser that increments column index by 1 per cell will
   silently misalign every subsequent cell on the row. **Mandatory test fixture.**
2. **Formula-cached-value precedence.** Pageboy is a viewer: **always trust
   `office:value` / `office:string-value` / `office:date-value` / `office:boolean-value`
   over `table:formula`.** If both are present, use the cached value. If only
   `table:formula` is present (rare, indicates the document was saved without
   recalculation), display "#FORMULA" placeholder and log once at INFO.
3. **Giant sheets (`<table:table-row table:number-rows-repeated="1048576">`).**
   Calc's default empty sheet writes its full address space as one repeated row. A
   naive parser that materialises each row crashes with OOM. **Skip repeated rows
   that contain no non-empty cells without allocating row objects.**

Other items from the prompt:

- **Named ranges:** parse `<table:named-range>` from
  `<table:named-expressions>` in `content.xml`. Surface as a "Go to…"
  dropdown in the reader chrome (matches the bookmarks pattern from
  whisperboy). **No formula evaluation** — the range is purely a navigation
  target (sheet + cell address).
- **Merged cells:** `table:number-columns-spanned` / `table:number-rows-spanned` on
  a `<table:table-cell>` mean the cell visually spans N×M. Subsequent cells in the
  span are emitted as `<table:covered-table-cell>` placeholders; pageboy must skip
  rendering those. Store the merge as an overlay map keyed on the anchor cell.
- **Frozen panes:** `<table:table-header-rows>` and `<table:table-header-columns>`
  inside the sheet declare the frozen region. Render as a sticky `Row` / `Column`
  in the Compose grid (Material 3 Expressive's `LazyVerticalGrid` doesn't natively
  support frozen panes — we'll need a small custom layout, or stack two scrollables
  with synchronised scroll offsets; document the chosen approach in the renderer).
- **Conditional formatting:** ignored in v1 (the cached `office:value` already
  reflects the value; cached cell style is rendered as written). Note this in the
  About / format-support copy.
- **Charts (`<chart:chart>`):** placeholder rectangle with the chart title beneath.
- **Sheets > 100k rows:** windowed render mandatory (see Performance above).
- **Multi-sheet workbooks:** sheet switcher in the bottom of the reader chrome
  (tab-row style, M3 Expressive `PrimaryScrollableTabRow`).
- **Number formatting:** honour `<number:number-style>` / `<number:date-style>` from
  `styles.xml` for v1's "common" cases (currency, percent, ISO date). Anything else:
  render the raw value. Document this in the renderer.

## Shared with DOCX/XLSX?

**Yes — but only for the spreadsheet side, and only with XLSX.** I propose:

```kotlin
// in format/common/SpreadsheetModel.kt
class SpreadsheetModel(
    val sheets: List<SheetMeta>,
    val cellSource: CellSource,            // lazy, windowed
    val mergedRegions: Map<SheetId, List<MergedRegion>>,
    val frozenPanes: Map<SheetId, FrozenPane?>,
    val namedRanges: List<NamedRange>,
    val meta: DocumentMeta,
)

data class SheetMeta(val id: SheetId, val name: String, val rowCount: Int, val colCount: Int)

interface CellSource {
    /** Returns null for empty/unparsed cells. Implementations are expected to lazy-load. */
    fun cellAt(sheet: SheetId, row: Int, col: Int): Cell?
    /** Pre-warm a row band — implementations may parse-on-demand under the hood. */
    suspend fun prefetchRows(sheet: SheetId, rowRange: IntRange)
}

sealed interface Cell {
    val style: CellStyle
    data class Text(val value: String, override val style: CellStyle) : Cell
    data class Number(val value: Double, val formatted: String, override val style: CellStyle) : Cell
    data class Date(val epochDay: Long, val formatted: String, override val style: CellStyle) : Cell
    data class Bool(val value: Boolean, override val style: CellStyle) : Cell
    data class Placeholder(val reason: String, override val style: CellStyle) : Cell
}

data class CellStyle(
    val bold: Boolean = false, val italic: Boolean = false,
    val align: Align? = null, val bg: Long? = null, val fg: Long? = null,
)

data class MergedRegion(val anchorRow: Int, val anchorCol: Int, val rowSpan: Int, val colSpan: Int)
data class FrozenPane(val rows: Int, val cols: Int)
data class NamedRange(val name: String, val sheet: SheetId, val row: Int, val col: Int)
```

**Why this works for ODS + XLSX:** both formats reduce to "grid of typed cells with
merge regions, frozen panes, named ranges, multiple sheets, cached values". The cell
type union (text / number / date / bool / placeholder) is identical. Style
representation collapses to a small struct in both cases for our viewer subset. The
`CellSource` interface lets each parser implement lazy windowed loading appropriately
(XLSX's shared-strings table + per-sheet XML maps naturally to this; ODS's
`<table:table-row>` SAX events map to it too).

**Where it gets leaky** (the honest part):
- **Formula syntax** — ODF uses `=[.A1]+[.A2]` (square-bracketed cell refs);
  XLSX uses `=A1+A2`. **Pageboy ignores formulas entirely in v1**, so this leak
  doesn't manifest yet. If formula evaluation ever lands, we'd need a per-format
  formula AST and a single evaluator — non-trivial, but a separate plan.
- **Style inheritance** — ODF uses style chains in `styles.xml`; XLSX uses
  `cellXfs` indices into `styles.xml`. Resolution happens in each parser
  *before* hitting the shared `CellStyle`. Fine.
- **Date encoding** — ODF stores `office:date-value="2024-03-15"` (ISO 8601);
  XLSX stores a serial day number with a 1900-vs-1904 epoch knob. Each parser
  normalises to `epochDay`. Fine — the leak is contained at the parse boundary.
- **Sheet limits** — both formats specify ~1M rows × 16k cols, but real-world ODS
  files often write tighter bounds. Use the parser-derived bounds, not a constant.

**Where it does NOT share with ODT/DOCX:** spreadsheets and text documents are
fundamentally different abstractions. The shared layer between ODT and DOCX is
`RichTextDocument` (see [format-odt.md](format-odt.md#shared-with-docxxlsx)); the
shared layer between ODS and XLSX is `SpreadsheetModel`. Two parallel shared models,
both living in `format/common/`. No need to unify further.

**Recommended structure:**

```
format/
├── common/
│   ├── RichTextDocument.kt        // ODT + DOCX
│   ├── SpreadsheetModel.kt        // ODS + XLSX  ← this plan
│   ├── ZipDocumentSource.kt       // all four (ZIP-of-XML formats)
│   └── …
├── ods/
│   ├── OdsRenderer.kt
│   ├── OdsParser.kt
│   └── OdsCellSource.kt
├── xlsx/
│   └── XlsxParser.kt              // emits the same SpreadsheetModel
└── …
```

The Compose grid renderer `SpreadsheetGridView(model: SpreadsheetModel)` is written
once. **Strong recommendation** — the OOXML agent should land the symmetric opinion
from the XLSX side; if both agents converge, Phase J + Phase L share UI code.

## Phase L — implementation

- [ ] **L.1** Add `format/common/SpreadsheetModel.kt` (coordinate with the OOXML
      agent — whichever phase lands first defines it; the other consumes).
- [ ] **L.2** Add `format/ods/OdsRenderer.kt` implementing `DocumentRenderer` with the
      `DocumentFormat.ODS` enum case. Add the case to `FormatDetector`
      (extension `.ods`, magic-byte sniff: `PK\x03\x04` + `mimetype` first entry equals
      `application/vnd.oasis.opendocument.spreadsheet`).
- [ ] **L.3** Implement `OdsParser` two-pass:
      - Pass 1 (eager, on open): parse `styles.xml`, parse `content.xml` for
        sheet bounds, merged-cell map, frozen-pane map, named ranges. Collect
        `List<SheetMeta>`.
      - Pass 2 (lazy, via `OdsCellSource`): on `prefetchRows(...)`, re-scan the
        relevant `<table:table-row>` band into the sparse cell store. Respect
        `table:number-columns-repeated` and `table:number-rows-repeated` —
        repeated empty rows are *not* materialised.
- [ ] **L.4** Implement cached-value precedence: trust `office:value` /
      `office:string-value` / `office:date-value` / `office:boolean-value` over
      `table:formula`. If only a formula is present with no cached value, emit
      `Cell.Placeholder(reason = "#FORMULA")`.
- [ ] **L.5** `SpreadsheetGridView` composable (shared with XLSX) — `LazyVerticalGrid`
      with frozen-pane overlay. Sheet switcher (`PrimaryScrollableTabRow`).
      "Go to named range…" entry point in the reader chrome.
- [ ] **L.6** Robolectric tests: 14 fixtures (`empty.ods`, `one-sheet.ods`,
      `multi-sheet.ods`, `merged-cells.ods`, `frozen-panes.ods`, `named-ranges.ods`,
      `cached-formula.ods`, `formula-without-cache.ods`, `chart-embedded.ods`,
      `conditional-formatting.ods`, `100k-rows-sparse.ods`,
      `100k-rows-dense.ods`, `column-repeats.ods`, `date-styles.ods`). Generated by
      `scripts/build-ods-fixtures.sh` via headless LibreOffice.
- [ ] **L.7** Robolectric perf test: open `100k-rows-sparse.ods`, assert first-paint
      < 500 ms, RSS stays under 80 MB.
- [ ] **L.8** On-device verify via the AVD loop (`adb install -r` + screenshot)
      with a real ODS — screenshot a merged-cell + frozen-pane view + sheet switcher,
      per CLAUDE.md's "UI changes are verified on the running AVD" rule.
- [ ] **L.9** Tick checkboxes + add `shipped in change <jj-id>` to this phase
      header and to main.md Phase L when complete.

## Sources

- ODF Toolkit upstream — [tdf/odftoolkit on GitHub](https://github.com/tdf/odftoolkit)
- ODF Toolkit 0.13.0 release notes (2026-01-23) —
  [odftoolkit.org/ReleaseNotes.html](https://odftoolkit.org/ReleaseNotes.html)
- Maven Central — `org.odftoolkit:odfdom-java:0.13.0`
  ([central.sonatype.com](https://central.sonatype.com/artifact/org.odftoolkit/odfdom-java))
- ODF 1.2 OASIS spec —
  [OpenDocument-v1.2-os-part1.html](https://docs.oasis-open.org/office/v1.2/os/OpenDocument-v1.2-os-part1.html)
- "How to Read and Write ODF/ODS Files" reference —
  [codeguru.com/csharp/how-to-read-and-write-odf-ods-files-opendocument-spreadsheets](https://www.codeguru.com/csharp/how-to-read-and-write-odf-ods-files-opendocument-spreadsheets/)
- Apache JIRA ODFTOOLKIT-459 (Xerces-on-Android dex issue) —
  [issues.apache.org/jira/browse/ODFTOOLKIT-459](https://issues.apache.org/jira/browse/ODFTOOLKIT-459)
- SPDX Apache-2.0 — [spdx.org/licenses/Apache-2.0.html](https://spdx.org/licenses/Apache-2.0.html)
- Sister plan: [format-odt.md](format-odt.md) for the ODT decision rationale this
  plan inherits.
