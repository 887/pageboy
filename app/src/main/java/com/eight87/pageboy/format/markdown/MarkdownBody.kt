package com.eight87.pageboy.format.markdown

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import com.eight87.pageboy.domain.render.RendererContext
import com.eight87.pageboy.domain.render.ScrollPosition
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Phase D / D.3 / D.4 — Markdown body. Orchestrator only; per-block
 * Composables live in [MarkdownBlocks], inline → AnnotatedString in
 * [MarkdownInlines], style tokens in [MarkdownStyle].
 *
 * The body is one `LazyColumn`; each top-level block is one item, keyed
 * by index so a re-parse with appended content doesn't tear the
 * viewport. Off-screen items don't compose — long markdown documents
 * (50-page Obsidian notes) render with bounded per-frame work.
 *
 * Phase E.2 wires [RendererContext] integrations:
 *  - `scrollSink.load()` runs once on first compose to restore the
 *    saved [ScrollPosition] from the chrome's `ScrollPersistence`. The
 *    encoded `(pageIndex << 20) | offset` long is decoded by
 *    `DefaultScrollPersistence`; reflowable formats like Markdown read
 *    `pageIndex` as the LazyColumn item index.
 *  - Scroll observations push back through `scrollSink.record(...)`
 *    debounced inside the chrome.
 *  - The chrome's find query is observed; per-keystroke we run
 *    [MarkdownFind.findAll] over the raw markdown text and publish the
 *    results via `findSink.submitMatches(...)`.
 *  - On `currentMatchIndex` change we map the match's character offset
 *    to the nearest top-level block index and `animateScrollToItem` to
 *    it.
 *
 * Phase C `ReaderSettings.continuousScrolling` is honoured advisory-only
 * in v1: Markdown always renders continuous. Paginated mode is the
 * deferral noted on Phase D.9 — the `continuousScrolling` flag rides on
 * [RendererContext.readingPrefs] but is intentionally not yet consumed.
 */
@Composable
internal fun MarkdownBody(
  handle: MarkdownHandle,
  context: RendererContext,
  modifier: Modifier = Modifier,
) {
  // Re-flatten only when the AST identity changes (one per document
  // open). Inline blocks are cheap to walk; the flattening is bounded
  // by top-level block count, not by inline character count.
  val blocks = remember(handle) { flattenBlocks(handle.ast) }
  val listState = rememberLazyListState()

  // Precompute a sorted array of line-start character offsets so we can
  // map a find-match offset → line number in O(log n) without rescanning
  // the raw text on every keystroke. Then bucket lines by top-level
  // block via an approximate "match offset's line / total lines * block
  // count" heuristic — good enough to jump near the right block; the
  // user's eye does the last 20px of scrolling.
  val lineStarts = remember(handle) { buildLineStartIndex(handle.rawText) }

  // E.2 — scroll restore + scroll record + find-in-doc, all gated by the
  // RendererContext. Each effect is independent so renderer-tests can
  // assert one without setting up the others.
  MarkdownScrollRestore(
    documentId = context.documentId,
    listState = listState,
    scrollSink = context.scrollSink,
  )
  MarkdownScrollRecord(
    documentId = context.documentId,
    listState = listState,
    scrollSink = context.scrollSink,
  )
  MarkdownFindWiring(
    rawText = handle.rawText,
    blocks = blocks,
    lineStarts = lineStarts,
    listState = listState,
    findSink = context.findSink,
  )

  LazyColumn(
    state = listState,
    modifier = modifier
      .fillMaxSize()
      .semantics { testTag = "markdown_body" },
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
  ) {
    itemsIndexed(items = blocks) { _, block ->
      RenderBlock(block, modifier = Modifier.padding(vertical = 4.dp))
    }
  }
}

/**
 * On first compose, read the saved position out of the chrome's
 * `ScrollPersistence` adapter and jump the `LazyColumn` there. Reads
 * are cheap — one Room read per open. Runs once per `documentId`.
 */
