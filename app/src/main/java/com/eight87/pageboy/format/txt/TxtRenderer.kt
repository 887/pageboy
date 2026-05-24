package com.eight87.pageboy.format.txt

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
 * Phase E.4 — second real [DocumentRenderer] impl (after the Phase D
 * Markdown one). The trivial-degenerate case: no formatting, no inline
 * runs, just lines of monospace text. The interesting work all lives
 * in:
 *  - [TxtEncodingDetector] — BOM sniff + UTF-8 trial + Windows-1252
 *    fallback (no third-party charset detector — `format-txt.md`
 *    licensed-out juniversalchardet on MPL-1.1 and rejected ICU4J on
 *    APK budget).
 *  - [TxtLineSource] — line-windowed accessor that wraps very-long
 *    single lines to bound LazyColumn item width (format-txt.md G3).
 *  - [TxtBody] — `LazyColumn` of monospace `Text` items keyed by line
 *    index; only the visible window composes.
 *
 * SOLID notes:
 *  - **R.X.1** narrow — takes a [DocumentBytesSource], emits a
 *    [DocumentHandle]; no `Context`, no `LibraryRepository`.
 *  - **R.X.5** no `NotImplementedError` — empty / 0-byte / 1-byte
 *    files all decode to a single-item `LazyColumn`; binary files
 *    that fall through to cp1252 render as mojibake (the user picks
 *    the wrong document; the renderer doesn't crash).
 *  - **R.X.6** does not import `data/library/` (except the closed-enum
 *    `DocumentFormat`, per the documented Phase C audit observation
 *    O.C.1 exception).
 *  - **R.X.9** registry-dispatched; adding TXT is one line in
 *    [com.eight87.pageboy.AppGraph.formatRegistry], no `when (format)`
 *    in the reader.
 */
class TxtRenderer : DocumentRenderer {

  override val format: DocumentFormat = DocumentFormat.Txt

  override suspend fun open(source: DocumentBytesSource): DocumentHandle = withContext(Dispatchers.IO) {
    val bytes = source.openStream().use { it.readBytes() }
    val head = if (bytes.size > TxtEncodingDetector.HEAD_SAMPLE_BYTES) {
      bytes.copyOf(TxtEncodingDetector.HEAD_SAMPLE_BYTES)
    } else {
      bytes
    }
    val encoding = TxtEncodingDetector.detect(head)
    val lineSource = InMemoryTxtLineSource(
      bytes = bytes,
      charset = encoding.charset,
      bomLength = encoding.bomLength,
    )
    val title = source.displayName() ?: DEFAULT_TITLE
    TxtHandle(
      lineSource = lineSource,
      encodingLabel = encoding.charset.name(),
      title = title,
    )
  }

  @Composable
  override fun Body(handle: DocumentHandle, context: RendererContext, modifier: Modifier) {
    val txt = handle as? TxtHandle ?: return
    TxtBody(handle = txt, context = context, modifier = modifier)
  }

  /**
   * TXT has no embedded title concept — no front-matter, no first-H1,
   * no metadata block. The library scanner's filename-derived title is
   * the best we can offer, so we return `null` (the contract's "fall
   * back to filename" signal).
   */
  override suspend fun extractTitle(source: DocumentBytesSource): String? = null

  private companion object {
    const val DEFAULT_TITLE = "Plain text document"
  }
}
