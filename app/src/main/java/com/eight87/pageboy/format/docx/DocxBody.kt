package com.eight87.pageboy.format.docx

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import com.eight87.pageboy.domain.render.RendererContext
import com.eight87.pageboy.domain.render.RendererFindSink
import com.eight87.pageboy.domain.render.RendererScrollSink
import com.eight87.pageboy.domain.render.ScrollPosition
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Phase I — DOCX body. Orchestrator only: per-block Composables live
 * in [RichTextBlocks]; inline run folding in [RichTextRuns]; table
 * rendering in [RichTextTable]. Keeps this file under R.X.4.
 *
 * The body is one `LazyColumn`; each top-level [com.eight87.pageboy.format.docx.internal.RichTextBlock]
 * is one item, keyed by index so an updated parse doesn't tear the
 * viewport. Off-screen items don't compose — long Word docs (200+ page
 * thesis) render with bounded per-frame work.
 *
 * Wiring via [RendererContext]:
 *  - `scrollSink.load()` runs once on first compose to restore the
 *    saved [ScrollPosition].
 *  - Scroll observations push back through `scrollSink.record(...)`.
 *  - Find queries trigger [DocxFind.findAll] over the parsed blocks;
 *    matches publish via `findSink.submitMatches`. On match-index
 *    change we `animateScrollToItem` to the target block.
 */
@Composable
internal fun DocxBody(
  handle: DocxHandle,
  context: RendererContext,
  modifier: Modifier = Modifier,
) {
  val listState = rememberLazyListState()
  val blocks = handle.blocks

  DocxScrollRestore(documentId = context.documentId, listState = listState, scrollSink = context.scrollSink)
  DocxScrollRecord(documentId = context.documentId, listState = listState, scrollSink = context.scrollSink)
  DocxFindWiring(blocks = blocks, listState = listState, findSink = context.findSink)

  LazyColumn(
    state = listState,
    modifier = modifier
      .fillMaxSize()
      .semantics { testTag = "docx_body" },
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
  ) {
    itemsIndexed(items = blocks, key = { i, _ -> i }) { _, block ->
      RenderRichTextBlock(
        block = block,
        modifier = Modifier.padding(vertical = 4.dp),
      )
    }
  }
}

@Composable
private fun DocxScrollRestore(
  documentId: String,
  listState: LazyListState,
  scrollSink: RendererScrollSink,
) {
  LaunchedEffect(documentId) {
    val saved = scrollSink.load() ?: return@LaunchedEffect
    val index = saved.pageIndex.coerceAtLeast(0)
    val offset = (saved.offsetFraction * 1000f).toInt().coerceAtLeast(0)
    runCatching { listState.scrollToItem(index, scrollOffset = offset) }
  }
}

@Composable
private fun DocxScrollRecord(
  documentId: String,
  listState: LazyListState,
  scrollSink: RendererScrollSink,
) {
  LaunchedEffect(documentId, listState) {
    snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
      .distinctUntilChanged()
      .collect { (index, offset) ->
        scrollSink.record(
          ScrollPosition(
            pageIndex = index,
            offsetFraction = (offset.toFloat() / 1000f).coerceIn(0f, 1f),
          ),
        )
      }
  }
}

@Composable
private fun DocxFindWiring(
  blocks: List<com.eight87.pageboy.format.docx.internal.RichTextBlock>,
  listState: LazyListState,
  findSink: RendererFindSink,
) {
  LaunchedEffect(blocks, findSink) {
    findSink.query.collect { q -> findSink.submitMatches(DocxFind.findAll(blocks, q)) }
  }
  LaunchedEffect(blocks, findSink, listState) {
    findSink.query
      .combine(findSink.currentMatchIndex) { q, i -> q to i }
      .distinctUntilChanged()
      .collect { (q, i) ->
        if (q.isEmpty() || i < 0) return@collect
        val matches = DocxFind.findAll(blocks, q)
        val match = matches.getOrNull(i) ?: return@collect
        runCatching { listState.animateScrollToItem(match.rangeStart) }
      }
  }
}
