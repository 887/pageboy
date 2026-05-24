package com.eight87.pageboy.format.txt

import com.eight87.pageboy.data.library.DocumentFormat
import com.eight87.pageboy.format.api.DocumentHandle

/**
 * Phase E.4 — opened-document state for the TXT renderer.
 *
 * Holds the [TxtLineSource] (windowed line accessor) + detected
 * encoding (surfaced as a label later — Phase E ships detection but the
 * override chip lands when the user-overrideable encoding UI does) +
 * the derived title.
 *
 * `pageCount` is `null` — plain text is reflowable; the line count is
 * not equivalent to a page count.
 *
 * `close()` releases the line source (no-op for the in-memory impl;
 * future disk-backed impls free file descriptors here).
 */
data class TxtHandle(
  val lineSource: TxtLineSource,
  val encodingLabel: String,
  override val title: String,
) : DocumentHandle {

  override val format: DocumentFormat = DocumentFormat.Txt
  override val pageCount: Int? = null

  override fun close() {
    lineSource.close()
  }
}
