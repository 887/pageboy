package com.eight87.pageboy.format.odt

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.eight87.pageboy.domain.render.RendererContext
import com.eight87.pageboy.domain.render.RendererFindSink
import com.eight87.pageboy.domain.render.RendererScrollSink
import com.eight87.pageboy.domain.render.ScrollPosition
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Phase K — Compose body for the ODT renderer. `LazyColumn` over the
 * top-level [OdfTextBlock] list; per-block dispatch through one sealed
 * `when` (R.X.2). Off-screen blocks don't compose — large ODTs render
 * with bounded per-frame work.
 *
 * Scroll-position restore / record + find-in-doc wiring lifts the same
 * pattern from `MarkdownBody` / `TxtBody`; only the per-block
 * rendering differs.
 */
@Composable
internal fun OdtBody(
  handle: OdtHandle,
  context: RendererContext,
  modifier: Modifier = Modifier,
) {
  val listState = rememberLazyListState()
  val blocks = remember(handle) { handle.blocks }

  OdtScrollRestore(context.documentId, listState, context.scrollSink)
  OdtScrollRecord(context.documentId, listState, context.scrollSink)
  OdtFindWiring(blocks, listState, context.findSink)

  LazyColumn(
    state = listState,
    modifier = modifier
      .fillMaxSize()
      .semantics { testTag = "odt_body" },
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
  ) {
    itemsIndexed(blocks) { _, block ->
      RenderOdtBlock(block)
      Spacer(Modifier.padding(vertical = 4.dp))
    }
  }
}

@Composable
private fun RenderOdtBlock(block: OdfTextBlock) {
  when (block) {
    is OdfTextBlock.Paragraph -> ParagraphBlock(block)
    is OdfTextBlock.Heading -> HeadingBlock(block)
    is OdfTextBlock.ListBlock -> ListBlockView(block)
    is OdfTextBlock.Table -> TableBlock(block)
    is OdfTextBlock.EmbeddedPlaceholder -> EmbeddedPlaceholderCard(block)
  }
}

@Composable
private fun ParagraphBlock(block: OdfTextBlock.Paragraph) {
  val style = MaterialTheme.typography.bodyLarge
  Text(
    text = block.runs.toAnnotated(),
    style = style,
    color = MaterialTheme.colorScheme.onSurface,
    textAlign = block.style.align.toTextAlign(),
    modifier = Modifier.fillMaxWidth(),
  )
}

@Composable
private fun HeadingBlock(block: OdfTextBlock.Heading) {
  val style = when (block.level) {
    1 -> MaterialTheme.typography.headlineLarge
    2 -> MaterialTheme.typography.headlineMedium
    3 -> MaterialTheme.typography.headlineSmall
    4 -> MaterialTheme.typography.titleLarge
    5 -> MaterialTheme.typography.titleMedium
    else -> MaterialTheme.typography.titleSmall
  }
  Text(
    text = block.runs.toAnnotated(),
    style = style,
    color = MaterialTheme.colorScheme.onSurface,
    modifier = Modifier.fillMaxWidth(),
  )
}

@Composable
private fun ListBlockView(block: OdfTextBlock.ListBlock) {
  Column(modifier = Modifier.fillMaxWidth()) {
    block.items.forEachIndexed { index, item ->
      Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        val marker = if (block.ordered) "${index + 1}. " else "• "
        Text(
          text = marker,
          style = MaterialTheme.typography.bodyLarge,
          color = MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.width(28.dp),
        )
        Column(modifier = Modifier.fillMaxWidth()) {
          item.forEach { inner -> RenderOdtBlock(inner) }
        }
      }
    }
  }
}

@Composable
private fun TableBlock(block: OdfTextBlock.Table) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .border(width = 1.dp, color = MaterialTheme.colorScheme.outlineVariant),
  ) {
    block.rows.forEach { row ->
      Row(modifier = Modifier.fillMaxWidth()) {
        row.forEach { cell ->
          Column(
            modifier = Modifier
              .weight(1f)
              .border(width = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
              .padding(8.dp),
          ) {
            if (cell.isEmpty()) {
              Text(text = "", style = MaterialTheme.typography.bodyMedium)
            } else {
              cell.forEach { inner -> RenderOdtBlock(inner) }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun EmbeddedPlaceholderCard(block: OdfTextBlock.EmbeddedPlaceholder) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .background(MaterialTheme.colorScheme.surfaceVariant)
      .border(width = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
      .padding(12.dp),
    verticalArrangement = Arrangement.Center,
  ) {
    Text(
      text = "[embedded ${block.kind}]",
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

/* ------ helpers ------ */

private fun List<OdfRun>.toAnnotated(): AnnotatedString = buildAnnotatedString {
  this@toAnnotated.forEach { run ->
    val style = SpanStyle(
      fontWeight = if (run.style.bold) FontWeight.Bold else null,
      fontStyle = if (run.style.italic) FontStyle.Italic else null,
      textDecoration = buildDecoration(run.style.underline, run.style.strike),
    )
    pushStyle(style)
    append(run.text)
    pop()
  }
}

private fun buildDecoration(underline: Boolean, strike: Boolean): TextDecoration? {
  val parts = ArrayList<TextDecoration>(2)
  if (underline) parts += TextDecoration.Underline
  if (strike) parts += TextDecoration.LineThrough
  return when (parts.size) {
    0 -> null
    1 -> parts[0]
    else -> TextDecoration.combine(parts)
  }
}

private fun OdfAlign.toTextAlign(): TextAlign = when (this) {
  OdfAlign.Start -> TextAlign.Start
  OdfAlign.Center -> TextAlign.Center
  OdfAlign.End -> TextAlign.End
  OdfAlign.Justify -> TextAlign.Justify
}

/* ------ wiring ------ */

@Composable
private fun OdtScrollRestore(
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
private fun OdtScrollRecord(
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
private fun OdtFindWiring(
  blocks: List<OdfTextBlock>,
  listState: LazyListState,
  findSink: RendererFindSink,
) {
  LaunchedEffect(blocks, findSink) {
    findSink.query.collect { q -> findSink.submitMatches(OdtFind.findAll(blocks, q)) }
  }
  LaunchedEffect(blocks, findSink, listState) {
    findSink.query
      .combine(findSink.currentMatchIndex) { q, i -> q to i }
      .distinctUntilChanged()
      .collect { (q, i) ->
        if (q.isEmpty() || i < 0) return@collect
        val matches = OdtFind.findAll(blocks, q)
        val match = matches.getOrNull(i) ?: return@collect
        runCatching { listState.animateScrollToItem(match.rangeStart) }
      }
  }
}
