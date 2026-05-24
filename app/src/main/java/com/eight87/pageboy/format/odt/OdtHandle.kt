package com.eight87.pageboy.format.odt

import com.eight87.pageboy.data.library.DocumentFormat
import com.eight87.pageboy.format.api.DocumentHandle

/**
 * Phase K — opened-document state for the ODT renderer. Carries the
 * parsed [OdfTextBlock] list + the derived title.
 *
 * `pageCount` is `null` — ODT is a reflowable format; the v1 viewer
 * does not paginate (page styles ignored per `format-odt.md`).
 *
 * `close()` is a no-op — the parser owns no native handles; the inflated
 * bytes are dropped once parsing completes.
 */
data class OdtHandle(
  val blocks: List<OdfTextBlock>,
  override val title: String,
) : DocumentHandle {

  override val format: DocumentFormat = DocumentFormat.Odt
  override val pageCount: Int? = null
}
