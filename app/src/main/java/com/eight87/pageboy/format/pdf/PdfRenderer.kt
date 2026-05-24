package com.eight87.pageboy.format.pdf

import android.content.ContentResolver
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.eight87.pageboy.data.library.DocumentFormat
import com.eight87.pageboy.domain.render.RendererContext
import com.eight87.pageboy.format.api.DocumentBytesSource
import com.eight87.pageboy.format.api.DocumentHandle
import com.eight87.pageboy.format.api.DocumentRenderer
import com.eight87.pageboy.format.api.SafDocumentBytesSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Phase F.3 — third real [DocumentRenderer] impl (after Markdown +
 * TXT). View-only PDF rendering delegating to androidx.pdf's
 * `PdfViewerFragment` (Apache-2.0; minSdk 28 backport; no native code
 * in the APK — Pdfium runs inside the system PdfRenderer sandbox).
 *
 * Per `format-pdf.md`:
 *  - Renderer chosen: `androidx.pdf:pdf-viewer-fragment` 1.0.0-alpha18.
 *  - Annotation editing comes with the fragment (Phase G consumes it;
 *    Phase F uses only the view side).
 *  - Cryptographic signing is Phase H.
 *
 * SOLID notes:
 *  - **R.X.1** narrow — takes a [DocumentBytesSource], emits a
 *    [DocumentHandle]; no `Context`, no `LibraryRepository`. The
 *    SAF URI ride along via [SafDocumentBytesSource] type-narrowing in
 *    [open] (the only place that crosses).
 *  - **R.X.5** no `NotImplementedError`. Encrypted PDFs / malformed
 *    XRef / oversize files surface as failed-open exceptions which the
 *    chrome's projector projects into [com.eight87.pageboy.ui.reader.control.ReaderState.Failed]
 *    (Liskov-clean: every error path goes through the contract).
 *  - **R.X.6** `format/pdf/` imports `format/api/` + `domain/render/`
 *    + the narrow exception of `data/library/DocumentFormat` (Phase C
 *    audit observation O.C.1).
 *  - **R.X.9** third registry entry. Adding PDF was one line in
 *    `AppGraph.formatRegistry`, no `when (format)` in the reader.
 */
class PdfRenderer(
  private val contentResolver: ContentResolver,
) : DocumentRenderer {

  override val format: DocumentFormat = DocumentFormat.Pdf

  /**
   * Resolve the SAF URI + sniff metadata (page count, title) via the
   * system [android.graphics.pdf.PdfRenderer]. The androidx.pdf
   * `PdfDocument` lives inside the fragment; we deliberately don't
   * open a parallel handle here because the sandbox process is heavy.
   * The system PdfRenderer is cheaper and gives us exactly the two
   * metadata points the chrome's top bar needs.
   *
   * The Info-dict `/Title` doesn't surface from the framework
   * [android.graphics.pdf.PdfRenderer] (only page count + form-type +
   * linearization survive its public surface). We sniff `/Title`
   * directly from the bytes via [PdfTitleExtractor]; falls back to
   * `source.displayName()` (filename) when no embedded title is
   * present or the file declines parsing.
   */
  override suspend fun open(source: DocumentBytesSource): DocumentHandle = withContext(Dispatchers.IO) {
    val uri = (source as? SafDocumentBytesSource)?.documentUri
      ?: throw IOException("PdfRenderer requires a SAF-backed DocumentBytesSource")

    val pageCount = readPageCount(uri)
    val sniffedTitle = runCatching {
      source.openStream().use { stream -> PdfTitleExtractor.extractTitle(stream) }
    }.getOrNull()
    val title = sniffedTitle
      ?: source.displayName()
      ?: DEFAULT_TITLE

    PdfHandle(uri = uri, pageCount = pageCount, title = title)
  }

  @Composable
  override fun Body(handle: DocumentHandle, context: RendererContext, modifier: Modifier) {
    val pdf = handle as? PdfHandle ?: return
    PdfBody(handle = pdf, context = context, modifier = modifier)
  }

  /**
   * Scanner-side title probe. Cheap — opens the bytes stream and runs
   * the byte-level [PdfTitleExtractor]. Returns null for encrypted
   * PDFs / PDFs with no Info-dict title / malformed PDFs (the scanner
   * falls back to the filename).
   */
  override suspend fun extractTitle(source: DocumentBytesSource): String? =
    withContext(Dispatchers.IO) {
      runCatching {
        source.openStream().use { stream -> PdfTitleExtractor.extractTitle(stream) }
      }.getOrNull()
    }

  /**
   * Resolve the page count via the system PdfRenderer. Returns null
   * for encrypted PDFs (the framework's PdfRenderer throws
   * `SecurityException` on encrypted docs without a password) and for
   * any IO failure — the chrome's top bar handles a null page count
   * by simply not showing the page indicator.
   */
  private fun readPageCount(uri: Uri): Int? = runCatching {
    contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
      android.graphics.pdf.PdfRenderer(pfd).use { renderer -> renderer.pageCount }
    }
  }.getOrNull()

  private companion object {
    const val DEFAULT_TITLE = "PDF document"
  }
}

/**
 * Tiny [ParcelFileDescriptor]-shaped helper so the [android.graphics.pdf.PdfRenderer]
 * close lands deterministically — Kotlin's `use` on the framework
 * PdfRenderer works because it implements [AutoCloseable] since API 21.
 */
private inline fun <R> android.graphics.pdf.PdfRenderer.use(block: (android.graphics.pdf.PdfRenderer) -> R): R {
  var thrown: Throwable? = null
  try {
    return block(this)
  } catch (t: Throwable) {
    thrown = t
    throw t
  } finally {
    if (thrown == null) close()
    else runCatching { close() }
  }
}
