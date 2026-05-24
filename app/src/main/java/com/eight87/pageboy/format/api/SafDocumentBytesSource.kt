package com.eight87.pageboy.format.api

import android.content.ContentResolver
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream

/**
 * Phase C.1 — Android-side [DocumentBytesSource] backed by a SAF
 * `content://` URI. Resolves through [ContentResolver]; reads happen on
 * [Dispatchers.IO].
 *
 * Living in the format package (not in `data/library/`) because the
 * format layer owns the bytes-source contract — renderers are pure
 * plumbing that take a stream and emit Compose content (per R.X.6 the
 * format/ layer must not import data/library/). The reader chrome
 * builds one of these from the resolved
 * [com.eight87.pageboy.data.library.DocumentEntity.documentUriString]
 * and hands it to whatever renderer the registry returns.
 *
 * The class is small on purpose; bigger affordances (caching, magic-byte
 * sniff replays, range requests) land if a per-format renderer earns
 * them.
 */
class SafDocumentBytesSource(
  private val contentResolver: ContentResolver,
  /**
   * Phase F.3 — visible to the package so the PDF renderer can hand
   * the SAF URI straight to androidx.pdf's `PdfViewerFragment` without
   * round-tripping bytes through a parcel + back. Other renderers
   * (Markdown / TXT) stay on the `openStream()` path and never see
   * the URI.
   */
  val documentUri: Uri,
  private val documentFile: DocumentFile? = null,
) : DocumentBytesSource {

  override suspend fun openStream(): InputStream = withContext(Dispatchers.IO) {
    contentResolver.openInputStream(documentUri)
      ?: throw IOException("ContentResolver returned null stream for $documentUri")
  }

  override suspend fun length(): Long = withContext(Dispatchers.IO) {
    documentFile?.length() ?: runCatching {
      contentResolver.openFileDescriptor(documentUri, "r")?.use { it.statSize }
    }.getOrNull() ?: -1L
  }

  override suspend fun displayName(): String? = withContext(Dispatchers.IO) {
    documentFile?.name
  }
}
