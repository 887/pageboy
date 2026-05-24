package com.eight87.pageboy.format.markdown

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
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.commonmark.ext.gfm.tables.TableBlock
import org.commonmark.ext.gfm.tables.TableBody
import org.commonmark.ext.gfm.tables.TableCell
import org.commonmark.ext.gfm.tables.TableHead
import org.commonmark.ext.gfm.tables.TableRow
import org.commonmark.node.Node

/**
 * Phase D — GFM table renderer split off [MarkdownBlocks] to keep that
 * file under the R.X.4 file-size threshold. One concern: walk the
 * `TableBlock` → `TableHead` / `TableBody` → `TableRow` → `TableCell`
 * tree, render each cell as an inline-folded `AnnotatedString` inside a
 * bordered `Box`.
 *
 * The table is wrapped in a horizontal scroller because GFM tables
 * routinely contain content wider than the viewport (URLs, code spans,
 * long English sentences). Truncating would lose information; scrolling
 * is the SAF-reader-friendly default.
 */
@Composable
internal fun TableBlockView(node: TableBlock, modifier: Modifier) {
  val colors = MaterialTheme.colorScheme
  val typography = MaterialTheme.typography
  Column(
    modifier = modifier
      .fillMaxWidth()
      .padding(vertical = 4.dp)
      .border(1.dp, colors.outlineVariant, RoundedCornerShape(4.dp))
      .horizontalScroll(rememberScrollState()),
  ) {
    var section: Node? = node.firstChild
    while (section != null) {
      when (val s = section) {
        is TableHead -> TableRows(s, header = true, colors = colors, typography = typography)
        is TableBody -> TableRows(s, header = false, colors = colors, typography = typography)
        else -> Unit
      }
      section = section.next
    }
  }
}

@Composable
private fun TableRows(
  section: Node,
  header: Boolean,
  colors: ColorScheme,
  typography: Typography,
) {
  var row: Node? = section.firstChild
  while (row is TableRow) {
    Row(
      modifier = Modifier
        .background(if (header) colors.surfaceContainerHigh else colors.surface),
    ) {
      var cell: Node? = row.firstChild
      while (cell is TableCell) {
        val text = MarkdownInlines.foldInlines(cell, colors, typography)
        val style =
          if (header) MarkdownStyle.paragraphStyle(typography, colors).copy(fontWeight = FontWeight.SemiBold)
          else MarkdownStyle.paragraphStyle(typography, colors)
        Box(
          modifier = Modifier
            .border(0.5.dp, colors.outlineVariant)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
          Text(text = text, style = style)
        }
        cell = cell.next
      }
    }
    row = row.next
  }
}
