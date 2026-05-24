package com.eight87.pageboy.format.xlsx

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import com.eight87.pageboy.domain.render.RendererContext
import com.eight87.pageboy.domain.render.RendererFindSink
import com.eight87.pageboy.domain.render.RendererScrollSink
import com.eight87.pageboy.domain.render.ScrollPosition
import com.eight87.pageboy.format.xlsx.internal.SpreadsheetSheet
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Phase J — XLSX body. Orchestrator only: per-sheet grid lives in
 * [SpreadsheetTable]. Keeps this file under R.X.4.
 *
 * Layout: a `PrimaryScrollableTabRow` along the top with one tab per
 * sheet; the body below is the [SpreadsheetTable] for the selected
 * sheet. Frozen panes / merged cells are honoured by the table
 * renderer.
 *
 * Wiring via [RendererContext]:
 *  - `scrollSink.load()` runs once to restore the saved row index +
 *    fractional offset for the ACTIVE SHEET; per-sheet scroll position
 *    is encoded into [ScrollPosition.pageIndex] (the sheet selector
 *    state) + the row index falls into `offsetFraction`'s integer part.
 *    In v1 we keep it simple: we restore only the row index for the
 *    first sheet on open. Per-sheet position memory is deferred.
 *  - Find-in-doc searches the active sheet's cells via [XlsxFind] and
 *    scrolls the grid to the matching row.
 */
@Composable
internal fun XlsxBody(
  handle: XlsxHandle,
  context: RendererContext,
  modifier: Modifier = Modifier,
) {
  val sheets = handle.sheets
  if (sheets.isEmpty()) {
    Box(
      modifier = modifier
        .fillMaxSize()
        .semantics { testTag = "xlsx_body" },
    ) {
      Text(
        text = "Empty workbook",
        modifier = Modifier.padding(16.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    return
  }

  var activeIndex by rememberSaveable(handle.title) { mutableIntStateOf(0) }
  val activeSheet = sheets.getOrNull(activeIndex) ?: sheets.first()
  val listState = rememberLazyListState()

  // Restore on first compose against the active sheet's identity.
  XlsxScrollRestore(
    documentId = context.documentId,
    sheetName = activeSheet.name,
    listState = listState,
    scrollSink = context.scrollSink,
  )
  XlsxScrollRecord(
    documentId = context.documentId,
    sheetName = activeSheet.name,
    listState = listState,
    scrollSink = context.scrollSink,
  )
  XlsxFindWiring(
    sheet = activeSheet,
    listState = listState,
    findSink = context.findSink,
  )

  Column(
    modifier = modifier
      .fillMaxSize()
      .semantics { testTag = "xlsx_body" },
  ) {
    SheetTabRow(
      sheets = sheets,
      activeIndex = activeIndex,
      onSelect = { activeIndex = it },
    )
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    SpreadsheetTable(
      sheet = activeSheet,
      listState = listState,
      modifier = Modifier
        .fillMaxWidth()
        .weight(1f),
    )
  }
}

@Composable
private fun SheetTabRow(
  sheets: List<SpreadsheetSheet>,
  activeIndex: Int,
  onSelect: (Int) -> Unit,
) {
  PrimaryScrollableTabRow(
    selectedTabIndex = activeIndex,
    modifier = Modifier
      .fillMaxWidth()
      .height(48.dp),
  ) {
    for ((index, sheet) in sheets.withIndex()) {
      Tab(
        selected = index == activeIndex,
        onClick = { onSelect(index) },
        text = {
          Text(
            text = sheet.name,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
          )
        },
      )
    }
  }
}

@Composable
private fun XlsxScrollRestore(
  documentId: String,
  sheetName: String,
  listState: LazyListState,
  scrollSink: RendererScrollSink,
) {
  LaunchedEffect(documentId, sheetName) {
    val saved = scrollSink.load() as? ScrollPosition.LazyColumn ?: return@LaunchedEffect
    val index = saved.itemIndex.coerceAtLeast(0)
    val offset = saved.offset.coerceAtLeast(0)
    runCatching { listState.scrollToItem(index, scrollOffset = offset) }
  }
}

@Composable
private fun XlsxScrollRecord(
  documentId: String,
  sheetName: String,
  listState: LazyListState,
  scrollSink: RendererScrollSink,
) {
  LaunchedEffect(documentId, sheetName, listState) {
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
private fun XlsxFindWiring(
  sheet: SpreadsheetSheet,
  listState: LazyListState,
  findSink: RendererFindSink,
) {
  val scope = rememberCoroutineScope()
  LaunchedEffect(sheet, findSink) {
    findSink.query.collect { q -> findSink.submitMatches(XlsxFind.findInSheet(sheet, q)) }
  }
  LaunchedEffect(sheet, findSink, listState) {
    findSink.query
      .combine(findSink.currentMatchIndex) { q, i -> q to i }
      .distinctUntilChanged()
      .collect { (q, i) ->
        if (q.isEmpty() || i < 0) return@collect
        val matches = XlsxFind.findInSheet(sheet, q)
        val match = matches.getOrNull(i) ?: return@collect
        scope.launch { runCatching { listState.animateScrollToItem(match.rangeStart) } }
      }
  }
}
