package com.eight87.pageboy.format.markdown

import org.commonmark.ext.footnotes.FootnoteDefinition
import org.commonmark.ext.gfm.tables.TableBlock
import org.commonmark.node.BlockQuote
import org.commonmark.node.BulletList
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.Heading
import org.commonmark.node.HtmlBlock
import org.commonmark.node.Image
import org.commonmark.node.IndentedCodeBlock
import org.commonmark.node.LinkReferenceDefinition
import org.commonmark.node.ListBlock
import org.commonmark.node.Node
import org.commonmark.node.OrderedList
import org.commonmark.node.Paragraph
import org.commonmark.node.ThematicBreak

/**
 * Phase D — flat list of top-level block kinds the renderer feeds into
 * a `LazyColumn`. Sealed dispatch (R.X.2) — adding a block kind = adding
 * a variant + one renderer arm, no `when (node.javaClass)` chain
 * scattered across the file.
 *
 * Why a flat list (and not a recursive render-tree walk inside the
 * Composable)?
 *  - `LazyColumn` only lazy-composes its top-level items. Long markdown
 *    documents (think: a 50-page Obsidian note) render with bounded
 *    work per frame because each top-level block is one item; off-screen
 *    items don't compose at all.
 *  - Find-in-doc and scroll-restoration both want a stable item-index
 *    surface; flattening up front gives us one.
 *  - Nested constructs (block-quote-inside-list-inside-blockquote) stay
 *    recursive *inside* their own renderer — the flat list is only for
 *    the LazyColumn's top-level item-keying.
 */
internal sealed interface MarkdownBlock {
  data class Heading(val node: org.commonmark.node.Heading) : MarkdownBlock
  data class Paragraph(val node: org.commonmark.node.Paragraph) : MarkdownBlock
  data class BlockQuote(val node: org.commonmark.node.BlockQuote) : MarkdownBlock
  data class BulletList(val node: org.commonmark.node.BulletList) : MarkdownBlock
  data class OrderedList(val node: org.commonmark.node.OrderedList) : MarkdownBlock
  data class FencedCode(val node: FencedCodeBlock) : MarkdownBlock
  data class IndentedCode(val node: IndentedCodeBlock) : MarkdownBlock
  data class Html(val node: HtmlBlock) : MarkdownBlock
  data class Thematic(val node: ThematicBreak) : MarkdownBlock
  data class Table(val node: TableBlock) : MarkdownBlock
  data class StandaloneImage(val node: Image, val originalParagraph: org.commonmark.node.Paragraph) : MarkdownBlock
  data class Footnote(val node: FootnoteDefinition) : MarkdownBlock
  data class Unknown(val node: Node) : MarkdownBlock
}

/**
 * Walk the top-level children of an AST root and collapse them into a
 * flat list of [MarkdownBlock]s.
 *
 * A paragraph that contains only a single block-level image (the
 * `![alt](url)` on its own line case) is unwrapped to a
 * [MarkdownBlock.StandaloneImage] so the renderer can show the
 * placeholder-card affordance instead of an inline `[image: …]` token.
 *
 * [LinkReferenceDefinition] nodes are dropped — they have no rendered
 * content (they're consumed by `[label][ref]` link inlines elsewhere).
 *
 * Footnote definitions are collected at the end so they always render
 * below the body, regardless of their position in the source.
 */
internal fun flattenBlocks(root: Node): List<MarkdownBlock> {
  val main = ArrayList<MarkdownBlock>()
  val footnotes = ArrayList<MarkdownBlock>()
  var child: Node? = root.firstChild
  while (child != null) {
    when (val node = child) {
      is FootnoteDefinition -> footnotes += MarkdownBlock.Footnote(node)
      is LinkReferenceDefinition -> Unit
      is Paragraph -> main += paragraphOrImage(node)
      is Heading -> main += MarkdownBlock.Heading(node)
      is BlockQuote -> main += MarkdownBlock.BlockQuote(node)
      is BulletList -> main += MarkdownBlock.BulletList(node)
      is OrderedList -> main += MarkdownBlock.OrderedList(node)
      is FencedCodeBlock -> main += MarkdownBlock.FencedCode(node)
      is IndentedCodeBlock -> main += MarkdownBlock.IndentedCode(node)
      is HtmlBlock -> main += MarkdownBlock.Html(node)
      is ThematicBreak -> main += MarkdownBlock.Thematic(node)
      is TableBlock -> main += MarkdownBlock.Table(node)
      else -> main += MarkdownBlock.Unknown(node)
    }
    child = child.next
  }
  if (footnotes.isNotEmpty()) {
    main += footnotes
  }
  return main
}

private fun paragraphOrImage(p: Paragraph): MarkdownBlock {
  val first = p.firstChild ?: return MarkdownBlock.Paragraph(p)
  if (first is Image && first.next == null) {
    return MarkdownBlock.StandaloneImage(first, p)
  }
  return MarkdownBlock.Paragraph(p)
}

internal fun isListBlock(b: MarkdownBlock): Boolean =
  b is MarkdownBlock.BulletList || b is MarkdownBlock.OrderedList

internal fun listBlockNode(b: MarkdownBlock): ListBlock? = when (b) {
  is MarkdownBlock.BulletList -> b.node
  is MarkdownBlock.OrderedList -> b.node
  else -> null
}
