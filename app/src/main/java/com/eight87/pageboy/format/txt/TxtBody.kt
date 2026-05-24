package com.eight87.pageboy.format.txt

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.eight87.pageboy.domain.render.RendererContext
import com.eight87.pageboy.domain.render.RendererFindSink
import com.eight87.pageboy.domain.render.RendererScrollSink
import com.eight87.pageboy.domain.render.ScrollPosition
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Phase E.4 — Compose body for the TXT renderer. `LazyColumn` over the
 * line range; each item is one monospace `Text(lineSource.lineAt(i))`.
 * Off-screen items don't compose, so 100K-line log files render with
 * bounded per-frame work.
 *
 * Phase E.2 / E.3 wiring through [RendererContext]:
 *  - Scroll-position restore + record via `context.scrollSink`.
 *  - Find-in-doc: re-run `TxtFind.findAll` on every query change,
 *    publish matches via `context.findSink.submitMatches`, animate to
 *    the line of the current match on index change.
 *
 * Typography: `MaterialTheme.typography.bodyMedium.copy(fontFamily =
 * FontFamily.Monospace)` per `format-txt.md` E.6.
 */
@Composable
internal fun TxtBody(
  handle: TxtHandle,
  context: RendererContext,
  modifier: Modifier = Modifier,
) {
  val source = handle.lineSource
  val listState = rememberLazyListState()

  // Stable index list keyed by line position. Snapshotting it once per
  // open avoids rebuilding on every recomposition.
  val indices = remember(handle) { (0 until source.lineCount).toList() }

  TxtScrollRestore(context.documentId, listState, context.scrollSink)
  TxtScrollRecord(context.documentId, listState, context.scrollSink)
  TxtFindWiring(source = source, listState = listState, findSink = context.findSink)

  val style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)

  LazyColumn(
    state = listState,
    modifier = modifier
      .fillMaxSize()
      .semantics { testTag = "txt_body" },
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
  ) {
    items(indices, key = { it }) { i ->
      val line = source.lineAt(i)
      // Empty lines still take a row so blank-line semantics survive.
      Text(
        text = if (line.isEmpty()) " " else line,
        style = style,
        color = MaterialTheme.colorScheme.onSurface,
      )
    }
  }
}

@Composable
private fun TxtScrollRestore(
  documentId: String,
  listState: LazyListState,
  scrollSink: RendererScrollSink,
) {
  LaunchedEffect(documentId) {
    // Same pattern as Markdown — only restore `LazyColumn` variants.
    val saved = scrollSink.load() as? ScrollPosition.LazyColumn ?: return@LaunchedEffect
    val index = saved.itemIndex.coerceAtLeast(0)
    val offset = saved.offset.coerceAtLeast(0)
    runCatching { listState.scrollToItem(index, scrollOffset = offset) }
  }
}

@Composable
private fun TxtScrollRecord(
  documentId: String,
  listState: LazyListState,
  scrollSink: RendererScrollSink,
) {
  LaunchedEffect(documentId, listState) {
    snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
      .distinctUntilChanged()
      .collect { (index, offset) ->
        scrollSink.record(ScrollPosition.LazyColumn(itemIndex = index, offset = offset))
      }
  }
}

@Composable
private fun TxtFindWiring(
  source: TxtLineSource,
  listState: LazyListState,
  findSink: RendererFindSink,
) {
  LaunchedEffect(source, findSink) {
    // StateFlow already de-dupes — no `distinctUntilChanged()` needed.
    findSink.query.collect { q -> findSink.submitMatches(TxtFind.findAll(source, q)) }
  }
  LaunchedEffect(source, findSink, listState) {
    findSink.query
      .combine(findSink.currentMatchIndex) { q, i -> q to i }
      .distinctUntilChanged()
      .collect { (q, i) ->
        if (q.isEmpty() || i < 0) return@collect
        val matches = TxtFind.findAll(source, q)
        val match = matches.getOrNull(i) ?: return@collect
        runCatching { listState.animateScrollToItem(match.rangeStart) }
      }
  }
}
