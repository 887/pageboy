# pageboy — XLSX renderer plan

## Status: ✅ DONE — Phase J shipped (format/xlsx/ with XlsxRenderer, XlsxParser, XlsxBody)

_Sibling plan: [`format-docx.md`](format-docx.md). The two share library landscape (Apache POI dominates OOXML); read the DOCX plan first for the POI-on-Android setup notes (StAX bootstrap, R8 keep-rules pattern, license-allowlist closure) — this file does not repeat them. A parallel ODS agent owns [`format-ods.md`](format-ods.md); the shared-plumbing opinion at the bottom of this file is the OOXML side's proposal._

## Recommendation

**Apache POI 5.5.1 `XSSF` via [`centic9/poi-on-android`](https://github.com/centic9/poi-on-android) `poishadow-all`**, with **[`xlsx-streamer`](https://github.com/monitorjbl/excel-streaming-reader) (`com.monitorjbl:xlsx-streamer`)** layered on top for windowed row reading on large sheets ([Apache-2.0](https://spdx.org/licenses/Apache-2.0.html)). The base POI dependency is **already paid for by DOCX in Phase I** — for the spreadsheet pair we add the `xssf` schemas + xlsx-streamer wrapper only.

- **APK delta on top of DOCX (Phase I)**: **~1–1.5 MB compressed.** XSSF + spreadsheetml schemas + xlsx-streamer total roughly 4–5 MB unshrunk; R8 strips the schemas the renderer never references (pivot caches, conditional-format expressions, chart series shapes we display as placeholders, externalLink fragments). Hard local ceiling on top of DOCX: **2.5 MB**.
- **Pure JVM** — same story as DOCX, no JNI, no per-ABI.

The decision is not "POI again because we already paid for it"; the decision is "POI is the only library that handles shared strings, frozen panes, merged cells, formula-cached values, and the long tail of XLSX serializations (LibreOffice-generated, Google-Sheets-export, Numbers-export, ancient-Excel-export) correctly enough that pageboy does not need to re-debug them." `xlsx-streamer` mitigates POI's only real wart for read-only viewing: in-memory cost on big workbooks.

**Serious alternative that almost won:** [`fastexcel-reader`](https://github.com/dhatim/fastexcel) (see below). It's 10× faster than POI streaming, ~50× smaller in APK delta, and Apache-2.0. It loses on **merged cells, frozen panes, named ranges, and styles** — explicitly out of scope for the library. For a v1 read-only viewer pageboy wants those. Document the option as the future "fast path" if we ever add a streaming-only render mode for >1M-row workbooks.

## Alternatives considered

### `fastexcel-reader` 0.18.4 — **rejected for v1, kept as a future fast path**

[`dhatim/fastexcel`](https://github.com/dhatim/fastexcel) is the leanest serious XLSX reader in the JVM ecosystem.
- **License:** [Apache-2.0](https://spdx.org/licenses/Apache-2.0.html), allowlist-clean.
- **Runtime deps:** `com.fasterxml:aalto-xml:1.4.0` + `org.apache.commons:commons-compress:1.28.0`. **No POI, no XmlBeans.** ([fastexcel-reader pom.xml](https://github.com/dhatim/fastexcel/blob/master/fastexcel-reader/pom.xml))
- **APK delta:** estimated **< 400 KB shrunk** — aalto + commons-compress are already pulled in for DOCX via POI, so the marginal cost is just the dhatim code (~150 KB of Java).
- **Perf:** 10× faster than POI non-streaming, ~5× faster than POI streaming, and uses constant memory regardless of sheet size.
- **Why rejected for v1:** the [project README](https://github.com/dhatim/fastexcel) explicitly states "It only reads cell content. It discards styles, graphs, and many other stuff." That includes **merged cells, frozen panes, named ranges, and the parts of cell styling pageboy wants for "this looks like the Excel doc the user remembers"** (bold headers, currency formatting, date display). A viewer that strips all of those is a tabular-data printer, not a spreadsheet viewer.
- **Where it wins later:** if pageboy ever ships a "show me a million rows" sheet-import surface or a CSV-export pipeline, fastexcel-reader is the right tool. Keep the option live; do not pull it in at Phase J.

### `docx4j` — **not applicable to XLSX**

`docx4j` is Word-only despite the misleading name's `xlsx-extensions` subprojects (which never matured). Not a contender.

### `xlsx-streamer` standalone — **adopted as a layer, not the primary**

[`monitorjbl/excel-streaming-reader`](https://github.com/monitorjbl/excel-streaming-reader) wraps POI's SXSSF event reader in the familiar `Workbook` / `Sheet` / `Row` API. It needs POI 5.x as a dependency — so it isn't a POI alternative, it's a memory mitigation. Use it when the sheet has > 10,000 rows; fall back to plain XSSF for typical small workbooks. License: Apache-2.0.

### `poi-android` (SUPERCILEX fork) — **rejected**

Same reason as in [`format-docx.md`](format-docx.md): stale since Nov 2022, POI 3.17.

### Hand-rolled minimal extractor — **rejected as primary, kept as Plan B**

XLSX is a ZIP with `xl/sharedStrings.xml` + `xl/worksheets/sheet*.xml`. A read-only reader of cell values + merged-cell ranges + frozen-pane metadata + formula-cached values lands in **2,000–3,000 LOC of Kotlin** with a SAX cursor. The pain is the sharedStrings table (must be loaded eagerly or lazily-indexed), the OOXML cell `r` attribute (A1 → row/col arithmetic, easy to get wrong on columns past Z / past AA / past ZZ), and the styles part (`xl/styles.xml`) if we want number formatting and date detection. Plan B if POI lands too heavy after DOCX's Phase I R8 settle.

### Headless LibreOffice — **rejected**

Same reason as DOCX: 60+ MB per ABI native code. Off-table.

## License compatibility

| Library | SPDX | Allowlist hit |
|---|---|---|
| Apache POI `poi-ooxml` 5.5.1 (xssf path) | [Apache-2.0](https://spdx.org/licenses/Apache-2.0.html) | yes (already paid for in Phase I) |
| `com.monitorjbl:xlsx-streamer` 5.x | [Apache-2.0](https://spdx.org/licenses/Apache-2.0.html) | yes |
| `com.fasterxml:aalto-xml` 1.4.0 | Apache-2.0 | yes (already paid for in Phase I) |
| `commons-compress` | Apache-2.0 | yes |

No license additions beyond Phase I. xlsx-streamer is a clean Apache-2.0 wrapper.

## APK-size budget

- Phase I lands POI + XmlBeans + aalto (~3.5–4.5 MB).
- Phase J adds: XSSF + SpreadsheetML schemas (the bulk of the delta), the `xlsx-streamer` wrapper (~30 KB), keep-rules co-located with DOCX.
- **Marginal APK delta for XLSX on top of DOCX: target ~1–1.5 MB compressed, hard ceiling 2.5 MB.**
- **OOXML pair total budget: ~5 MB compressed** for the combined DOCX + XLSX surface. This is the figure to feature in the overall APK-size reckoning.

R8 keep-rules (additions to the rules from Phase I):
- Keep `org.apache.poi.xssf.**` and `org.apache.poi.xssf.streaming.**`.
- Keep `org.openxmlformats.schemas.spreadsheetml.**` schemas POI actually loads.
- Keep `com.monitorjbl.xlsx.**`.

## JVM vs native

Pure JVM. Same as DOCX.

## Performance characteristics

Two paths:

**Small/medium workbooks (< 10,000 rows, < 50 sheets):** Plain `XSSFWorkbook`. Open + render-first-sheet in < 600 ms on AVD `medium_phone`. In-memory heap ~5–20 MB depending on shared-string density.

**Large workbooks (≥ 10,000 rows or pathological wide sheets):** Switch to `xlsx-streamer` (`StreamingReader.builder().rowCacheSize(100).bufferSize(4096).open(...)`). Memory bounded at row-cache size × row width. **Sheets > 100,000 rows render via `LazyColumn` with viewport-driven row loading** — only the visible rows + an over-scan margin are converted from `Row` to the Compose-side `SpreadsheetModel.Row`.

Heuristic for the path switch: ZIP-walk the XLSX, count `sheet*.xml` part sizes, sum > 5 MB → streaming. Cheap to compute, robust enough.

`xlsx-streamer` does **not** support `getMergedRegions()` (because the merged-region metadata is at the bottom of the sheet XML and the stream has moved past it). For large workbooks, **pageboy reads merged regions in a separate non-streaming pre-pass** (cheap — they're listed compactly at the sheet end) and feeds them into the streaming row renderer as a sparse-lookup table.

## Android-version compatibility (minSdk 28)

Inherits everything from [`format-docx.md`](format-docx.md) — StAX bootstrap is process-global and already in place. XSSF specifically does not introduce new `java.awt` references beyond the chart/drawing path, which pageboy renders as a placeholder anyway (see "Spec gotchas").

## Maintenance status (May 2026)

- **Apache POI (XSSF path):** very active. See [`format-docx.md`](format-docx.md).
- **`xlsx-streamer`:** active. Tracks POI major versions. ([excel-streaming-reader releases](https://github.com/monitorjbl/excel-streaming-reader/releases))
- **`fastexcel`:** active. Latest reader 0.18.4 (Feb 2026), writer 0.20.1 (May 2026). ([dhatim/fastexcel releases](https://github.com/dhatim/fastexcel/releases))

## Spec gotchas (the three most likely production crashes)

1. **Shared strings table (`xl/sharedStrings.xml`) on big workbooks.** Excel interns repeated string cells into one table referenced by cell `t="s"` + integer index. Eager-loading is O(N) memory; lazy-loading via random-access into the XML is O(1) memory but the standard StAX cursor cannot rewind. POI's plain XSSF eager-loads (fine for small workbooks). `xlsx-streamer` provides a temp-file-backed lazy implementation (`sstCacheSize`). **Decision:** plain XSSF up to 5 MB sheet size; xlsx-streamer with `sstCacheSize=10000` above.
2. **Formula cells with cached values vs. without.** XLSX `<c r="A1" t="str"><f>SUM(B1:B10)</f><v>42</v></c>` — `<v>` is the **cached** value the producer wrote. Pageboy **trusts the cached `<v>`** in v1 and never evaluates formulas. Implication: a workbook that was opened-but-not-saved in another tool may have stale cached values, and a workbook where the producer omitted `<v>` will show empty cells for those formulas. **Documented behaviour, not a bug** — surface in onboarding/help text if the question comes up. No formula engine in v1.
3. **Pivot tables, conditional formatting, charts.** Pivot tables store their rendered result in the same sheet as plain cells with cached values — they Just Render. Conditional formatting (per-cell color rules) — pageboy ignores in v1 (renders as unconditional cells). Charts — embedded as drawings inside `xl/drawings/`; pageboy renders the chart cell range as `[chart]` placeholder + dimensions. The cached PNG preview that newer Excel writes inside the chart drawing's `xdr:graphicFrame` MAY be rasterized — investigate at J.6, but v1 ships as placeholder if the rasterization adds > 200 KB code or > 100 ms first-paint latency.

Secondary / documented decisions:
- **Frozen panes (`<sheetView><pane>`):** render correctly when scrolling — the frozen header rows / columns stay pinned during `LazyColumn` scroll. Implement via a two-`LazyColumn` split (header pane + scrolling pane) bound to the same row data with the same horizontal `LazyRow` scroll state.
- **Merged cells:** read merged regions, render the merged area as one big cell with the value from the top-left member (Excel convention). Visual: a `Modifier` that spans the cell grid.
- **Named ranges:** load for **navigation only** in v1 — populate a "Jump to…" overflow menu. No formula resolution.
- **Sheets with > 100,000 rows:** windowed render via `LazyColumn`, only visible rows materialised into `SpreadsheetModel.Row`. xlsx-streamer's `rowCacheSize` is the underlying knob.
- **Cell date detection:** XLSX has no boolean "this is a date" — date cells are numeric cells with a date-shaped number-format string. POI exposes `DateUtil.isCellDateFormatted(cell)`; trust it. Display in the user's locale via `DateFormat.getDateInstance()`. No timezone conversion (XLSX dates are intentionally timezone-naive).
- **Number formats:** apply POI's `DataFormatter` for cell-display strings (currency, percentage, scientific, accounting). `DataFormatter` is small, pure-JVM, and already in the XSSF dependency closure.
- **Hyperlinks (`<hyperlinks>` part):** render as tappable `AnnotatedString` on cell text. External URLs only in v1; sheet-internal references jump to that cell.
- **`xl/calcChain.xml`:** ignore. Calculation order is only relevant if we were evaluating formulas.
- **Encrypted workbooks:** POI throws `EncryptedDocumentException` — `DocumentRenderer` returns `RenderResult.Unsupported("encrypted")`. Password-prompt UX is a future phase.

## Shared with ODT/ODS?

The ODT/ODS agent owns the symmetric opinion. The OOXML-side proposal:

**Yes, share an intermediate spreadsheet model for the XLSX + ODS pair.** Two parsers (XSSF for XLSX, the ODS parser for ODS), one Compose-side renderer. Sketch:

```kotlin
package com.eight87.pageboy.format.sheet

// A full workbook as the renderer wants to see it.
// Parsers produce this lazily (sheets are lazy, rows in big sheets are lazy).
interface SpreadsheetModel {
  val title: String?
  val sheets: List<SheetRef>      // names + dimensions only, sheets load on demand
  fun openSheet(ref: SheetRef): Sheet

  interface Sheet : AutoCloseable {
    val name: String
    val rowCount: Int             // may be approximate for streamed sheets
    val columnCount: Int
    val frozenRows: Int           // 0 if no frozen header
    val frozenColumns: Int
    val mergedRegions: List<MergedRegion>
    val namedRanges: List<NamedRange>     // for "Jump to…"
    fun row(index: Int): Row?     // null if past end (streamed sheets resolve lazily)
  }
}

data class SheetRef(val name: String, val approxRows: Int)
data class Row(val cells: List<Cell>)
sealed interface Cell {
  data class Text(val value: String, val href: String? = null) : Cell
  data class Number(val raw: Double, val formatted: String) : Cell
  data class Date(val instant: java.time.LocalDateTime, val formatted: String) : Cell
  data class Bool(val value: Boolean) : Cell
  data object Empty : Cell
  data class Placeholder(val label: String /* "[chart]", "[image]" */) : Cell
}
data class MergedRegion(val firstRow: Int, val lastRow: Int, val firstCol: Int, val lastCol: Int)
data class NamedRange(val name: String, val sheet: String, val row: Int, val col: Int)
```

Compose renderer in `format/sheet/render/SpreadsheetRenderer.kt` consumes `SpreadsheetModel` agnostically. Both `format/xlsx/` and `format/ods/` provide one `SpreadsheetModel` implementation each. `DocumentRenderer` for each format is a thin shell.

The renderer is the load-bearing shared code — frozen panes, merged cell layout, `LazyColumn` viewport mechanics, the "jump to named range" navigation, the cell-tap-shows-formula popover — all non-trivial, all the same between XLSX and ODS.

Note the deliberate asymmetry with the text-document pair: a spreadsheet `Sheet` is **stateful and `AutoCloseable`** because the streaming-read path holds an open `InputStream` into the sheet part. The text-document `RichTextDocument` is a value type. That asymmetry is real and reflects the format difference, not an API inconsistency to fix.

## Phase J — implementation

- [ ] **J.1** Add `com.monitorjbl:xlsx-streamer:<latest 5.x>` to `app/build.gradle.kts`. POI itself is already on the classpath from Phase I — no new POI dep, just extend the keep-rules. Verify APK delta with the APK analyzer: **target ≤ 1.5 MB on top of DOCX, hard ceiling 2.5 MB.** If exceeded, switch to the hand-rolled extractor (Plan B).
- [ ] **J.2** Define `SpreadsheetModel`, `Sheet`, `Row`, `Cell`, `MergedRegion`, `NamedRange` in `format/sheet/model/` per the sketch above. **Owned jointly with the ODS agent** — coordinate `Cell` sealed-class shape before either parser lands. Unit test: build a `SpreadsheetModel` by hand, serialize/snapshot.
- [ ] **J.3** Write `XlsxSpreadsheetModel.kt` in `format/xlsx/`. ZIP-walk the SAF `InputStream` to size the sheet parts; **pick plain XSSF (`XSSFWorkbook(InputStream)`) for sheets summing < 5 MB, xlsx-streamer (`StreamingReader.builder().rowCacheSize(100).sstCacheSize(10000).open(InputStream)`) above.** Pre-pass for merged regions on streaming sheets (read sheet XML once with a SAX cursor looking only for `<mergeCells>` near the end). Date cells via `DateUtil.isCellDateFormatted`. Number formats via POI `DataFormatter`. Hyperlinks from the `<hyperlinks>` part. Charts → `Cell.Placeholder("[chart]")`, images → `Cell.Placeholder("[image]")`.
- [ ] **J.4** Compose renderer `format/sheet/render/SpreadsheetRenderer.kt`. Sheet tabs along the bottom (Material 3 Expressive `PrimaryScrollableTabRow`). Active sheet shown as a frozen-header `LazyColumn` + horizontal `LazyRow` per row. Frozen rows / columns rendered as a separately scrolled pane, locked to the main viewport's horizontal/vertical scroll state via a shared `ScrollableState`. Merged cells via `Modifier`-based span. Cell tap shows a bottom-sheet with the raw value + (if formula present) the formula text — POI exposes `cell.getCellFormula()`.
- [ ] **J.5** Implement `XlsxDocumentRenderer : DocumentRenderer`. Thin adapter: SAF `Uri` → `XlsxSpreadsheetModel` → `SpreadsheetRenderer`. Returns `RenderResult.Unsupported("encrypted")` on `EncryptedDocumentException`. Closes the underlying `Sheet` (and the streaming reader) when the renderer disposes.
- [ ] **J.6** Investigate chart-preview rasterization. Look for `xdr:graphicFrame` PNG previews in `xl/media/`; if cheap (< 200 KB code, < 100 ms first-paint latency), upgrade chart placeholder to a thumbnail; else keep `[chart]`.
- [ ] **J.7** R8 / ProGuard keep-rules — additions to Phase I's set in `app/proguard-rules.pro`: keep `org.apache.poi.xssf.**`, `org.apache.poi.xssf.streaming.**`, `org.openxmlformats.schemas.spreadsheetml.**` (the subset xlsx-streamer + XSSF actually load), `com.monitorjbl.xlsx.**`. Confirm with the APK analyzer that PowerPoint schema fragments are still stripped.
- [ ] **J.8** Robolectric fixtures in `app/src/test/resources/format/xlsx/`: (a) `minimal.xlsx` (one sheet, 5 rows), (b) `merged-and-frozen.xlsx` (frozen header row, frozen first column, several merged regions), (c) `formulas-cached.xlsx` (sum + average + date arithmetic with cached values; assert pageboy displays the cached results), (d) `formulas-no-cache.xlsx` (formulas with `<v>` deliberately removed; assert empty-cell display, no crash, no formula evaluation), (e) `wide-and-tall.xlsx` (programmatically generated 50,000 × 50; assert streaming path engages and first-paint < 1500 ms), (f) `charts-and-images.xlsx` (assert placeholder cells; no crash on the embedded drawing), (g) `pivot-table.xlsx` (assert cached pivot result rendered as plain cells), (h) `named-ranges.xlsx` (assert named ranges populate the Jump-to menu).
- [ ] **J.9** Catalog XLSX in the Licenses screen per [`oss-licenses.md`](oss-licenses.md). xlsx-streamer's Apache-2.0 license adds one new entry.
- [ ] **J.10** UI verification on AVD `emulator-5554` per the [`CLAUDE.md`](../../CLAUDE.md) rule. Open `merged-and-frozen.xlsx` via SAF; screencap confirms the frozen header pane stays put on vertical scroll, merged cells render as one cell, sheet tabs are present and tappable. Repeat with `wide-and-tall.xlsx` to confirm scrolling stays smooth (no jank) over the windowed renderer.
- [ ] **J.11** Tick the Phase J header in [`main.md`](main.md) with the jj change ID (or git SHA) once J.1–J.10 are all ticked. Mark this file `## Status: ✅ DONE`.
