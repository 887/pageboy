package com.eight87.pageboy.format.xlsx.internal

/**
 * Phase J — sheet-level model for the XLSX renderer. One per worksheet
 * in the workbook. Read-only — pageboy ships viewing only in v1.
 *
 * Rows are materialised eagerly for small / medium sheets (the
 * `XSSFWorkbook` path); large workbooks use a streamed accessor (the
 * `excel-streaming-reader` path) that materialises only the visible
 * window. Either way the renderer consumes the same flat shape.
 *
 * Internal to `format/xlsx/` per the Wave A boundary; ODS owns its own
 * sibling model.
 */
internal data class SpreadsheetSheet(
  val name: String,
  val rows: List<SpreadsheetRow>,
  val columnCount: Int,
  val frozenRows: Int = 0,
  val frozenColumns: Int = 0,
  val mergedRegions: List<MergedRegion> = emptyList(),
)

/**
 * One row in a sheet. Cells are indexed by column starting at 0. A
 * sparse row may pad with [SpreadsheetCell.Empty] up to the sheet's
 * `columnCount` so the grid renderer doesn't need to special-case
 * holes.
 */
internal data class SpreadsheetRow(val cells: List<SpreadsheetCell>)

/**
 * A merged cell region. `firstRow`/`lastRow`/`firstCol`/`lastCol` are
 * inclusive — `firstRow == lastRow && firstCol == lastCol` is a single
 * cell (not merged); anything bigger is a merge.
 */
internal data class MergedRegion(
  val firstRow: Int,
  val lastRow: Int,
  val firstCol: Int,
  val lastCol: Int,
) {
  fun contains(row: Int, col: Int): Boolean =
    row in firstRow..lastRow && col in firstCol..lastCol

  /** True iff `(row, col)` is the top-left anchor of the merge. */
  fun isAnchor(row: Int, col: Int): Boolean = row == firstRow && col == firstCol

  /** True iff `(row, col)` is inside the merge but NOT the anchor. */
  fun isSpannedOver(row: Int, col: Int): Boolean = contains(row, col) && !isAnchor(row, col)
}
