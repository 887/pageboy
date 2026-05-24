package com.eight87.pageboy.format.markdown

import com.eight87.pageboy.data.library.DocumentFormat
import com.eight87.pageboy.format.api.DocumentHandle
import org.commonmark.node.Node

/**
 * Phase D — opened-document state for the Markdown renderer.
 *
 * Carries the parsed commonmark AST + the raw markdown text (for
 * find-in-doc substring search) + the first-H1-derived title + the
 * front-matter map sniffed off the top of the file (Obsidian / Jekyll /
 * Hugo metadata preamble).
 *
 * `pageCount` is `null` — Markdown is a reflowable format, the chrome
 * doesn't surface a page index.
 *
 * `close()` is a no-op — there are no native resources or open file
 * descriptors. The AST + the strings are plain JVM objects that the GC
 * reclaims when the chrome drops its reference.
 */
data class MarkdownHandle(
  val ast: Node,
  val rawText: String,
  val frontMatter: Map<String, String>,
  override val title: String,
) : DocumentHandle {

  override val format: DocumentFormat = DocumentFormat.Markdown
  override val pageCount: Int? = null

  override fun close() {
    // no-op — AST + strings are JVM-managed.
  }
}
