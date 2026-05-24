package com.eight87.pageboy.format.pdf

import android.net.Uri
import com.eight87.pageboy.data.library.DocumentFormat
import com.eight87.pageboy.format.api.DocumentHandle

/**
 * Phase F.3 — opened-document state for the PDF renderer.
 *
 * Thin envelope: the PDF body delegates rendering to androidx.pdf's
 * `PdfViewerFragment` (which holds its own
 * [androidx.pdf.PdfDocument] handle via a Hilt-free
 * [androidx.lifecycle.ViewModel] backed by [androidx.pdf.SandboxedPdfLoader]).
 * The chrome only needs the URI to hand the fragment + the page count +
 * the resolved title.
 *
 * [close] is a no-op — the fragment's `onDestroyView` releases the
 * native PdfRenderer handle in the sandbox process; pageboy's chrome
 * doesn't own the handle, so there's nothing for the chrome's reader
 * teardown to free here.
 */
data class PdfHandle(
  val uri: Uri,
  override val pageCount: Int?,
  override val title: String,
) : DocumentHandle {

  override val format: DocumentFormat = DocumentFormat.Pdf

  override fun close() {
    // No-op — androidx.pdf's PdfDocumentViewModel owns the sandbox
    // PdfDocument and releases it from its `onCleared()` when the
    // fragment is destroyed. The chrome never sees the native handle.
  }
}
