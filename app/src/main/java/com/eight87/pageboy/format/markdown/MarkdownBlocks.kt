package com.eight87.pageboy.format.markdown

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.commonmark.ext.footnotes.FootnoteDefinition
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.Heading
import org.commonmark.node.HtmlBlock
import org.commonmark.node.Image
import org.commonmark.node.IndentedCodeBlock
import org.commonmark.node.Node
import org.commonmark.node.Paragraph

/**
 * Phase D / D.3 — top-level dispatch from a [MarkdownBlock] variant to
 * its Composable renderer. Sealed dispatch (R.X.2) — adding a block
 * kind = adding a variant + one arm; no `when (node.javaClass)` chains
 * scattered across the file.
 *
 * Heavy renderers split out per R.X.4 file-size discipline:
 *   - lists → [MarkdownLists]
 *   - tables → [MarkdownTable]
 *   - inline → [MarkdownInlines]
 *
 * This file owns the leaf renderers (heading / paragraph / blockquote /
 * code blocks / divider / image placeholder / footnote / HTML
 * fallback) plus the link tap-to-open glue used by every block that
 * carries inline content.
 */
@Composable
internal fun RenderBlock(block: MarkdownBlock, modifier: Modifier = Modifier) {
  when (block) {
    is MarkdownBlock.Heading -> HeadingBlock(block.node, modifier)
    is MarkdownBlock.Paragraph -> ParagraphBlock(block.node, modifier)
    is MarkdownBlock.BlockQuote -> BlockQuoteBlock(block.node, modifier)
    is MarkdownBlock.BulletList -> BulletListBlock(block.node, depth = 0, modifier = modifier)
    is MarkdownBlock.OrderedList -> OrderedListBlock(block.node, depth = 0, modifier = modifier)
    is MarkdownBlock.FencedCode -> FencedCodeBlockView(block.node, modifier)
    is MarkdownBlock.IndentedCode -> IndentedCodeBlockView(block.node, modifier)
    is MarkdownBlock.Html -> HtmlBlockView(block.node, modifier)
    is MarkdownBlock.Thematic -> ThematicView(modifier)
    is MarkdownBlock.Table -> TableBlockView(block.node, modifier)
    is MarkdownBlock.StandaloneImage -> StandaloneImageView(block.node, modifier)
    is MarkdownBlock.Footnote -> FootnoteDefinitionView(block.node, modifier)
    is MarkdownBlock.Unknown -> UnknownBlockView(block.node, modifier)
  }
}

@Composable
private fun HeadingBlock(node: Heading, modifier: Modifier) {
  val typography = MaterialTheme.typography
  val colors = MaterialTheme.colorScheme
  val style = MarkdownStyle.headingStyle(node.level, typography, colors)
  val text = MarkdownInlines.foldInlines(node, colors, typography)
  ClickableInlineText(text = text, style = style, modifier = modifier)
}

@Composable
private fun ParagraphBlock(node: Paragraph, modifier: Modifier) {
  val typography = MaterialTheme.typography
  val colors = MaterialTheme.colorScheme
  val style = MarkdownStyle.paragraphStyle(typography, colors)
  val text = MarkdownInlines.foldInlines(node, colors, typography)
  ClickableInlineText(text = text, style = style, modifier = modifier)
}

@Composable
internal fun BlockQuoteBlock(node: org.commonmark.node.BlockQuote, modifier: Modifier) {
  val colors = MaterialTheme.colorScheme
  Row(
    modifier = modifier
      .fillMaxWidth()
      .padding(vertical = 4.dp),
  ) {
    Box(
      modifier = Modifier
        .width(4.dp)
        .background(colors.outline, RoundedCornerShape(2.dp)),
    ) {
      // 4 dp accent rail — measurable-height spacer aligned with the
      // surrounding content.
      Text(" ")
    }
    Column(
      modifier = Modifier
        .padding(start = 12.dp)
        .fillMaxWidth(),
    ) {
      RenderNestedBlocks(parent = node, modifier = Modifier.fillMaxWidth())
    }
  }
}

