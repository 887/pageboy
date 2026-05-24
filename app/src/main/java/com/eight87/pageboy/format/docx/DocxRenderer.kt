package com.eight87.pageboy.format.docx

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.eight87.pageboy.data.library.DocumentFormat
import com.eight87.pageboy.domain.render.RendererContext
import com.eight87.pageboy.format.api.DocumentBytesSource
import com.eight87.pageboy.format.api.DocumentHandle
import com.eight87.pageboy.format.api.DocumentRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.poi.xwpf.usermodel.XWPFDocument

/**
 * Phase I — DOCX [DocumentRenderer] impl.
 *
 * Thin façade: [open] hands the SAF byte stream to [DocxParser],
 * gathers the result into a [DocxHandle]; [Body] dispatches into
 * `DocxBody.kt` (the Compose Composable lives there, in its own file
 * so this renderer stays under the R.X.4 200-LOC marker).
 *
 * [extractTitle] reuses the parser path because POI's `coreProperties`
 * read needs the document open — we open + close on the probe path so
 * the scanner doesn't hold a long-lived handle.
 *
 * SOLID notes:
 *  - **R.X.1** narrow — takes a [DocumentBytesSource] only; no
 *    `Context`, no `LibraryRepository`.
 *  - **R.X.6** does not import `ui/` or `data/library/` (except the
 *    closed-enum `DocumentFormat`, per the documented Phase C audit
 *    observation O.C.1).
 *  - **R.X.9** open/closed dispatch — one new line in
 *    [com.eight87.pageboy.AppGraph.formatRegistry] registers it; the
 *    reader chrome stays untouched.
 *
 * Liskov: every parse failure surfaces as an exception on [open]
 * (caught by `DefaultReaderStateProjector` and projected to
 * `ReaderState.Failed`), never a `NotImplementedError`. Encrypted DOCX
 * raises POI's `EncryptedDocumentException`; same propagation path.
 */
internal class DocxRenderer(
  private val parser: DocxParser = DocxParser(),
) : DocumentRenderer {

  override val format: DocumentFormat = DocumentFormat.Docx

  override suspend fun open(source: DocumentBytesSource): DocumentHandle = withContext(Dispatchers.IO) {
    val result = source.openStream().use { stream -> parser.parse(stream) }
    val title = result.title
      ?: source.displayName()
      ?: DEFAULT_TITLE
    DocxHandle(
      document = result.document,
      blocks = result.blocks,
      title = title,
    )
  }

  @Composable
  override fun Body(handle: DocumentHandle, context: RendererContext, modifier: Modifier) {
    val docx = handle as? DocxHandle ?: return
    DocxBody(handle = docx, context = context, modifier = modifier)
  }

  /**
   * Cheap title probe for the library scanner. Opens the document just
   * long enough to read `coreProperties.title` then closes immediately
   * — the parsed block list is discarded.
   */
  override suspend fun extractTitle(source: DocumentBytesSource): String? = withContext(Dispatchers.IO) {
    runCatching {
      val title: String? = source.openStream().use { stream ->
        val doc = XWPFDocument(stream)
        try {
          doc.properties?.coreProperties?.title?.takeIf { it.isNotBlank() }
        } finally {
          runCatching { doc.close() }
        }
      }
      title
    }.getOrNull()
  }

  private companion object {
    const val DEFAULT_TITLE = "Word document"
  }
}
