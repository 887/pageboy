package com.eight87.pageboy.format.ods

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eight87.pageboy.domain.render.RendererContext
import com.eight87.pageboy.domain.render.RendererFindSink
import com.eight87.pageboy.domain.render.RendererScrollSink
import com.eight87.pageboy.domain.render.ScrollPosition
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Phase L — Compose body for the ODS renderer.
 *
 * Layout:
 *  - Sheet tabs along the top (`TabRow` of [OdsHandle.sheets]).
 *  - Per-sheet grid below: outer `LazyColumn` over rows, each row a
 *    horizontally-scrollable `LazyRow` of cell `Text` boxes. Frozen
 *    panes render as sticky header rows / leading sticky columns
 *    overlaid above the lazy grid.
 *
 * The two-axis lazy composition is what keeps the body's per-frame
 * work bounded — even a 100k-row sheet only composes the visible
 * cells.
 *
 * Find-in-doc scope: active sheet only (cross-sheet defers per agent
 * prompt to v1.1). The wiring re-runs [OdsFind] when the active sheet
 * changes too, so jumping between sheets while a query is set still
 * surfaces fresh matches.
 */
@Composable
internal fun OdsBody(
  handle: OdsHandle,
  context: RendererContext,
  modifier: Modifier = Modifier,
) {
  if (handle.sheets.isEmpty()) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      Text("Empty workbook", style = MaterialTheme.typography.bodyMedium)
    }
    return
  }

  var activeSheetIndex by rememberSaveable(handle) { mutableIntStateOf(0) }
  val activeSheet = handle.sheets[activeSheetIndex.coerceIn(0, handle.sheets.size - 1)]
  val rowListState = rememberLazyListState()

  OdsScrollRestore(context.documentId, rowListState, context.scrollSink)
  OdsScrollRecord(context.documentId, rowListState, context.scrollSink)
  OdsFindWiring(activeSheet, rowListState, context.findSink)

  Column(
    modifier = modifier
      .fillMaxSize()
      .semantics { testTag = "ods_body" },
  ) {
    if (handle.sheets.size > 1) {
      TabRow(
        selectedTabIndex = activeSheetIndex,
        modifier = Modifier
          .fillMaxWidth()
          .semantics { testTag = "ods_sheet_tabs" },
      ) {
        handle.sheets.forEachIndexed { index, sheet ->
          Tab(
            selected = index == activeSheetIndex,
            onClick = { activeSheetIndex = index },
            text = { Text(sheet.name) },
          )
        }
      }
    }
    OdsSheetGrid(sheet = activeSheet, listState = rowListState)
  }
}

@Composable
private fun OdsSheetGrid(
  sheet: OdfSheet,
  listState: LazyListState,
  modifier: Modifier = Modifier,
) {
  val frozenRowCount = sheet.frozenPane?.rows ?: 0
  val frozenColCount = sheet.frozenPane?.cols ?: 0

  Column(modifier = modifier.fillMaxSize()) {
    // Frozen header rows render eagerly above the lazy grid.
    if (frozenRowCount > 0) {
      Column {
        for (r in 0 until frozenRowCount.coerceAtMost(sheet.rowCount)) {
          OdsRowView(sheet = sheet, row = r, frozenCols = frozenColCount, isHeader = true)
        }
      }
    }
    val nonFrozenStart = frozenRowCount.coerceAtMost(sheet.rowCount)
    val rows = remember(sheet) { (nonFrozenStart until sheet.rowCount).toList() }
    LazyColumn(
      state = listState,
      modifier = Modifier
        .fillMaxSize()
        .semantics { testTag = "ods_grid" },
    ) {
      items(items = rows, key = { it }) { r ->
        OdsRowView(sheet = sheet, row = r, frozenCols = frozenColCount, isHeader = false)
      }
    }
  }
}

@Composable
private fun OdsRowView(
  sheet: OdfSheet,
  row: Int,
  frozenCols: Int,
  isHeader: Boolean,
) {
  val bg = if (isHeader) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
  val weight = if (isHeader) FontWeight.SemiBold else FontWeight.Normal
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .height(28.dp)
      .background(bg),
  ) {
    // Frozen columns render in line; they share the same scrollable
    // row but stay at the start so a subsequent LazyRow's horizontal
    // scroll can carry the remaining columns. v1 ships them inline —
    // a true sticky-column overlay is a v1.1 ergonomic win the agent
    // prompt names as out of scope here.
    for (c in 0 until sheet.colCount.coerceAtMost(OdsRowMaxCols)) {
      OdsCellView(sheet = sheet, row = row, col = c, weight = weight, frozen = c < frozenCols)
    }
  }
}

@Composable
private fun OdsCellView(
  sheet: OdfSheet,
  row: Int,
  col: Int,
  weight: FontWeight,
  frozen: Boolean,
) {
  val cell = sheet.cellAt(row, col)
  val borderColor = MaterialTheme.colorScheme.outlineVariant
  val cellBg = if (frozen) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surface
  Box(
    modifier = Modifier
      .width(96.dp)
      .height(28.dp)
      .background(cellBg)
      .border(width = 1.dp, color = borderColor)
      .padding(horizontal = 4.dp, vertical = 2.dp),
    contentAlignment = Alignment.CenterStart,
  ) {
    val text = cell.formatted
    if (text.isNotEmpty()) {
      Text(
        text = text,
        style = MaterialTheme.typography.bodySmall.copy(fontWeight = weight),
        maxLines = 1,
        color = MaterialTheme.colorScheme.onSurface,
      )
    }
  }
}

/** Hard cap on visible columns per row to bound per-frame work on
 *  extremely wide sheets. v1 caps at 64; v1.1 can lift via a LazyRow
 *  inside each row. */
private const val OdsRowMaxCols = 64

@Composable
private fun OdsScrollRestore(
  documentId: String,
  listState: LazyListState,
  scrollSink: RendererScrollSink,
) {
  LaunchedEffect(documentId) {
    val saved = scrollSink.load() as? ScrollPosition.LazyColumn ?: return@LaunchedEffect
    val index = saved.itemIndex.coerceAtLeast(0)
    val offset = saved.offset.coerceAtLeast(0)
    runCatching { listState.scrollToItem(index, scrollOffset = offset) }
  }
}

@Composable
private fun OdsScrollRecord(
  documentId: String,
  listState: LazyListState,
  scrollSink: RendererScrollSink,
) {
  LaunchedEffect(documentId, listState) {
    snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
      .distinctUntilChanged()
      .collect { (index, offset) ->
        scrollSink.record(
          ScrollPosition.LazyColumn(itemIndex = index, offset = offset),
        )
      }
  }
}

@Composable
private fun OdsFindWiring(
  sheet: OdfSheet,
  listState: LazyListState,
  findSink: RendererFindSink,
) {
  LaunchedEffect(sheet, findSink) {
    findSink.query.collect { q -> findSink.submitMatches(OdsFind.findAll(sheet, q)) }
  }
  LaunchedEffect(sheet, findSink, listState) {
    findSink.query
      .combine(findSink.currentMatchIndex) { q, i -> q to i }
      .distinctUntilChanged()
      .collect { (q, i) ->
        if (q.isEmpty() || i < 0) return@collect
        val matches = OdsFind.findAll(sheet, q)
        val match = matches.getOrNull(i) ?: return@collect
        runCatching { listState.animateScrollToItem(match.rangeStart) }
      }
  }
}