@Composable
internal fun FencedCodeBlockView(node: FencedCodeBlock, modifier: Modifier) {
  val colors = MaterialTheme.colorScheme
  val typography = MaterialTheme.typography
  // TODO(phase D+/v1.1): plug Prism4j fork for syntax highlighting per
  // format-markdown.md G2 / D.10 — fenced code ships monochrome in v1.
  Surface(
    modifier = modifier
      .fillMaxWidth()
      .padding(vertical = 4.dp),
    color = colors.surfaceContainer,
    shape = RoundedCornerShape(8.dp),
  ) {
    Column(modifier = Modifier.padding(12.dp)) {
      node.info?.takeIf { it.isNotBlank() }?.let { lang ->
        Text(
          text = lang.trim(),
          style = MarkdownStyle.captionStyle(typography, colors),
          modifier = Modifier.padding(bottom = 4.dp),
        )
      }
      Box(modifier = Modifier.horizontalScroll(rememberScrollState())) {
        Text(
          text = node.literal ?: "",
          style = MarkdownStyle.codeStyle(typography, colors),
        )
      }
    }
  }
}

@Composable
internal fun IndentedCodeBlockView(node: IndentedCodeBlock, modifier: Modifier) {
  val colors = MaterialTheme.colorScheme
  val typography = MaterialTheme.typography
  Surface(
    modifier = modifier
      .fillMaxWidth()
      .padding(vertical = 4.dp),
    color = colors.surfaceContainer,
    shape = RoundedCornerShape(8.dp),
  ) {
    Box(modifier = Modifier.padding(12.dp).horizontalScroll(rememberScrollState())) {
      Text(
        text = node.literal ?: "",
        style = MarkdownStyle.codeStyle(typography, colors),
      )
    }
  }
}

@Composable
private fun HtmlBlockView(node: HtmlBlock, modifier: Modifier) {
  // Per format-markdown.md G3 — never render arbitrary HTML. Surface
  // the raw source inside an error-container Surface so the user sees
  // something was skipped.
  val colors = MaterialTheme.colorScheme
  val typography = MaterialTheme.typography
  Surface(
    modifier = modifier
      .fillMaxWidth()
      .padding(vertical = 4.dp),
    color = colors.errorContainer,
    shape = RoundedCornerShape(8.dp),
  ) {
    Box(modifier = Modifier.padding(12.dp).horizontalScroll(rememberScrollState())) {
      Text(
        text = node.literal ?: "",
        style = MarkdownStyle.codeStyle(typography, colors).copy(color = colors.onErrorContainer),
      )
    }
  }
}

@Composable
private fun ThematicView(modifier: Modifier) {
  HorizontalDivider(
    modifier = modifier
      .fillMaxWidth()
      .padding(vertical = 8.dp),
    color = MaterialTheme.colorScheme.outlineVariant,
  )
}

@Composable
private fun StandaloneImageView(image: Image, modifier: Modifier) {
  // Per D.10 — image rendering is a placeholder card; Coil deferred to
  // Phase F (PDF page rasters earn it). Tap opens the URL via
  // ACTION_VIEW so the user can still see the image in their browser
  // or image viewer.
  val colors = MaterialTheme.colorScheme
  val typography = MaterialTheme.typography
  val alt = collectInlineText(image)
  val dest = image.destination ?: ""
  val context = LocalContext.current
  Surface(
    modifier = modifier
      .fillMaxWidth()
      .padding(vertical = 4.dp)
      .clickable {
        if (dest.isNotEmpty()) launchUri(context, dest)
      },
    color = colors.surfaceContainer,
    shape = RoundedCornerShape(8.dp),
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Text(
        text = "Image",
        style = MarkdownStyle.captionStyle(typography, colors),
      )
      if (alt.isNotEmpty()) {
        Text(
          text = alt,
          style = MarkdownStyle.paragraphStyle(typography, colors)
            .copy(fontStyle = FontStyle.Italic),
        )
      }
      if (dest.isNotEmpty()) {
        Text(
          text = dest,
          style = MarkdownStyle.codeStyle(typography, colors)
            .copy(color = colors.primary),
        )
      }
    }
  }
}

@Composable
private fun FootnoteDefinitionView(node: FootnoteDefinition, modifier: Modifier) {
  val colors = MaterialTheme.colorScheme
  val typography = MaterialTheme.typography
  Row(
    modifier = modifier
      .fillMaxWidth()
      .padding(vertical = 2.dp),
  ) {
    Text(
      text = "[^${node.label}]",
      style = MarkdownStyle.captionStyle(typography, colors)
        .copy(color = colors.primary, fontWeight = FontWeight.Medium),
      modifier = Modifier.padding(end = 8.dp),
    )
    Column(modifier = Modifier.fillMaxWidth()) {
      RenderNestedBlocks(parent = node, modifier = Modifier.fillMaxWidth())
    }
  }
}

