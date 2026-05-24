package com.eight87.pageboy.format.docx

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eight87.pageboy.format.docx.internal.PlaceholderKind
import com.eight87.pageboy.format.docx.internal.RichTextBlock

/**
 * Phase I — per-block Composable dispatch for DOCX. One concern per
 * Composable; the table renderer is split out into [RichTextTable] to
 * keep this file under the R.X.4 LOC marker.
 *
 * R.X.2 — sealed dispatch via exhaustive `when`. Adding a block kind
 * (e.g. real image render in v1.1) is one new `is` arm; the compiler
 * flags every site that needs an update.
 */
@Composable
internal fun RenderRichTextBlock(block: RichTextBlock, modifier: Modifier = Modifier) {
  when (block) {
    is RichTextBlock.Paragraph -> ParagraphView(block, modifier)
    is RichTextBlock.Heading -> HeadingView(block, modifier)
    is RichTextBlock.BlockQuote -> BlockQuoteView(block, modifier)
    is RichTextBlock.BulletList -> ListView(block.items, ordered = false, modifier = modifier)
    is RichTextBlock.NumberedList -> ListView(block.items, ordered = true, modifier = modifier)
    is RichTextBlock.Table -> RichTextTable(block, modifier)
    is RichTextBlock.ImagePlaceholder -> ImagePlaceholderView(block, modifier)
    is RichTextBlock.TextBox -> TextBoxView(block, modifier)
    is RichTextBlock.Placeholder -> PlaceholderView(block, modifier)
    is RichTextBlock.SectionBreak -> SectionBreakView(modifier)
  }
}

@Composable
private fun ParagraphView(block: RichTextBlock.Paragraph, modifier: Modifier) {
  if (block.runs.isEmpty()) {
    // Empty paragraph still takes a row so blank-line semantics survive.
    Spacer(modifier = modifier.height(8.dp))
    return
  }
  Text(
    text = RichTextRuns.foldInlinesFromTheme(block.runs),
    style = MaterialTheme.typography.bodyLarge,
    modifier = modifier.fillMaxWidth(),
  )
}

@Composable
private fun HeadingView(block: RichTextBlock.Heading, modifier: Modifier) {
  val typography = MaterialTheme.typography
  val style = when (block.level.coerceIn(1, 6)) {
    1 -> typography.displaySmall
    2 -> typography.headlineLarge
    3 -> typography.headlineMedium
    4 -> typography.headlineSmall
    5 -> typography.titleLarge
    else -> typography.titleMedium
  }
  Text(
    text = RichTextRuns.foldInlinesFromTheme(block.runs),
    style = style.copy(fontWeight = FontWeight.SemiBold),
    color = MaterialTheme.colorScheme.onSurface,
    modifier = modifier
      .fillMaxWidth()
      .padding(top = 8.dp, bottom = 4.dp),
  )
}

@Composable
private fun BlockQuoteView(block: RichTextBlock.BlockQuote, modifier: Modifier) {
  val colors = MaterialTheme.colorScheme
  Row(
    modifier = modifier
      .fillMaxWidth()
      .background(colors.surfaceContainerHigh, RoundedCornerShape(4.dp))
      .padding(horizontal = 12.dp, vertical = 8.dp),
  ) {
    Box(
      modifier = Modifier
        .width(3.dp)
        .height(24.dp)
        .background(colors.primary),
    )
    Spacer(modifier = Modifier.width(12.dp))
    Text(
      text = RichTextRuns.foldInlinesFromTheme(block.runs),
      style = MaterialTheme.typography.bodyLarge,
      color = colors.onSurface,
    )
  }
}

@Composable
private fun ListView(
  items: List<List<com.eight87.pageboy.format.docx.internal.RichTextRun>>,
  ordered: Boolean,
  modifier: Modifier,
) {
  Column(modifier = modifier.fillMaxWidth().padding(start = 8.dp)) {
    for ((index, runs) in items.withIndex()) {
      val marker = if (ordered) "${index + 1}." else "•"
      Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
          text = marker,
          style = MaterialTheme.typography.bodyLarge,
          modifier = Modifier
            .width(24.dp)
            .padding(end = 8.dp),
          textAlign = TextAlign.End,
        )
        Text(
          text = RichTextRuns.foldInlinesFromTheme(runs),
          style = MaterialTheme.typography.bodyLarge,
        )
      }
    }
  }
}

@Composable
private fun ImagePlaceholderView(block: RichTextBlock.ImagePlaceholder, modifier: Modifier) {
  val colors = MaterialTheme.colorScheme
  Box(
    modifier = modifier
      .fillMaxWidth()
      .padding(vertical = 8.dp)
      .background(colors.surfaceContainerLow, RoundedCornerShape(8.dp))
      .padding(16.dp),
  ) {
    Text(
      text = if (block.altText.isNotBlank()) "[image] ${block.altText}" else "[image]",
      style = MaterialTheme.typography.labelMedium.copy(fontSize = 14.sp),
      color = colors.onSurfaceVariant,
    )
  }
}

@Composable
private fun TextBoxView(block: RichTextBlock.TextBox, modifier: Modifier) {
  val colors = MaterialTheme.colorScheme
  Box(
    modifier = modifier
      .fillMaxWidth()
      .background(colors.surfaceContainer, RoundedCornerShape(4.dp))
      .padding(12.dp),
  ) {
    Text(
      text = RichTextRuns.foldInlinesFromTheme(block.runs),
      style = MaterialTheme.typography.bodyMedium,
      color = colors.onSurface,
    )
  }
}

@Composable
private fun PlaceholderView(block: RichTextBlock.Placeholder, modifier: Modifier) {
  val colors = MaterialTheme.colorScheme
  val labelPrefix = when (block.kind) {
    PlaceholderKind.Drawing -> "[shape]"
    PlaceholderKind.Chart -> "[chart]"
    PlaceholderKind.OleObject -> "[embedded object]"
    PlaceholderKind.SmartArt -> "[smart art]"
    PlaceholderKind.Unknown -> "[unknown]"
  }
  Text(
    text = "$labelPrefix ${block.label}".trim(),
    style = MaterialTheme.typography.labelMedium,
    color = colors.onSurfaceVariant,
    modifier = modifier
      .fillMaxWidth()
      .padding(PaddingValues(vertical = 8.dp)),
  )
}

@Composable
private fun SectionBreakView(modifier: Modifier) {
  HorizontalDivider(
    modifier = modifier
      .fillMaxWidth()
      .padding(vertical = 12.dp),
    color = MaterialTheme.colorScheme.outlineVariant,
  )
}
