package com.eight87.pageboy.format.markdown

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.commonmark.ext.task.list.items.TaskListItemMarker
import org.commonmark.node.BulletList
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.IndentedCodeBlock
import org.commonmark.node.ListItem
import org.commonmark.node.Node
import org.commonmark.node.OrderedList
import org.commonmark.node.Paragraph

/**
 * Phase D — list renderers split off [MarkdownBlocks] for R.X.4
 * file-size discipline. Bullet + ordered + task-list-item all share the
 * same shape: marker on the left, content on the right; content
 * recurses back through [RenderBlock] for nested constructs (blockquote
 * inside a list item, sub-list, etc.).
 *
 * Depth indents at 16 dp per level — keeps deeply nested lists
 * readable on a phone-width viewport without running off-screen for
 * realistic depths (3–4 levels).
 */
@Composable
internal fun BulletListBlock(node: BulletList, depth: Int, modifier: Modifier) {
  Column(modifier = modifier.fillMaxWidth().padding(vertical = 2.dp)) {
    var item: Node? = node.firstChild
    while (item is ListItem) {
      ListItemRow(
        marker = { ListMarker(text = "•") },
        item = item,
        depth = depth,
      )
      item = item.next
    }
  }
}

@Composable
internal fun OrderedListBlock(node: OrderedList, depth: Int, modifier: Modifier) {
  val startNumber = node.markerStartNumber ?: 1
  Column(modifier = modifier.fillMaxWidth().padding(vertical = 2.dp)) {
    var index = startNumber
    var item: Node? = node.firstChild
    while (item is ListItem) {
      val n = index
      ListItemRow(
        marker = { ListMarker(text = "$n.") },
        item = item,
        depth = depth,
      )
      index += 1
      item = item.next
    }
  }
}

@Composable
private fun ListItemRow(marker: @Composable () -> Unit, item: ListItem, depth: Int) {
  val indent = (depth * 16).dp
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(start = indent, top = 2.dp, bottom = 2.dp),
    verticalAlignment = Alignment.Top,
  ) {
    val taskMarker = item.firstChild as? TaskListItemMarker
    if (taskMarker != null) {
      Checkbox(
        checked = taskMarker.isChecked,
        onCheckedChange = null,
        modifier = Modifier.padding(end = 4.dp),
      )
    } else {
      Box(modifier = Modifier.padding(end = 8.dp)) { marker() }
    }
    Column(modifier = Modifier.fillMaxWidth()) {
      RenderListItemChildren(item, depth)
    }
  }
}

@Composable
private fun RenderListItemChildren(item: ListItem, depth: Int) {
  val typography = MaterialTheme.typography
  val colors = MaterialTheme.colorScheme
  val style = MarkdownStyle.listItemStyle(typography, colors)
  var child: Node? = item.firstChild
  while (child != null) {
    when (val node = child) {
      is TaskListItemMarker -> Unit
      is Paragraph -> {
        val text = MarkdownInlines.foldInlines(node, colors, typography)
        ClickableInlineText(text = text, style = style, modifier = Modifier.fillMaxWidth())
      }
      is BulletList -> BulletListBlock(node, depth = depth + 1, modifier = Modifier.fillMaxWidth())
      is OrderedList -> OrderedListBlock(node, depth = depth + 1, modifier = Modifier.fillMaxWidth())
      is org.commonmark.node.BlockQuote -> BlockQuoteBlock(node, modifier = Modifier.fillMaxWidth())
      is FencedCodeBlock -> FencedCodeBlockView(node, modifier = Modifier.fillMaxWidth())
      is IndentedCodeBlock -> IndentedCodeBlockView(node, modifier = Modifier.fillMaxWidth())
      else -> {
        val text = MarkdownInlines.foldInlines(node, colors, typography)
        if (text.isNotEmpty()) {
          ClickableInlineText(text = text, style = style, modifier = Modifier.fillMaxWidth())
        }
      }
    }
    child = child.next
  }
}

@Composable
private fun ListMarker(text: String) {
  Text(
    text = text,
    style = MarkdownStyle.listItemStyle(MaterialTheme.typography, MaterialTheme.colorScheme),
  )
}
