package com.eight87.pageboy.format.api

import com.eight87.pageboy.data.library.DocumentFormat

/**
 * Phase C.1 — opened-document state. Returned by [DocumentRenderer.open],
 * threaded into [DocumentRenderer.Body], held by the reader chrome until
 * the user navigates away.
 *
 * Each format renderer ships its own subtype carrying whatever
 * format-specific state it needs (parsed AST, native Pdfium handle, ZIP
 * spine reader, etc.). The reader chrome only reads the common surface
 * defined here; the renderer's `Body` casts back to its own subtype.
 *
 * [AutoCloseable] so the chrome can release native resources / open file
 * descriptors deterministically when the user leaves the screen — the
 * Pdfium native handle, the EPUB ZIP file, the Markdown AST cache. The
 * Phase C [PlaceholderRenderer] handle has nothing to free; closing it
 * is a no-op.
 */
interface DocumentHandle : AutoCloseable {

  /** Format this handle was opened for. Matches its [DocumentRenderer.format]. */
  val format: DocumentFormat

  /** Display title shown in the reader's top bar. */
  val title: String

  /**
   * Total page count when the format paginates (PDF, future paged
   * Markdown when continuousScrolling is off, paged DOCX). `null` for
   * reflowable formats (Markdown / Txt / EPUB) where the page concept
   * doesn't apply natively. The reader chrome surfaces this only when
   * non-null.
   */
  val pageCount: Int?

  /**
   * Phase M.7 — whether the format exposes a non-empty table of
   * contents the reader chrome can surface as an overflow entry.
   * Defaults to `false` so formats without a ToC concept (Markdown,
   * TXT, plain DOCX paragraphs, etc.) silently opt out; the EPUB
   * handle returns `true` when the publication declared one.
   *
   * Adding a per-format ToC support = override this + expose the
   * navigation primitives via the renderer's own surface. The
   * chrome's overflow menu reads only this single capability flag.
   */
  val tocAvailable: Boolean
    get() = false

  /** Default impl — most handles have nothing to release. */
  override fun close() {
    // no-op by default
  }
}
