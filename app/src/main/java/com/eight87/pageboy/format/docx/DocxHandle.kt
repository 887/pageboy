package com.eight87.pageboy.format.docx

import com.eight87.pageboy.data.library.DocumentFormat
import com.eight87.pageboy.format.api.DocumentHandle
import com.eight87.pageboy.format.docx.internal.RichTextBlock
import org.apache.poi.xwpf.usermodel.XWPFDocument

/**
 * Phase I — opened-document state for DOCX.
 *
 * Carries the parsed [RichTextBlock] list (what Compose reads) and the
 * still-open POI [XWPFDocument] handle (closed by [close] when the
 * reader leaves the screen, releasing the in-memory schema model).
 *
 * `pageCount` is `null` — DOCX is reflowable; Word's page concept is
 * derived at render time and pageboy doesn't preserve it.
 */
internal data class DocxHandle(
  val document: XWPFDocument,
  val blocks: List<RichTextBlock>,
  override val title: String,
) : DocumentHandle {

  override val format: DocumentFormat = DocumentFormat.Docx
  override val pageCount: Int? = null

  override fun close() {
    runCatching { document.close() }
  }
}
