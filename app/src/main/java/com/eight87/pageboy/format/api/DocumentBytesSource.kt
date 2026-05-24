package com.eight87.pageboy.format.api

import java.io.InputStream

/**
 * Phase C.1 — the byte-stream contract every [DocumentRenderer] reads. The
 * reader resolves a [com.eight87.pageboy.data.library.DocumentEntity] to a
 * [DocumentBytesSource] (concrete impl in [SafDocumentBytesSource]) and
 * hands it to the renderer; renderers never touch SAF, ContentResolver,
 * `DocumentFile`, or Android `Context` directly.
 *
 * The single-stream contract keeps renderers testable from JVM-only
 * unit tests — a `ByteArrayInputStream`-backed fake satisfies the
 * interface without needing an Android `Context`.
 *
 * Streams returned from [openStream] are caller-owned and must be closed
 * by the caller. Renderers that need to read the document multiple times
 * (e.g. PDF: page bitmap rasterisation after initial parse) call
 * [openStream] once per read; the impl may serve a fresh underlying
 * `InputStream` each time.
 */
interface DocumentBytesSource {

  /** Open a fresh stream over the document's bytes. Caller closes. */
  suspend fun openStream(): InputStream

  /** Byte length, or `-1` when unknown (the SAF case for some providers). */
  suspend fun length(): Long

  /** Display name from the source (filename, etc.), or null when unavailable. */
  suspend fun displayName(): String?
}
