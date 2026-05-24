package com.eight87.pageboy.format.markdown

import org.commonmark.node.AbstractVisitor
import org.commonmark.node.Code
import org.commonmark.node.Heading
import org.commonmark.node.Node
import org.commonmark.node.Text

/**
 * Phase D / D.6 — first-H1 plain-text title probe. Used by
 * [MarkdownRenderer.extractTitle] (so the scanner can use a real title
 * instead of `notes.md`) and by [MarkdownRenderer.open] to populate
 * [MarkdownHandle.title].
 *
 * Walks the AST until it finds the first `Heading(level=1)`, then
 * collapses its inline children into a single string. Bold / italic /
 * code spans are flattened to their text content — the reader top bar
 * has no font-style affordance.
 *
 * Returns `null` when:
 *  - no H1 exists (very common for note-style markdown that uses H2+ as
 *    the top header), or
 *  - the H1 is empty (`#` with no content — degenerate but legal).
 *
 * The "first H1" rule (instead of "first heading") is deliberate. Many
 * notes start with `## Date` or `## Tag` — those are sub-section headers,
 * not document titles. The scanner's filename-derived fallback is a
 * better choice than picking up a stray H2.
 */
internal object MarkdownTitleExtractor {

  fun extract(ast: Node): String? {
    var captured: String? = null
    ast.accept(object : AbstractVisitor() {
      override fun visit(heading: Heading) {
        if (captured != null) return
        if (heading.level != 1) {
          // Don't recurse into deeper headings; we only want the first H1.
          super.visit(heading)
          return
        }
        val text = flatten(heading)
        if (text.isNotEmpty()) {
          captured = text
        }
      }
    })
    return captured
  }

  private fun flatten(node: Node): String {
    val buf = StringBuilder()
    var child = node.firstChild
    while (child != null) {
      when (child) {
        is Text -> buf.append(child.literal)
        is Code -> buf.append(child.literal)
        else -> buf.append(flatten(child))
      }
      child = child.next
    }
    return buf.toString().trim()
  }
}
