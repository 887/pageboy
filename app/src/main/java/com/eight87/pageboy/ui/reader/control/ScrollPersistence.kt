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
 */
interface ScrollPersistence {

  /**
   * Last-known scroll position for a document, or null if the document
   * hasn't been scrolled yet. Reader chrome reads this once on open to
   * restore the user's place.
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
 * Default [ScrollPersistence] backed by [DocumentSource.setReadProgress].
 * Debounces to one write per [debounceMs] window per document.
 *
 * The debounce maps a `pageIndex` + `offsetFraction` pair onto the
 * single-value `lastReadPositionMs` + `readFraction` columns the
 * Phase B repository already exposes:
 *  - `lastReadPositionMs` = `(pageIndex.toLong() shl 20) or (offsetFraction * (1L shl 20)).toLong()`
 *    — fits a 12-bit page index + a 20-bit fractional offset into one
 *    long; renderers without pagination set `pageIndex = 0` and the
 *    encoding collapses to just the offset.
 *  - `readFraction` = float position the library card's progress bar
 *    surfaces; renderers compute it from their own context (page-count
 *    fraction for PDF, char-offset fraction for text).
 */
class DefaultScrollPersistence(
  private val applicationScope: CoroutineScope,
  private val documentSource: DocumentSource,
  private val debounceMs: Long = DEFAULT_DEBOUNCE_MS,
) : ScrollPersistence {

  private val pending: MutableMap<String, ScrollPosition> = HashMap()
  private val jobs: MutableMap<String, Job> = HashMap()
  private val lock = Any()

  override suspend fun lastPosition(documentId: String): ScrollPosition? {
    val entity = documentSource.findById(documentId) ?: return null
    val encoded = entity.lastReadPositionMs
    if (encoded == 0L && entity.readFraction == 0f) return null
    return ScrollPosition(
      pageIndex = (encoded shr OFFSET_BITS).toInt(),
      offsetFraction = entity.readFraction.coerceIn(0f, 1f),
    )
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
        val pageOffsetEncoded =
          (toWrite.pageIndex.toLong() shl OFFSET_BITS) or
            (toWrite.offsetFraction.coerceIn(0f, 1f) * (1L shl OFFSET_BITS)).toLong()
        documentSource.setReadProgress(
          id = documentId,
          positionMs = pageOffsetEncoded,
          fraction = toWrite.offsetFraction.coerceIn(0f, 1f),
        )
      }
    }
  }

  private companion object {
    const val DEFAULT_DEBOUNCE_MS = 750L
    const val OFFSET_BITS = 20
  }
}
