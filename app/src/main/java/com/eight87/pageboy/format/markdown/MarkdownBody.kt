package com.eight87.pageboy.format.markdown

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
 * Phase C `ReaderSettings.continuousScrolling` is honoured advisory-only
 * in v1: Markdown always renders continuous. Paginated mode is the
 * deferral noted on Phase D.9.
 *
 * Scroll position persistence (Phase D.7) and find-in-doc (Phase D.8)
 * are deliberately *not* wired here — they need to reach the reader
 * chrome (which owns the find query, the `ScrollPersistence` instance,
 * and the document id). Wiring them here would force `MarkdownBody` to
 * grow constructor parameters that aren't on `DocumentRenderer.Body()`'s
 * signature. The chrome's existing per-axis controllers handle both
 * surfaces for the body shape currently shipped; the renderer's job is
 * the rendering itself.
 *
 * The `LazyListState` is hoistable via [rememberLazyListState] above the
 * body if Phase E onwards wires a richer scroll-restore that needs to
 * cross the renderer/chrome boundary.
 */
@Composable
internal fun MarkdownBody(
  handle: MarkdownHandle,
  modifier: Modifier = Modifier,
) {
  // Re-flatten only when the AST identity changes (one per document
  // open). Inline blocks are cheap to walk; the flattening is bounded
  // by top-level block count, not by inline character count.
  val blocks = remember(handle) { flattenBlocks(handle.ast) }
  val listState = rememberLazyListState()

  // No-op observer for now — kept here so when the chrome wires the
  // body up with a hoisted listState (Phase E refactor) the
  // distinctUntilChanged contract is already in place. Removing the
  // observer is a no-op compile-time change; leaving it gives us the
  // hook the next phase needs.
  LaunchedEffect(listState) {
    snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
      .distinctUntilChanged()
      .collect { /* observed in Phase E; no-op in D */ }
  }

  LazyColumn(
    state = listState,
    modifier = modifier
      .fillMaxSize()
      .semantics { testTag = "markdown_body" },
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
  ) {
    itemsIndexed(items = blocks) { index, block ->
      RenderBlock(block, modifier = Modifier.padding(vertical = 4.dp))
      @Suppress("UNUSED_PARAMETER") val k = index // keyed by position
    }
  }
}
