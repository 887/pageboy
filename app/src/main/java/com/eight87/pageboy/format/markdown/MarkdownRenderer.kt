package com.eight87.pageboy.format.markdown

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.eight87.pageboy.data.library.DocumentFormat
import com.eight87.pageboy.domain.render.RendererContext
import com.eight87.pageboy.format.api.DocumentBytesSource
import com.eight87.pageboy.format.api.DocumentHandle
import com.eight87.pageboy.format.api.DocumentRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Phase D — first real [DocumentRenderer] impl. Markdown end-to-end.
 *
 * Thin façade: [open] reads the SAF bytes-stream into a String, strips
 * front-matter via [MarkdownFrontMatter], hands the body to
 * [MarkdownParser], derives the title via [MarkdownTitleExtractor], and
 * wraps the AST + raw text + frontmatter into a [MarkdownHandle].
 *
 * [Body] delegates to [MarkdownBody] — the actual Compose rendering
 * lives in its own file (R.X.4 — no god-files; this renderer stays
 * under 100 LOC).
 *
 * [extractTitle] is a cheaper version of [open] that stops as soon as
 * the first H1 is captured (well, after a full parse — commonmark does
 * not stream — but it avoids holding the AST). Used by the library
 * scanner when it lands a title-extraction pass (Phase B currently
 * derives titles from the filename; the hook is in place for a later
 * phase to consume).
 *
 * SOLID notes:
 *  - **R.X.1** narrow — takes a [DocumentBytesSource], emits a
 *    [DocumentHandle]; no `Context`, no `LibraryRepository`.
 *  - **R.X.5** no `NotImplementedError` — every block type in the AST
 *    falls into one of the [MarkdownBlocks] dispatch arms (unknown
 *    blocks render as a raw monospace fallback per G3).
 *  - **R.X.6** does not import `data/library/` (except the closed-enum
 *    `DocumentFormat`, which is the documented narrow exception from
 *    Phase C audit observation O.C.1).
 *  - **R.X.9** is the second `DocumentRenderer` after the placeholder;
 *    proves the open/closed dispatch lands without changing the chrome.
 */
class MarkdownRenderer(
  private val parser: MarkdownParser,
) : DocumentRenderer {

  override val format: DocumentFormat = DocumentFormat.Markdown

  override suspend fun open(source: DocumentBytesSource): DocumentHandle = withContext(Dispatchers.IO) {
    val raw = source.openStream().use { stream -> stream.readBytes().toString(Charsets.UTF_8) }
    val (frontMatter, body) = MarkdownFrontMatter.split(raw).let { it.frontMatter to it.body }
    val ast = parser.parse(body)
    val title = MarkdownTitleExtractor.extract(ast)
      ?: frontMatter["title"]
      ?: source.displayName()
      ?: DEFAULT_TITLE
    MarkdownHandle(
      ast = ast,
      rawText = body,
      frontMatter = frontMatter,
      title = title,
    )
  }

  @Composable
  override fun Body(handle: DocumentHandle, context: RendererContext, modifier: Modifier) {
    val md = handle as? MarkdownHandle ?: return
    MarkdownBody(handle = md, context = context, modifier = modifier)
  }

  override suspend fun extractTitle(source: DocumentBytesSource): String? = withContext(Dispatchers.IO) {
    runCatching {
      val raw = source.openStream().use { it.readBytes().toString(Charsets.UTF_8) }
      val (fm, body) = MarkdownFrontMatter.split(raw).let { it.frontMatter to it.body }
      val ast = parser.parse(body)
      MarkdownTitleExtractor.extract(ast) ?: fm["title"]
    }.getOrNull()
  }

  private companion object {
    const val DEFAULT_TITLE = "Markdown document"
  }
}
