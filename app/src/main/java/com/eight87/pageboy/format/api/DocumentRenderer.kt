package com.eight87.pageboy.format.api

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.eight87.pageboy.data.library.DocumentFormat
import com.eight87.pageboy.domain.render.RendererContext

/**
 * Phase C.1 / R.X.9 — the central open/closed interface every per-format
 * renderer (Phase D–M) implements.
 *
 * **The shape stays narrow on purpose.** Three methods:
 *
 *   1. [open] — parse the bytes into a format-specific [DocumentHandle]
 *      ready for rendering. Suspends; expected to be I/O bound.
 *   2. [Body] — Composable that renders the opened document. The reader
 *      chrome wraps this with its own scaffold (top bar, find panel,
 *      error states). The body never owns chrome; the chrome never owns
 *      parsing.
 *   3. [extractTitle] — cheap probe for the library scanner so it can
 *      surface a nicer title than "guide.pdf" when the format exposes
 *      one (PDF metadata, EPUB OPF title, DOCX core properties, the
 *      Markdown `# H1`). Phase B doesn't yet call this — the scanner
 *      currently uses filename-without-extension — but the hook lands
 *      now so renderers ship with the contract complete.
 *
 * Adding a format = one new impl + one new registration line in
 * [com.eight87.pageboy.AppGraph]. The reader chrome stays untouched —
 * it dispatches through [com.eight87.pageboy.format.registry.FormatRegistry],
 * never through `when (format)`.
 *
 * **Liskov.** Every renderer honours the contract totally. A format the
 * renderer cannot parse falls out of [open] as a failure (exception
 * propagated to the projector → [com.eight87.pageboy.ui.reader.control.ReaderState.Failed]),
 * NOT a `NotImplementedError`. The Phase C placeholder is a different
 * deferral pattern — it implements the contract trivially and renders a
 * "not yet supported" message; it does not throw.
 */
interface DocumentRenderer {

  /** Format this renderer handles. Matches the entry under which the registry maps it. */
  val format: DocumentFormat

  /** Parse / probe the bytes into a renderable handle. Caller closes the returned handle. */
  suspend fun open(source: DocumentBytesSource): DocumentHandle

  /**
   * Render the opened document. Called from inside the reader chrome's
   * body slot; the renderer takes a [Modifier] so the chrome can size
   * the canvas correctly.
   *
   * Phase E.1 widened — [context] carries the chrome-side handles each
   * renderer may need (scroll-position persistence, find-in-doc sink,
   * read-only reading prefs). Renderers ignore the fields they don't
   * read (R.X.7 ISP — the placeholder ignores everything; the markdown
   * renderer reads scroll + find; future paginated renderers will read
   * `continuousScrolling`). Adding a future handle (annotation
   * commands at Phase G, signature commands at Phase H) is one field on
   * [RendererContext], not another signature widening here.
   */
  @Composable
  fun Body(handle: DocumentHandle, context: RendererContext, modifier: Modifier)

  /**
   * Cheap title probe for the scanner. Default returns `null`, meaning
   * "fall back to the filename-derived title". Renderers that can read
   * an embedded title without parsing the whole document (EPUB OPF,
   * PDF Info dict, Markdown first `#` heading) should override. The
   * scanner caps the work; an impl that needs to parse the entire
   * document to find a title should leave this null.
   */
  suspend fun extractTitle(source: DocumentBytesSource): String? = null
}
