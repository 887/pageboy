package com.eight87.pageboy.ui.reader.control

import com.eight87.pageboy.data.library.DocumentSource
import com.eight87.pageboy.domain.render.ScrollPosition
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Phase C.4 / R.C — debounced read-progress persistence. The reader
 * chrome calls [recordPosition] on every scroll-stop tick; the impl
 * collapses bursts into a single write so SAF reads + Room writes don't
 * thrash on rapid scroll.
 *
 * Lives behind a narrow interface (R.X.1) because the chrome + the
 * per-format renderers both need to write, but nothing on the read side
 * cares about debounce semantics — the read-side surface is just a
 * single suspend lookup.
 *
 * Phase F.2 — surface accepts the sealed [ScrollPosition] (was the
 * plain data class). Closes Phase D audit O.D.2. Persistence-side
 * encoding goes through `ScrollPosition.encode/decode` to JSON; the
 * Room column is a single TEXT.
 */
interface ScrollPersistence {

  /**
   * Last-known scroll position for a document, or null if the document
   * hasn't been scrolled yet. Reader chrome reads this once on open to
   * restore the user's place. Returns whichever sealed variant was
   * previously written; the renderer's `Body()` pattern-matches on the
   * variant it knows (Markdown / TXT match `LazyColumn`, PDF matches
   * `PdfPage`, etc.) and ignores variants it doesn't.
   */
  suspend fun lastPosition(documentId: String): ScrollPosition?

  /**
   * Record the latest scroll position. Debounced internally — multiple
   * calls within the debounce window collapse into one write. Safe to
   * call as often as the scroll tick fires.
   */
  fun recordPosition(documentId: String, position: ScrollPosition)
}

/**
 * Default [ScrollPersistence] backed by [DocumentSource.setScrollPosition].
 * Debounces to one write per [debounceMs] window per document.
 *
 * Phase F.2 — refactored to encode the sealed [ScrollPosition] via
 * `ScrollPosition.encode`. The chrome's derived fraction-complete still
 * lives on `DocumentEntity.readFraction` (per-renderer semantics — PDF
 * uses `page / pageCount`, reflowable formats use 0).
 *
 * Read path: prefer the new `scroll_position_json` column when present;
 * fall back to the legacy bit-packed `lastReadPositionMs` long for v1
 * rows that haven't been re-written since the migration (decode as
 * `ScrollPosition.LazyColumn` because that's what Markdown / TXT wrote
 * pre-Phase F). This keeps user state intact across the v1→v2 upgrade.
 */
class DefaultScrollPersistence(
  private val applicationScope: CoroutineScope,
  private val documentSource: DocumentSource,
  private val debounceMs: Long = DEFAULT_DEBOUNCE_MS,
  private val fractionFor: (ScrollPosition) -> Float = ::defaultFractionFor,
) : ScrollPersistence {

  private val pending: MutableMap<String, ScrollPosition> = HashMap()
  private val jobs: MutableMap<String, Job> = HashMap()
  private val lock = Any()

  override suspend fun lastPosition(documentId: String): ScrollPosition? {
    val entity = documentSource.findById(documentId) ?: return null
    // Prefer the post-Phase F JSON encoding.
    ScrollPosition.decode(entity.scrollPositionJson)?.let { return it }
    // Legacy v1 fallback: bit-packed lastReadPositionMs. Pre-Phase F
    // only reflowable renderers (Markdown / TXT) wrote here, so the
    // decode lands as `LazyColumn`. PDF didn't ship in v1.
    val encoded = entity.lastReadPositionMs
    if (encoded == 0L && entity.readFraction == 0f) return null
    val itemIndex = (encoded shr OFFSET_BITS).toInt()
    val offsetRaw = (encoded and OFFSET_MASK).toInt()
    return ScrollPosition.LazyColumn(itemIndex = itemIndex, offset = offsetRaw)
  }

  override fun recordPosition(documentId: String, position: ScrollPosition) {
    synchronized(lock) {
      pending[documentId] = position
      // Re-arm a single debounced write per document. Cancelling the
      // prior job collapses the write burst into one tail.
      jobs[documentId]?.cancel()
      jobs[documentId] = applicationScope.launch {
        delay(debounceMs)
        val toWrite = synchronized(lock) { pending.remove(documentId) } ?: return@launch
        documentSource.setScrollPosition(
          id = documentId,
          positionJson = ScrollPosition.encode(toWrite),
          fraction = fractionFor(toWrite).coerceIn(0f, 1f),
        )
      }
    }
  }

  internal companion object {
    const val DEFAULT_DEBOUNCE_MS = 750L
    const val OFFSET_BITS = 20
    const val OFFSET_MASK = (1L shl OFFSET_BITS) - 1L

    /**
     * Sensible default for the library card's progress bar. PDF reports
     * a real fraction `page / pageCount`; reflowable formats report 0
     * (the library card hides the progress bar when 0). Per-renderer
     * overrides slot in via the [fractionFor] ctor param.
     */
    fun defaultFractionFor(position: ScrollPosition): Float = when (position) {
      is ScrollPosition.LazyColumn -> 0f
      is ScrollPosition.PdfPage -> position.ratio.coerceIn(0f, 1f)
    }
  }
}