@Composable
private fun MarkdownScrollRestore(
  documentId: String,
  listState: LazyListState,
  scrollSink: com.eight87.pageboy.domain.render.RendererScrollSink,
) {
  LaunchedEffect(documentId) {
    // Pattern-match the sealed variant. Markdown only restores
    // [ScrollPosition.LazyColumn] positions — older PDF positions or
    // future EPUB CFI positions on the same document make no sense in
    // a reflowable LazyColumn surface and are ignored.
    val saved = scrollSink.load() as? ScrollPosition.LazyColumn ?: return@LaunchedEffect
    val index = saved.itemIndex.coerceAtLeast(0)
    val offset = saved.offset.coerceAtLeast(0)
    runCatching { listState.scrollToItem(index, scrollOffset = offset) }
  }
}

/**
 * Observe scroll-position changes; record each settled position back
 * through the chrome (debounced inside `DefaultScrollPersistence`).
 */
@Composable
private fun MarkdownScrollRecord(
  documentId: String,
  listState: LazyListState,
  scrollSink: com.eight87.pageboy.domain.render.RendererScrollSink,
) {
  LaunchedEffect(documentId, listState) {
    snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
      .distinctUntilChanged()
      .collect { (index, offset) ->
        scrollSink.record(ScrollPosition.LazyColumn(itemIndex = index, offset = offset))
      }
  }
}

/**
 * Run [MarkdownFind] against the raw text whenever the chrome's
 * find-query updates; publish matches back through the find sink;
 * scroll to the current match on index change.
 */
@Composable
private fun MarkdownFindWiring(
  rawText: String,
  blocks: List<MarkdownBlock>,
  lineStarts: IntArray,
  listState: LazyListState,
  findSink: com.eight87.pageboy.domain.render.RendererFindSink,
) {
  // Re-run search on every query change. The find result is memoised
  // by query text only — distinctUntilChanged keeps the same hits from
  // re-firing the scroll-to-match jump.
  LaunchedEffect(rawText, findSink) {
    // StateFlow already de-dupes — no `distinctUntilChanged()` needed.
    findSink.query.collect { q ->
      findSink.submitMatches(MarkdownFind.findAll(rawText, q))
    }
  }
  // On match-index change, jump to the nearest top-level block.
  LaunchedEffect(rawText, findSink, listState) {
    findSink.query
      .combine(findSink.currentMatchIndex) { q, i -> q to i }
      .distinctUntilChanged()
      .collect { (q, i) ->
        if (q.isEmpty() || i < 0) return@collect
        val matches = MarkdownFind.findAll(rawText, q)
        val match = matches.getOrNull(i) ?: return@collect
        val targetBlock = blockIndexForOffset(
          offset = match.rangeStart,
          blocks = blocks,
          lineStarts = lineStarts,
        )
        runCatching { listState.animateScrollToItem(targetBlock) }
      }
  }
}

/** Build sorted line-start offsets including 0 as the first entry. */
internal fun buildLineStartIndex(text: String): IntArray {
  if (text.isEmpty()) return intArrayOf(0)
  val starts = ArrayList<Int>().apply { add(0) }
  for (i in text.indices) {
    if (text[i] == '\n') starts.add(i + 1)
  }
  return starts.toIntArray()
}

/**
 * Map a raw-text character offset to the nearest top-level block index
 * via a "fraction-of-document" heuristic. Approximate by design — the
 * commonmark AST doesn't preserve per-block source spans for every
 * node, so a precise mapping would need its own walker. The fraction
 * jump lands within a screen of the target; the user's next scroll
 * trims the last bit. Good enough for v1; PDF / EPUB Phase F+ ship a
 * cleaner per-block range when their AST shapes support it.
 */
internal fun blockIndexForOffset(
  offset: Int,
  blocks: List<MarkdownBlock>,
  lineStarts: IntArray,
): Int {
  if (blocks.isEmpty()) return 0
  if (lineStarts.size <= 1) return 0
  // Binary-search lineStarts for the largest index ≤ offset.
  var lo = 0
  var hi = lineStarts.size - 1
  while (lo < hi) {
    val mid = (lo + hi + 1) ushr 1
    if (lineStarts[mid] <= offset) lo = mid else hi = mid - 1
  }
  val matchLine = lo
  val totalLines = (lineStarts.size - 1).coerceAtLeast(1)
  val fraction = matchLine.toFloat() / totalLines.toFloat()
  return (fraction * blocks.size).toInt().coerceIn(0, blocks.size - 1)
}
