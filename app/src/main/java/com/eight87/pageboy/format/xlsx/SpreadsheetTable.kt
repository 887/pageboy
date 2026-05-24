package com.eight87.pageboy.format.xlsx

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.eight87.pageboy.format.xlsx.internal.MergedRegion
import com.eight87.pageboy.format.xlsx.internal.SpreadsheetCell
import com.eight87.pageboy.format.xlsx.internal.SpreadsheetRow
import com.eight87.pageboy.format.xlsx.internal.SpreadsheetSheet

/**
 * Phase J.4 — grid renderer for one sheet. Top-aligned frozen header
 * rows (if any) sit above the scrollable body; both share the same
 * horizontal scroll state so columns line up. Merged cells render with
 * the anchor's value occupying the full span width; spanned-over
 * cells render as blanks.
 *
 * Layout strategy:
 *  - Sheets > 100K rows are bounded by the `LazyColumn` only
 *    composing visible items (the spec's "windowed render" requirement).
 *  - Column width: fixed 96.dp per column for v1. Auto-fit-by-content
 *    is a later polish — fixed-width makes the grid predictable and
 *    keeps the renderer fast on wide sheets.
 *  - Horizontal scroll is shared between header + body via a single
 *    `rememberScrollState()`.
 */
@Composable
internal fun SpreadsheetTable(
  sheet: SpreadsheetSheet,
  listState: LazyListState,
  modifier: Modifier = Modifier,
) {
  val hScroll = rememberScrollState()
  val columnWidth = 96.dp
  val totalCols = remember(sheet) {
    maxOf(sheet.columnCount, sheet.rows.maxOfOrNull { it.cells.size } ?: 0)
  }

  Box(modifier = modifier.fillMaxSize()) {
    androidx.compose.foundation.layout.Column(modifier = Modifier.fillMaxSize()) {
      // Frozen header rows (if any).
      if (sheet.frozenRows > 0) {
        androidx.compose.foundation.layout.Column(
          modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .horizontalScroll(hScroll),
        ) {
          for (rowIndex in 0 until sheet.frozenRows.coerceAtMost(sheet.rows.size)) {
            val row = sheet.rows[rowIndex]
            GridRow(
              row = row,
              rowIndex = rowIndex,
              totalColumns = totalCols,
              columnWidth = columnWidth,
              mergedRegions = sheet.mergedRegions,
              isHeader = true,
            )
          }
        }
      }
      // Scrollable body.
      LazyColumn(
        state = listState,
        modifier = Modifier
          .fillMaxSize()
          .horizontalScroll(hScroll),
      ) {
        val bodyRows = remember(sheet) {
          val drop = sheet.frozenRows.coerceAtMost(sheet.rows.size)
          sheet.rows.drop(drop).mapIndexed { idx, r -> drop + idx to r }
        }
        items(items = bodyRows, key = { it.first }) { (rowIndex, row) ->
          GridRow(
            row = row,
            rowIndex = rowIndex,
            totalColumns = totalCols,
            columnWidth = columnWidth,
            mergedRegions = sheet.mergedRegions,
            isHeader = false,
          )
        }
      }
    }
  }
}

@Composable
private fun GridRow(
  row: SpreadsheetRow,
  rowIndex: Int,
  totalColumns: Int,
  columnWidth: androidx.compose.ui.unit.Dp,
  mergedRegions: List<MergedRegion>,
  isHeader: Boolean,
) {
  val colors = MaterialTheme.colorScheme
  val bg = when {
    isHeader -> colors.surfaceContainerHigh
    rowIndex % 2 == 0 -> colors.surface
    else -> colors.surfaceContainerLowest
  }
  Row(modifier = Modifier.background(bg)) {
    var col = 0
    while (col < totalColumns) {
      val merge = mergedRegions.firstOrNull { it.contains(rowIndex, col) }
      when {
        merge == null -> {
          val cell = row.cells.getOrNull(col) ?: SpreadsheetCell.Empty
          GridCell(
            cell = cell,
            width = columnWidth,
            isHeader = isHeader,
          )
          col += 1
        }
        merge.isAnchor(rowIndex, col) -> {
          val cell = row.cells.getOrNull(col) ?: SpreadsheetCell.Empty
          val span = (merge.lastCol - merge.firstCol + 1).coerceAtLeast(1)
          GridCell(
            cell = cell,
            width = columnWidth * span,
            isHeader = isHeader,
          )
          col = merge.lastCol + 1
        }
        else -> {
          // Spanned-over by a merge anchored elsewhere; skip the
          // remaining width without rendering a cell (the anchor's
          // cell already drew the full span).
          val span = (merge.lastCol - col + 1).coerceAtLeast(1)
          Box(modifier = Modifier.width(columnWidth * span))
          col = merge.lastCol + 1
        }
      }
    }
  }
}

@Composable
private fun GridCell(
  cell: SpreadsheetCell,
  width: androidx.compose.ui.unit.Dp,
  isHeader: Boolean,
) {
  val colors = MaterialTheme.colorScheme
  val style = if (isHeader) {
    MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)
  } else {
    MaterialTheme.typography.bodySmall
  }
  Box(
    modifier = Modifier
      .width(width)
      .border(0.5.dp, colors.outlineVariant)
      .padding(horizontal = 6.dp, vertical = 6.dp),
  ) {
    val align = when (cell) {
      is SpreadsheetCell.Number, is SpreadsheetCell.Bool -> TextAlign.End
      else -> TextAlign.Start
    }
    Text(
      text = cell.display,
      style = style,
      color = colors.onSurface,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      textAlign = align,
      modifier = Modifier.fillMaxWidth(),
    )
  }
}
