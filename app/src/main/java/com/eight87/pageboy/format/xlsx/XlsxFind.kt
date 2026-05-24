package com.eight87.pageboy.format.xlsx

import com.eight87.pageboy.domain.render.FindMatch
import com.eight87.pageboy.format.xlsx.internal.SpreadsheetSheet

/**
 * Phase J.6 — find-in-doc for XLSX. v1 scope: search the active sheet
 * only (cross-sheet search defers to v1.1). Each match carries the
 * row index in [FindMatch.rangeStart] (so the grid can scroll-to-row)
 * and the column index in [FindMatch.rangeEnd].
 *
 * Case-insensitive substring across the cell's `display` text — this
 * matches what users mean by "find" on a spreadsheet (the cell as the
 * grid renders it, not the underlying numeric raw or the formula).
 */
internal object XlsxFind {

  fun findInSheet(sheet: SpreadsheetSheet, query: String, maxMatches: Int = 500): List<FindMatch> {
    if (query.isEmpty()) return emptyList()
    val needle = query.lowercase()
    val matches = ArrayList<FindMatch>()
    for ((rowIndex, row) in sheet.rows.withIndex()) {
      if (matches.size >= maxMatches) break
      for ((colIndex, cell) in row.cells.withIndex()) {
        if (matches.size >= maxMatches) break
        val text = cell.display
        if (text.isEmpty()) continue
        if (text.lowercase().contains(needle)) {
          matches += FindMatch(
            rangeStart = rowIndex,
            rangeEnd = colIndex,
            pageIndex = null,
            contextSnippet = snippet(text),
          )
        }
      }
    }
    return matches
  }

  private fun snippet(text: String): String =
    if (text.length <= 64) text else text.substring(0, 60).trimEnd() + "…"
}
