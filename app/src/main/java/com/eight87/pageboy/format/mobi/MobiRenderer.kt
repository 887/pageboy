package com.eight87.pageboy.format.mobi

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
 * Phase Q — MOBI [DocumentRenderer] impl.
 *
 * Thin façade: [open] reads the SAF byte stream into memory, feeds it
 * to [MobiParser], wraps the parsed [MobiBookContent] in a
 * [MobiHandle]. [Body] dispatches into `MobiBody.kt` so this file
 * stays under R.X.4.
 *
 * [extractTitle] reuses [MobiTitleExtractor] (which only touches the
 * PalmDB envelope + record 0 — no body decompress) so the scanner can
 * surface a nice display title for free.
 *
 * SOLID notes:
 *  - **R.X.1** narrow — takes a [DocumentBytesSource] only; no
 *    `Context`, no `LibraryRepository`.
 *  - **R.X.6** does not import `ui/` or `data/library/` (except the
 *    closed-enum `DocumentFormat`, per O.C.1).
 *  - **R.X.9** open/closed dispatch — one new line in
 *    [com.eight87.pageboy.AppGraph.formatRegistry] registers it; the
 *    reader chrome stays untouched.
 *  - **L** Liskov: every parse failure surfaces as a
 *    [com.eight87.pageboy.format.mobi.internal.MobiParseException]
 *    on [open], caught by `DefaultReaderStateProjector` and projected
 *    to `ReaderState.Failed`. Never a `NotImplementedError`.
 */
internal class MobiRenderer(
  private val parser: MobiParser = MobiParser(),
) : DocumentRenderer {

  override val format: DocumentFormat = DocumentFormat.Mobi

  override suspend fun open(source: DocumentBytesSource): DocumentHandle = withContext(Dispatchers.IO) {
    val bytes = source.openStream().use { it.readBytes() }
    val content = parser.parse(bytes)
    val title = content.metadata.title?.takeIf { it.isNotBlank() }
      ?: source.displayName()?.substringBeforeLast('.')?.takeIf { it.isNotBlank() }
      ?: DEFAULT_TITLE
    MobiHandle(content = content, title = title)
  }

  @Composable
  override fun Body(handle: DocumentHandle, context: RendererContext, modifier: Modifier) {
    val mobi = handle as? MobiHandle ?: return
    MobiBody(handle = mobi, context = context, modifier = modifier)
  }

  override suspend fun extractTitle(source: DocumentBytesSource): String? = withContext(Dispatchers.IO) {
    runCatching {
      val bytes = source.openStream().use { it.readBytes() }
      MobiTitleExtractor.extractFrom(bytes)
    }.getOrNull()
  }

  private companion object {
    const val DEFAULT_TITLE = "MOBI book"
  }
}
