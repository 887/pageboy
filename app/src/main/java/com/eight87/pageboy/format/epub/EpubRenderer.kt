package com.eight87.pageboy.format.epub

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
 * Phase M.3 / R.X.9 — EPUB [DocumentRenderer] backed by the Readium
 * Kotlin Toolkit 3.2.0 (BSD-3-Clause).
 *
 * Architecture (per `docs/plans/format-epub.md`):
 *  - **Parsing**: [EpubParser] wraps `PublicationOpener` +
 *    `AssetRetriever` for the open lifecycle. The renderer never
 *    touches Readium's parser surface directly; the parser is a
 *    constructor dep so tests can stub it.
 *  - **Rendering**: hands the publication off to [EpubBody], which
 *    hosts Readium's WebView-based `EpubNavigatorFragment` via Compose
 *    `AndroidFragment` interop (same pattern as the PDF renderer's
 *    `PdfBody`).
 *  - **Persistence**: `RendererContext.scrollSink` round-trips
 *    [com.eight87.pageboy.domain.render.ScrollPosition.EpubCfi] —
 *    Readium's `Locator` JSON-serialised opaque-from-the-chrome's-view
 *    so the codec stays single-concern.
 *
 * SOLID notes:
 *  - **R.X.1** narrow — takes only [EpubParser]; no `Context`, no
 *    `LibraryRepository`.
 *  - **R.X.5** no `NotImplementedError`. Encrypted publications / LCP-
 *    protected EPUBs / malformed ZIPs fall out of [open] as thrown
 *    `IOException` (the projector lands `ReaderState.Failed`). LCP DRM
 *    deliberately out per the plan (proprietary blob, not on the
 *    licence allowlist).
 *  - **R.X.6** `format/epub/` imports `format/api/` + `domain/render/`
 *    + Readium (third-party); the only `data/library/` import is
 *    `DocumentFormat` (Phase C audit observation O.C.1 — closed enum
 *    used as renderer-identity tag).
 *  - **R.X.9** Phase M adds one entry to `AppGraph.formatRegistry`. No
 *    `when (format)` switch in the reader chrome grew.
 */
class EpubRenderer(
  private val parser: EpubParser,
) : DocumentRenderer {

  override val format: DocumentFormat = DocumentFormat.Epub

  override suspend fun open(source: DocumentBytesSource): DocumentHandle = withContext(Dispatchers.IO) {
    val publication = parser.parse(source)
    val resolvedTitle = EpubTitleExtractor.titleFor(publication)
      ?: source.displayName()
      ?: DEFAULT_TITLE
    EpubHandle(
      publication = publication,
      title = resolvedTitle,
      // Readium normalises EPUB 2 NCX + EPUB 3 nav doc to the same
      // flat list (it preserves the tree, but the chrome consumes the
      // flat surface for the overflow menu). Each entry is a Link
      // whose href points at the spine resource + optional fragment.
      tocItems = publication.tableOfContents,
    )
  }

  /**
   * Scanner-side title probe. Reuses the parser pipeline — Readium's
   * `open()` is the cheap-to-fail-fast path that already runs through
   * the format sniffer + container parser; if the bytes aren't an
   * EPUB the cost is the sniff + a couple-KB read. The streamer does
   * not eagerly pull spine items.
   *
   * Returns null on any failure so the scanner falls back to the
   * filename-derived title.
   */
  override suspend fun extractTitle(source: DocumentBytesSource): String? =
    withContext(Dispatchers.IO) {
      runCatching {
        val publication = parser.parse(source)
        try {
          EpubTitleExtractor.titleFor(publication)
        } finally {
          runCatching { publication.close() }
        }
      }.getOrNull()
    }

  @Composable
  override fun Body(handle: DocumentHandle, context: RendererContext, modifier: Modifier) {
    val epub = handle as? EpubHandle ?: return
    EpubBody(handle = epub, context = context, modifier = modifier)
  }

  private companion object {
    const val DEFAULT_TITLE = "EPUB document"
  }
}
