package com.eight87.pageboy.data.openwith

import android.content.ContentResolver
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import com.eight87.pageboy.data.library.DocumentClassifier
import com.eight87.pageboy.data.library.DocumentFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream

/**
 * Phase N.4 — narrow interface (R.X.1) the `OpenWithActivity` depends on.
 * Hands a foreign `ACTION_VIEW` [Intent] to one concrete impl
 * ([AndroidOpenWithResolver]) and gets back a sealed [OpenWithResult]
 * the activity dispatches on.
 *
 * The resolver is responsible for:
 *  - validating the intent carries a `content://` URI,
 *  - resolving display name + declared MIME via [ContentResolver],
 *  - classifying the file via [DocumentClassifier] (extension + magic),
 *  - inserting an ad-hoc [com.eight87.pageboy.data.library.DocumentEntity]
 *    via [AdHocDocumentStore], and
 *  - returning the document id so the activity can launch the reader.
 *
 * It is NOT responsible for navigation (that's the activity), URI
 * permission lifecycle beyond the implicit grant (see "Keep this
 * document" overflow + [AdHocDocumentStore.keepAdHoc]), or for
 * deciding when to fail (it returns a sealed result; the activity
 * picks the toast / dialog).
 */
interface OpenWithResolver {
  suspend fun resolve(intent: Intent): OpenWithResult
}

/**
 * Phase N.4 — sealed dispatch (R.X.2) for `OpenWithActivity`. Adding a
 * new variant is one file + a compiler-flagged `when` site, not a
 * scattered `if` chain.
 */
sealed class OpenWithResult {

  /**
   * The document was classified + persisted as an ad-hoc row; the
   * activity launches `ReaderRoute(documentId)` next.
   *
   * [ephemeral] is true when the URI grant is the system's transient
   * `FLAG_GRANT_READ_URI_PERMISSION` (the default). "Keep this
   * document" in the reader overflow attempts to upgrade.
   */
  data class Ready(val documentId: String, val ephemeral: Boolean) : OpenWithResult()

  /** Classifier returned [DocumentFormat.Unknown] and auto-classify is
   *  disabled / the magic header didn't match. The activity surfaces a
   *  toast naming the file. */
  data class UnknownFormat(val displayName: String?) : OpenWithResult()

  /**
   * The intent didn't carry a usable `content://` URI, or the sender
   * revoked the URI permission before we could read.
   */
  data class PermissionRefused(val reason: String) : OpenWithResult()

  /** Anything else (read failed, classifier exploded, DB write
   *  failed). [reason] is a developer-facing string. */
  data class Failure(val reason: String) : OpenWithResult()
}

/**
 * Phase N.4 — production [OpenWithResolver]. Lives behind the narrow
 * interface so [com.eight87.pageboy.openwith.OpenWithActivity] takes
 * only the contract, not this concrete class.
 *
 * `autoClassifyUnknownMime` is a thunk (not a constant) so the activity
 * passes the user's current setting at resolve time without the
 * resolver having to subscribe to the settings flow itself (R.X.1 —
 * narrow surface).
 */
class AndroidOpenWithResolver(
  private val contentResolver: ContentResolver,
  private val adHocDocumentStore: AdHocDocumentStore,
  private val autoClassifyUnknownMime: suspend () -> Boolean,
) : OpenWithResolver {

  override suspend fun resolve(intent: Intent): OpenWithResult = withContext(Dispatchers.IO) {
    val uri: Uri = intent.data
      ?: return@withContext OpenWithResult.PermissionRefused("Intent did not carry a URI.")

    if (uri.scheme != ContentResolver.SCHEME_CONTENT) {
      // Locked decision #2 — pageboy ignores `file://` URIs entirely.
      return@withContext OpenWithResult.PermissionRefused(
        "Unsupported URI scheme: ${uri.scheme}. Pageboy reads content:// only.",
      )
    }

    val displayName = queryDisplayName(uri)
    val declaredMime = intent.type ?: runCatching { contentResolver.getType(uri) }.getOrNull()
    val effectiveName = displayName ?: fallbackNameFromUri(uri)

    val format = runCatching {
      classify(uri = uri, declaredMime = declaredMime, fileName = effectiveName)
    }.getOrElse { t ->
      return@withContext OpenWithResult.Failure(
        t.message ?: t::class.simpleName ?: "Classification failed",
      )
    }

    if (format == DocumentFormat.Unknown) {
      val auto = autoClassifyUnknownMime()
      if (!auto) {
        return@withContext OpenWithResult.UnknownFormat(displayName)
      }
      // Auto-classify was on but the classifier still gave up.
      return@withContext OpenWithResult.UnknownFormat(displayName)
    }

    val documentId = runCatching {
      adHocDocumentStore.createAdHoc(
        uri = uri,
        format = format,
        displayName = effectiveName,
      )
    }.getOrElse { t ->
      return@withContext OpenWithResult.Failure(
        t.message ?: t::class.simpleName ?: "Persist failed",
      )
    }

    OpenWithResult.Ready(documentId = documentId, ephemeral = true)
  }

  /**
   * Resolve the display name via OpenableColumns. Returns null when the
   * provider doesn't expose the column (rare; some legacy providers).
   */
  private fun queryDisplayName(uri: Uri): String? = runCatching {
    contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
      readFirstNonEmpty(c, OpenableColumns.DISPLAY_NAME)
    }
  }.getOrNull()

  private fun readFirstNonEmpty(cursor: Cursor, column: String): String? {
    if (!cursor.moveToFirst()) return null
    val idx = cursor.getColumnIndex(column)
    if (idx < 0) return null
    val value = cursor.getString(idx)
    return value?.takeIf { it.isNotBlank() }
  }

  /**
   * Final fallback for senders that expose neither OpenableColumns nor a
   * meaningful path segment. The classifier still gets to magic-sniff
   * the bytes; the display name only feeds extension-fallback heuristics
   * + the reader top bar.
   */
  private fun fallbackNameFromUri(uri: Uri): String {
    val last = uri.lastPathSegment ?: "document"
    return last.substringAfterLast('/').ifEmpty { "document" }
  }

  /**
   * Classify the byte stream. Same `DocumentClassifier` Phase B uses for
   * the scanner — we always sniff (the file may be `octet-stream` /
   * `application/zip` with no useful MIME signal); extension fallback
   * resolves text-like formats the magic header can't disambiguate.
   *
   * The classifier's `openStream` thunk lets the read happen on demand;
   * stream-open failures fall through to extension classification per
   * Phase B's contract.
   */
  private fun classify(uri: Uri, declaredMime: String?, fileName: String): DocumentFormat {
    val format = DocumentClassifier.classify(fileName) { openStreamOrNull(uri) }
    if (format != DocumentFormat.Unknown) return format

    // Last-resort heuristic for plain text: if the sender declared
    // `text/plain` and the file name has no extension hint, treat as Txt
    // (the user explicitly asked pageboy to handle this; the classifier
    // returns Unknown only when neither magic nor extension matched).
    if (declaredMime == "text/plain" || declaredMime == "text/markdown") {
      return when (declaredMime) {
        "text/markdown" -> DocumentFormat.Markdown
        else -> DocumentFormat.Txt
      }
    }
    return DocumentFormat.Unknown
  }

  private fun openStreamOrNull(uri: Uri): InputStream? = runCatching {
    contentResolver.openInputStream(uri)
  }.getOrNull()
}
