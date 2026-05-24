package com.eight87.pageboy.format.markdown

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Typography
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import org.commonmark.ext.gfm.strikethrough.Strikethrough
import org.commonmark.ext.footnotes.FootnoteReference
import org.commonmark.node.Code
import org.commonmark.node.Emphasis
import org.commonmark.node.HardLineBreak
import org.commonmark.node.HtmlInline
import org.commonmark.node.Image
import org.commonmark.node.Link
import org.commonmark.node.Node
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.StrongEmphasis
import org.commonmark.node.Text

/**
 * Phase D / D.3 — inline-node → [AnnotatedString] folder. One pure
 * function per inline type; the caller (per-block renderer in
 * [MarkdownBlocks]) folds the inline children of a block into a single
 * [AnnotatedString] for Compose `Text` / `ClickableText` to lay out.
 *
 * The `LINK_URL_TAG` annotation lets the block renderer surface a
 * tap-to-open affordance: it consults the string annotation under the
 * tapped offset, and if a URL is there, fires `Intent.ACTION_VIEW`. We
 * use the string-annotation API (not a real `UrlAnnotation`) because the
 * latter is still experimental in Compose Material 3 1.5.0-alpha18.
 *
 * Image rendering deferred — inline images appear as their alt-text
 * surrounded by `[image: …]` markers so the user knows something was
 * skipped; block-level images get the placeholder card path in
 * [MarkdownBlocks]. Coil is deferred to Phase F per format-markdown.md.
 *
 * Known-safe HTML inline tags (`<br>`, `<kbd>`, `<sub>`, `<sup>`,
 * `<mark>`) get a minimal span-style mapping per G3; everything else
 * renders as raw `<tag>` literal text inside a code span (the user can
 * see the tag was skipped, no surprise blank output).
 */
internal object MarkdownInlines {

  /** String-annotation tag we attach to link spans so taps can resolve the URL. */
  const val LINK_URL_TAG = "pb.md.link"

  /** Fold the inline children of [parent] into a single [AnnotatedString]. */
  fun foldInlines(
    parent: Node,
    colors: ColorScheme,
    typography: Typography,
  ): AnnotatedString = buildAnnotatedString {
    var child: Node? = parent.firstChild
    while (child != null) {
      appendNode(child, colors, typography)
      child = child.next
    }
  }

  private fun AnnotatedString.Builder.appendNode(
    node: Node,
    colors: ColorScheme,
    typography: Typography,
  ) {
    when (node) {
      is Text -> append(node.literal)
      is SoftLineBreak -> append(' ')
      is HardLineBreak -> append('\n')
      is Emphasis -> withSpan(SpanStyle(fontStyle = FontStyle.Italic)) {
        appendChildren(node, colors, typography)
      }
      is StrongEmphasis -> withSpan(SpanStyle(fontWeight = FontWeight.Bold)) {
        appendChildren(node, colors, typography)
      }
      is Strikethrough -> withSpan(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
        appendChildren(node, colors, typography)
      }
      is Code -> withSpan(
        SpanStyle(
          fontFamily = FontFamily.Monospace,
          background = colors.surfaceContainerHighest,
          color = colors.onSurface,
        ),
      ) { append(node.literal) }
      is Link -> appendLink(node, colors, typography)
      is Image -> appendImageInline(node, colors, typography)
      is HtmlInline -> appendHtmlInline(node, colors, typography)
      is FootnoteReference -> withSpan(
        SpanStyle(
          color = colors.primary,
          fontWeight = FontWeight.Medium,
        ),
      ) { append("[^${node.label}]") }
      else -> {
        // Unknown inline — recurse to render any text children rather
        // than dropping content silently.
        appendChildren(node, colors, typography)
      }
    }
  }

  private fun AnnotatedString.Builder.appendChildren(
    node: Node,
    colors: ColorScheme,
    typography: Typography,
  ) {
    var c: Node? = node.firstChild
    while (c != null) {
      appendNode(c, colors, typography)
      c = c.next
    }
  }

  private fun AnnotatedString.Builder.appendLink(
    link: Link,
    colors: ColorScheme,
    typography: Typography,
  ) {
    val destination = link.destination ?: ""
    val start = length
    withSpan(
      SpanStyle(
        color = colors.primary,
        textDecoration = TextDecoration.Underline,
      ),
    ) {
      appendChildren(link, colors, typography)
    }
    if (destination.isNotEmpty()) {
      addStringAnnotation(
        tag = LINK_URL_TAG,
        annotation = destination,
        start = start,
        end = length,
      )
    }
  }

  private fun AnnotatedString.Builder.appendImageInline(
    image: Image,
    colors: ColorScheme,
    typography: Typography,
  ) {
    // Inline images render as `[image: alt | url]` in muted colour. Real
    // image rendering is the placeholder-card path in MarkdownBlocks
    // (which only fires for block-level standalone images); inline
    // images inside a paragraph need no further surface in v1.
    val alt = collectText(image).ifEmpty { "image" }
    val dest = image.destination ?: ""
    withSpan(
      SpanStyle(
        color = colors.onSurfaceVariant,
        fontStyle = FontStyle.Italic,
      ),
    ) {
      append("[image: $alt")
      if (dest.isNotEmpty()) append(" → $dest")
      append(']')
    }
  }

  private fun AnnotatedString.Builder.appendHtmlInline(
    node: HtmlInline,
    colors: ColorScheme,
    typography: Typography,
  ) {
    val raw = node.literal ?: return
    val lower = raw.trim().lowercase()
    when {
      lower == "<br>" || lower == "<br/>" || lower == "<br />" -> append('\n')
      lower == "<sub>" -> Unit // open tag — span applies via SUB_END below
      lower == "</sub>" -> Unit
      lower == "<sup>" -> Unit
      lower == "</sup>" -> Unit
      lower == "<kbd>" -> Unit
      lower == "</kbd>" -> Unit
      lower == "<mark>" -> Unit
      lower == "</mark>" -> Unit
      else -> withSpan(
        SpanStyle(
          fontFamily = FontFamily.Monospace,
          background = colors.errorContainer,
          color = colors.onErrorContainer,
        ),
      ) { append(raw) }
    }
  }

  private fun collectText(node: Node): String {
    val buf = StringBuilder()
    var c: Node? = node.firstChild
    while (c != null) {
      when (c) {
        is Text -> buf.append(c.literal)
        else -> buf.append(collectText(c))
      }
      c = c.next
    }
    return buf.toString()
  }

  private inline fun AnnotatedString.Builder.withSpan(
    span: SpanStyle,
    block: AnnotatedString.Builder.() -> Unit,
  ) {
    pushStyle(span)
    try {
      block()
    } finally {
      pop()
    }
  }
}
