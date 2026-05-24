package com.eight87.pageboy.format.epub

import com.eight87.pageboy.data.library.DocumentFormat
import com.eight87.pageboy.format.api.DocumentHandle
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Publication

/**
 * Phase M.3 — opened-document state for the EPUB renderer.
 *
 * Carries the live Readium [Publication] (kept open across rendering so
 * the navigator can stream spine items lazily on each page flip), the
 * resolved display title, and a normalised flat ToC link list that the
 * reader chrome's overflow menu can present without itself depending on
 * Readium types.
 *
 * [close] releases the Readium [Publication] — the underlying
 * `Container` (the SAF-resolved ZIP / Resource bundle) holds an OS-level
 * file descriptor against the EPUB archive, and the
 * `EpubNavigatorFragment` consumes the publication's container directly.
 * The chrome's `DefaultReaderStateProjector` calls this on screen
 * disposal so the next document open starts from a clean slate.
 *
 * SOLID notes:
 *  - **R.X.1** narrow — only the chrome-relevant surface (title +
 *    pageCount=null because EPUB reflows) is exposed via the
 *    [DocumentHandle] interface. The renderer-internal [publication]
 *    field is accessed only by [EpubBody] inside the same package.
 *  - **L (Liskov)** EPUB has no concept of a fixed page count under
 *    reflow; `pageCount = null` honours the contract intent
 *    (`pageCount` is "the count when the format paginates, otherwise
 *    null" per the interface doc-comment).
 */
data class EpubHandle(
  internal val publication: Publication,
  override val title: String,
  internal val tocItems: List<Link>,
) : DocumentHandle {

  override val format: DocumentFormat = DocumentFormat.Epub

  /**
   * EPUB reflows; the page count concept doesn't apply natively. The
   * top bar suppresses the page indicator when this is null.
   */
  override val pageCount: Int? = null

  /**
   * Whether the publication exposes a non-empty table of contents.
   * Drives the reader chrome's overflow "Table of contents…" entry —
   * suppressed (or disabled) when this is false. Other formats default
   * to `false` per [DocumentHandle.tocAvailable]; EPUB returns whatever
   * the publication declared.
   */
  override val tocAvailable: Boolean
    get() = tocItems.isNotEmpty()

  override fun close() {
    // Publication owns the Container which owns the OS-level file
    // descriptor against the EPUB ZIP. Closing here releases both.
    runCatching { publication.close() }
  }
}
