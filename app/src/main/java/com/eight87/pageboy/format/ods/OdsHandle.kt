package com.eight87.pageboy.format.ods

import com.eight87.pageboy.data.library.DocumentFormat
import com.eight87.pageboy.format.api.DocumentHandle

/**
 * Phase L — opened-document state for the ODS renderer. Carries the
 * sheet list + named ranges + derived title. The chrome doesn't
 * paginate ODS (it's a grid, not a paged document), so [pageCount] is
 * `null`.
 *
 * `close()` is a no-op — the parser owns no native handles; the
 * sparse cell maps are JVM-managed.
 */
data class OdsHandle(
  val sheets: List<OdfSheet>,
  val namedRanges: List<NamedRange>,
  override val title: String,
) : DocumentHandle {

  override val format: DocumentFormat = DocumentFormat.Ods
  override val pageCount: Int? = null
}
