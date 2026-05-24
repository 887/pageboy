package com.eight87.pageboy.format.ods

import com.eight87.pageboy.domain.render.FindMatch

/**
 * Phase L.6 — case-insensitive cell-value search across the active
 * sheet. Per the agent prompt, ODS find-in-doc is scoped to the active
 * sheet; cross-sheet search defers to v1.1.
 *
 * Matches encode the cell address as `rangeStart = row` and `rangeEnd
 * = col` (a pragmatic two-int payload reuse; the chrome's
 * [com.eight87.pageboy.ui.reader.control.ReaderFindPanel] surfaces
 * count + jump only, so the encoding is opaque to the chrome). The
 * body's find-wiring decodes back to (row, col) and scrolls the inner
 * `LazyColumn` / `LazyRow` to bring the cell into view.
 */
internal object OdsFind {

  fun findAll(sheet: OdfSheet, query: String, maxMatches: Int = 500): List<FindMatch> {
    if (query.isEmpty()) return emptyList()
    val needle = query.lowercase()
    val out = ArrayList<FindMatch>()
    // Iterate via sheet bounds because the sparse map's iteration order
    // is not row-major.
    outer@ for (row in 0 until sheet.rowCount) {
      for (col in 0 until sheet.colCount) {
        if (out.size >= maxMatches) break@outer
        val text = sheet.cellAt(row, col).formatted
        if (text.isEmpty()) continue
        if (text.lowercase().contains(needle)) {
          out += FindMatch(
            rangeStart = row,
            rangeEnd = col,
            pageIndex = null,
            contextSnippet = if (text.length > 64) text.substring(0, 64) + "…" else text,
          )
        }
      }
    }
    return out
  }
}
