package com.eight87.pageboy.format.docx

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eight87.pageboy.format.docx.internal.RichTextBlock

/**
 * Phase I — DOCX table renderer split off [RichTextBlocks] for R.X.4
 * compliance. One concern: walk `RichTextBlock.Table` rows + cells,
 * render each cell as a bordered `Box` with its inline-folded text.
 *
 * Wrapped in a horizontal scroller because Word tables routinely
 * exceed viewport width (multi-column data tables, code-listing tables,
 * sign-off matrices). Truncating would lose data; scroll is the
 * SAF-reader-friendly default.
 *
 * First row is shown as a header (bold, surfaceContainerHigh
 * background). Even/odd row zebra-striping inherits from Material's
 * `surface`/`surfaceContainerLowest` ladder for readability on tall
 * tables.
 */
@Composable
internal fun RichTextTable(block: RichTextBlock.Table, modifier: Modifier = Modifier) {
  val colors = MaterialTheme.colorScheme
  Column(
    modifier = modifier
      .fillMaxWidth()
      .padding(vertical = 4.dp)
      .border(1.dp, colors.outlineVariant, RoundedCornerShape(4.dp))
      .horizontalScroll(rememberScrollState()),
  ) {
    for ((rowIndex, row) in block.rows.withIndex()) {
      val isHeader = rowIndex == 0
      val rowBg = when {
        isHeader -> colors.surfaceContainerHigh
        rowIndex % 2 == 0 -> colors.surface
        else -> colors.surfaceContainerLowest
      }
      Row(modifier = Modifier.background(rowBg)) {
        for (cell in row) {
          val folded = RichTextRuns.foldInlinesFromTheme(cell.runs)
          val style = if (isHeader) {
            MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
          } else {
            MaterialTheme.typography.bodyMedium
          }
          Box(
            modifier = Modifier
              .border(0.5.dp, colors.outlineVariant)
              .padding(horizontal = 12.dp, vertical = 8.dp),
          ) {
            Text(text = folded, style = style)
          }
        }
      }
    }
  }
}