@Composable
private fun UnknownBlockView(node: Node, modifier: Modifier) {
  // Last-resort fallback so the renderer never silently swallows
  // content. Render the inline-folded text of whatever-this-was so the
  // user can at least see the affected words.
  val colors = MaterialTheme.colorScheme
  val typography = MaterialTheme.typography
  val text = MarkdownInlines.foldInlines(node, colors, typography)
  if (text.isEmpty()) return
  Text(
    text = text,
    style = MarkdownStyle.paragraphStyle(typography, colors),
    modifier = modifier.fillMaxWidth(),
  )
}

/**
 * Recursively render the block-level children of [parent] back through
 * the [RenderBlock] dispatch table. Used by blockquote / footnote
 * containers so nesting works without each container re-implementing
 * the dispatch.
 */
@Composable
internal fun RenderNestedBlocks(parent: Node, modifier: Modifier = Modifier) {
  Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
    var child: Node? = parent.firstChild
    while (child != null) {
      val node = child
      when (node) {
        is Heading -> RenderBlock(MarkdownBlock.Heading(node))
        is Paragraph -> {
          val first = node.firstChild
          if (first is Image && first.next == null) {
            RenderBlock(MarkdownBlock.StandaloneImage(first, node))
          } else {
            RenderBlock(MarkdownBlock.Paragraph(node))
          }
        }
        is org.commonmark.node.BlockQuote -> RenderBlock(MarkdownBlock.BlockQuote(node))
        is org.commonmark.node.BulletList -> RenderBlock(MarkdownBlock.BulletList(node))
        is org.commonmark.node.OrderedList -> RenderBlock(MarkdownBlock.OrderedList(node))
        is FencedCodeBlock -> RenderBlock(MarkdownBlock.FencedCode(node))
        is IndentedCodeBlock -> RenderBlock(MarkdownBlock.IndentedCode(node))
        is HtmlBlock -> RenderBlock(MarkdownBlock.Html(node))
        is org.commonmark.node.ThematicBreak -> RenderBlock(MarkdownBlock.Thematic(node))
        is org.commonmark.ext.gfm.tables.TableBlock -> RenderBlock(MarkdownBlock.Table(node))
        else -> {
          val text = MarkdownInlines.foldInlines(
            node,
            MaterialTheme.colorScheme,
            MaterialTheme.typography,
          )
          if (text.isNotEmpty()) {
            Text(
              text = text,
              style = MarkdownStyle.paragraphStyle(MaterialTheme.typography, MaterialTheme.colorScheme),
            )
          }
        }
      }
      child = child.next
    }
  }
}

/**
 * `ClickableText`-backed wrapper that resolves the link-URL annotation
 * placed by [MarkdownInlines.appendLink] and fires `ACTION_VIEW` on tap.
 *
 * For text with no link annotations we fall back to a plain `Text` —
 * `ClickableText` always builds a pressed-state ripple, which is
 * visual noise for non-link content.
 */
@Composable
internal fun ClickableInlineText(
  text: AnnotatedString,
  style: androidx.compose.ui.text.TextStyle,
  modifier: Modifier = Modifier,
) {
  val hasLinks = text.getStringAnnotations(MarkdownInlines.LINK_URL_TAG, 0, text.length).isNotEmpty()
  if (!hasLinks) {
    Text(text = text, style = style, modifier = modifier.fillMaxWidth().padding(vertical = 2.dp))
    return
  }
  val context = LocalContext.current
  ClickableText(
    text = text,
    style = style,
    modifier = modifier.fillMaxWidth().padding(vertical = 2.dp),
    onClick = { offset ->
      text.getStringAnnotations(MarkdownInlines.LINK_URL_TAG, offset, offset).firstOrNull()
        ?.let { launchUri(context, it.item) }
    },
  )
}

private fun launchUri(context: android.content.Context, uri: String) {
  runCatching {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
      .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
  }.onFailure { t ->
    if (t is ActivityNotFoundException) {
      // No app handles this scheme — silently swallow; the chrome has
      // no error-toast surface yet.
    }
  }
}

private fun collectInlineText(node: Node): String {
  val buf = StringBuilder()
  var c: Node? = node.firstChild
  while (c != null) {
    when (c) {
      is org.commonmark.node.Text -> buf.append(c.literal)
      else -> buf.append(collectInlineText(c))
    }
    c = c.next
  }
  return buf.toString()
}
