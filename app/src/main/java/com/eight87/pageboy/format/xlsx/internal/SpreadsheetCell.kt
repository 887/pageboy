package com.eight87.pageboy.format.xlsx.internal

/**
 * Phase J — cell-level model for the XLSX renderer. R.X.2 sealed
 * dispatch: each cell type carries the data the renderer needs without
 * a `when (cellType)` shotgun across the package.
 *
 * Internal to `format/xlsx/` per the Wave A boundary; the ODS
 * renderer ships its own sibling internal model. Cross-format
 * unification is a later refactor.
 *
 * Formula cells in v1 trust the producer-cached value (see
 * `format-xlsx.md` gotcha #2) — no formula evaluation engine.
 */
internal sealed interface SpreadsheetCell {

  /** Display string from POI's DataFormatter or the raw shared-string. */
  val display: String

  data class Text(override val display: String) : SpreadsheetCell

  data class Number(val raw: Double, override val display: String) : SpreadsheetCell

  data class Bool(val value: Boolean) : SpreadsheetCell {
    override val display: String = if (value) "TRUE" else "FALSE"
  }

  data class DateValue(val epochMillis: Long, override val display: String) : SpreadsheetCell

  /**
   * Cached-value formula cell. [formula] is the literal formula text
   * (e.g. `SUM(A1:A10)`) the renderer surfaces in the bottom-sheet
   * detail UI; [display] is the cached value Excel last wrote.
   */
  data class Formula(val formula: String, override val display: String) : SpreadsheetCell

  /** Placeholder for content we recognise but can't render — charts, images. */
  data class Placeholder(override val display: String) : SpreadsheetCell

  data object Empty : SpreadsheetCell {
    override val display: String = ""
  }
}
