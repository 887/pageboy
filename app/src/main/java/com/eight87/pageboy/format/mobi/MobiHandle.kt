package com.eight87.pageboy.format.mobi

import com.eight87.pageboy.data.library.DocumentFormat
import com.eight87.pageboy.format.api.DocumentHandle

/**
 * Phase Q — opened-MOBI handle. The renderer's [MobiRenderer.Body]
 * casts a [DocumentHandle] back to this concrete subtype to read the
 * already-parsed HTML + image map.
 *
 * Holds:
 *  - [content]: the parsed MOBI body (HTML + metadata + images +
 *    variant).
 *  - [title]: display title — EXTH title if present, otherwise
 *    derived from filename (resolved up at `MobiRenderer.open`).
 *
 * MOBI is reflowable, so `pageCount = null` (the WebView's intrinsic
 * scroll is what the reader chrome surfaces).
 */
internal data class MobiHandle(
  val content: MobiBookContent,
  override val title: String,
) : DocumentHandle {
  override val format: DocumentFormat = DocumentFormat.Mobi
  override val pageCount: Int? = null
}
