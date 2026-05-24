package com.eight87.pageboy.domain.render

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Phase F.2 — persisted scroll position for an opened document.
 *
 * Sealed (R.X.2) so each per-format renderer carries the scroll
 * primitive that actually fits its shape rather than forcing every
 * format through a single `(pageIndex, offsetFraction)` bit-packed
 * encoding. Closes Phase D audit observation O.D.2 — the Phase D plan
 * deferred this until PDF + EPUB joined the renderer roster, which
 * Phase F does (PDF) and Phase M will (EPUB CFI string).
 *
 * Variants ship per-renderer:
 *  - [LazyColumn] — reflowable formats (Markdown, TXT). The chrome's
 *    `LazyColumn` records its first-visible item index + pixel offset
 *    so re-open lands the user back on the same paragraph.
 *  - [PdfPage] — paginated formats (PDF). The renderer records the
 *    page index + a 0..1 ratio within that page so resuming a 500-page
 *    document lands the user back on the same paragraph regardless of
 *    device rotation or font-size change.
 *  - **EPUB CFI** lands in Phase M as a separate variant carrying the
 *    canonical EPUB-3 CFI string (XPath-shaped). Not declared here so
 *    Phase F doesn't introduce a Liskov-violating empty variant
 *    (R.X.5).
 *
 * Persistence: the chrome's `DefaultScrollPersistence` encodes the
 * variant as JSON via [encode] and writes it to a TEXT column on
 * `DocumentEntity.scrollPositionJson` (Phase F Room migration v1→v2).
 * Older rows still carry the legacy `lastReadPositionMs` long; the
 * decoder falls back to the legacy bit-packed encoding when the JSON
 * column is null (zero-loss migration; no data destroyed).
 *
 * Lives in `domain/render/` (not `ui/reader/control/`) so both the
 * chrome (`ui/reader/`) and the per-format renderers (`format/...`)
 * can import it without crossing the `format/` → `ui/` import barrier
 * R.X.6 forbids.
 */
@Serializable
sealed class ScrollPosition {

  /**
   * Position inside a `LazyColumn`-shaped scroll surface. Used by
   * reflowable renderers (Markdown / TXT) where there's no concept of
   * a "page number" — the document is one long list of blocks.
   *
   * [itemIndex] is the `firstVisibleItemIndex`; [offset] is the
   * `firstVisibleItemScrollOffset` (pixels). Renderers that don't
   * preserve item widths across rotation accept some scroll drift on
   * device rotation — the saved offset is in the source-time pixel
   * space.
   */
  @Serializable
  data class LazyColumn(val itemIndex: Int, val offset: Int) : ScrollPosition()

  /**
   * Position inside a paginated PDF. [page] is the 0-based page
   * number; [ratio] is 0.0..1.0 within that page (0 = top of page, 1
   * = bottom). Renderer maps the ratio onto the page bitmap on
   * restore.
   */
  @Serializable
  data class PdfPage(val page: Int, val ratio: Float) : ScrollPosition()

  companion object {
    private val json = Json {
      classDiscriminator = "kind"
      ignoreUnknownKeys = true
    }

    /**
     * Encode the sealed variant to a JSON string for persistence in
     * the `DocumentEntity.scrollPositionJson` TEXT column. Round-trips
     * via [decode]. Returns null only for null input (the chrome
     * passes the value through; no positions persisted before the
     * first scroll event).
     */
    fun encode(position: ScrollPosition?): String? {
      position ?: return null
      return json.encodeToString(serializer(), position)
    }

    /**
     * Decode a JSON string back into a [ScrollPosition], returning
     * null when the payload is empty / malformed / from a future
     * variant the current build doesn't know about (R.X.5 — we'd
     * rather lose the position than crash the reader).
     */
    fun decode(encoded: String?): ScrollPosition? {
      if (encoded.isNullOrBlank()) return null
      return runCatching { json.decodeFromString(serializer(), encoded) }
        .getOrNull()
    }
  }
}
