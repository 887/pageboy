package com.eight87.pageboy.format.ods

/**
 * Phase L — one sheet from an ODS workbook. The cell store is sparse
 * — only non-empty cells are kept; the body fills the gaps with
 * [OdfCell.Empty]. Bounds ([rowCount] / [colCount]) reflect the
 * highest non-empty cell address, capped by the parser's hard
 * [MAX_RENDER_ROWS] / [MAX_RENDER_COLS] for the v1 viewer.
 *
 * Lazy windowing is the long-game (format-ods.md gotcha #3 — Calc's
 * default empty sheet writes a 1M-row repeat that a naive parser
 * materialises and OOMs on). The v1 parser uses [OdsParser]'s
 * skip-empty-repeated-rows pass so the in-memory representation is
 * bounded by *non-empty content*, not declared sheet dimensions; for
 * 100k-row sparse sheets this is fine on-device, and the
 * `LazyColumn × LazyRow` body's lazy composition does the per-frame
 * windowing.
 */
data class OdfSheet internal constructor(
  val name: String,
  val rowCount: Int,
  val colCount: Int,
  internal val cells: Map<Long, OdfCell>,
  val mergedRegions: List<MergedRegion>,
  val frozenPane: FrozenPane?,
) {

  /**
   * Returns the cell at the given (0-based) coordinates. Returns
   * [OdfCell.Empty] for any address inside bounds but outside the
   * sparse store; returns [OdfCell.Empty] for out-of-bounds reads too
   * so the body never NPE's on a stale index.
   */
  fun cellAt(row: Int, col: Int): OdfCell = cells[packKey(row, col)] ?: OdfCell.Empty

  companion object {
    /** Pack (row, col) into one Long key for the sparse map. */
    internal fun packKey(row: Int, col: Int): Long = (row.toLong() shl 32) or (col.toLong() and 0xFFFF_FFFFL)

    /** Cap row count actually rendered to avoid an OOM on a 1M-row declared sheet. */
    const val MAX_RENDER_ROWS: Int = 100_000

    /** Cap column count — ODS spec is 1024 cols, real sheets rarely exceed this. */
    const val MAX_RENDER_COLS: Int = 1024
  }
}

/**
 * Anchor + span for a merged-cell region. Cells inside the span (other
 * than the anchor) are emitted by the parser as [OdfCell.Empty] so the
 * body draws an empty cell beneath the visual merge overlay.
 */
data class MergedRegion(
  val anchorRow: Int,
  val anchorCol: Int,
  val rowSpan: Int,
  val colSpan: Int,
)

/** Sheet-frozen rows / columns. */
data class FrozenPane(
  val rows: Int,
  val cols: Int,
)

/**
 * Named-range navigation target. Surfaced in the reader chrome's
 * overflow as a "Go to…" list; no formula evaluation — the range is
 * purely an address.
 */
data class NamedRange(
  val name: String,
  val sheetName: String,
  val row: Int,
  val col: Int,
)
