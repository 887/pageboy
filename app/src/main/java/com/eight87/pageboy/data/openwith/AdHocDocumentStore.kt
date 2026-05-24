package com.eight87.pageboy.data.openwith

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import com.eight87.pageboy.data.library.DocumentDao
import com.eight87.pageboy.data.library.DocumentEntity
import com.eight87.pageboy.data.library.DocumentFormat
import com.eight87.pageboy.data.library.DocumentSourceCodec
import com.eight87.pageboy.data.library.DocumentSourceKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest

/**
 * Phase N — narrow store for ad-hoc documents created by `OpenWithActivity`.
 * Three operations:
 *  - [createAdHoc] inserts a row (or returns the existing id) so the
 *    reader can open it.
 *  - [keepAdHoc] tries to upgrade the URI grant to persistable so the
 *    document survives the activity instance.
 *  - [saveToLibraryRoot] is the fallback when [keepAdHoc] returns
 *    `CannotPersist`: copy the bytes into a SAF tree pageboy already
 *    has access to, replace the row's source with `LibraryRoot`.
 *
 * Lives behind a narrow interface (R.X.1) so `OpenWithActivity` /
 * reader-side composables take only the methods they need.
 */
interface AdHocDocumentStore {

  /**
   * Insert (or return the existing id for) an ad-hoc document. Always
   * starts in the `AdHocOpen(uri, ephemeral = true)` state; "Keep this
   * document" upgrades later.
   */
  suspend fun createAdHoc(uri: Uri, format: DocumentFormat, displayName: String): String

  /**
   * Try to upgrade the ad-hoc grant to persistable. Returns
   * [KeepResult.Kept] on success, [KeepResult.CannotPersist] when the
   * sender did not include `FLAG_GRANT_PERSISTABLE_URI_PERMISSION`
   * (raises `SecurityException` internally) — that's the trigger for
   * the save-to-library-root fallback.
   *
   * [KeepResult.NotAdHoc] is returned if the document isn't an ad-hoc
   * row (defensive — the UI shouldn't surface "Keep document" on
   * library rows).
   */
  suspend fun keepAdHoc(documentId: String): KeepResult

  /**
   * Phase N.9 — fallback when [keepAdHoc] returned `CannotPersist`.
   * Copies the bytes from the ad-hoc URI into [targetTreeUri] (a SAF
   * tree pageboy already has access to) via the supplied [openOutput]
   * thunk. Replaces the row's source with `LibraryRoot(targetTreeUri)`
   * and clears the ephemeral state.
   *
   * The thunk is `suspend (Uri) -> Uri?` so the composable that owns
   * the SAF picker / `DocumentFile.createFile(...)` call (where the
   * Android UI thread can drive the SAF surface) can hand back the
   * resulting document URI without this store reaching into Compose.
   */
  suspend fun saveToLibraryRoot(
    documentId: String,
    targetTreeUri: Uri,
    openOutput: suspend (Uri) -> Uri?,
  ): KeepResult
}

/**
 * Phase N.8 — sealed result of [AdHocDocumentStore.keepAdHoc] /
 * [AdHocDocumentStore.saveToLibraryRoot]. Same pattern as
 * `OpenWithResult` — sealed dispatch over UI branching (R.X.2).
 */
sealed class KeepResult {

  /** Persistable URI grant taken; the row's `ephemeral` flag flipped to false. */
  data object Kept : KeepResult()

  /**
   * `takePersistableUriPermission` raised `SecurityException` or the
   * row's source URI is unsuitable for persistence; the UI surfaces
   * the save-to-library-root prompt.
   */
  data class CannotPersist(val reason: String) : KeepResult()

  /** Defensive — the documentId resolves to a library-root row, not an
   *  ad-hoc one. The UI shouldn't expose the action in this case but
   *  the store returns this instead of throwing. */
  data object NotAdHoc : KeepResult()
}

/**
 * Phase N — production [AdHocDocumentStore] backed by [DocumentDao] +
 * the system [ContentResolver].
 *
 * `createAdHoc` derives a stable document id from the SHA-256 of the
 * URI (same family pattern Phase B uses for library docs) so reopening
 * the same URI from a different intent picks up the existing row's
 * `lastOpenedAt` / `pinned` / `scrollPositionJson` — important for the
 * Recents feed (locked decision #5).
 */
class RoomAdHocDocumentStore(
  private val documentDao: DocumentDao,
  private val contentResolver: ContentResolver,
) : AdHocDocumentStore {

  override suspend fun createAdHoc(
    uri: Uri,
    format: DocumentFormat,
    displayName: String,
  ): String = withContext(Dispatchers.IO) {
    val documentId = adHocId(uri)
    val now = System.currentTimeMillis()
    val existing = documentDao.findById(documentId)
    val sourceJson = DocumentSourceCodec.encode(
      DocumentSourceKind.AdHocOpen(uri = uri.toString(), ephemeral = true),
    )
    val title = displayName.substringBeforeLast('.', missingDelimiterValue = displayName)
    val entity = DocumentEntity(
      documentId = documentId,
      // Empty treeUri so the row is unambiguously not a library doc;
      // the source_json column is the authoritative discriminator.
      treeUriString = "",
      relativePath = displayName,
      documentUriString = uri.toString(),
      title = existing?.title ?: title,
      fileName = displayName,
      format = DocumentFormat.id(format),
      sizeBytes = null,
      mtimeMs = existing?.mtimeMs ?: now,
      collection = null,
      addedAt = existing?.addedAt ?: now,
      lastOpenedAt = existing?.lastOpenedAt,
      lastReadPositionMs = existing?.lastReadPositionMs ?: 0L,
      readFraction = existing?.readFraction ?: 0f,
      scrollPositionJson = existing?.scrollPositionJson,
      pinned = existing?.pinned ?: false,
      isMissing = false,
      sourceJson = sourceJson,
    )
    documentDao.insertOne(entity)
    documentId
  }

  override suspend fun keepAdHoc(documentId: String): KeepResult = withContext(Dispatchers.IO) {
    val entity = documentDao.findById(documentId) ?: return@withContext KeepResult.CannotPersist(
      "Document not found.",
    )
    val source = entity.toSourceKind() as? DocumentSourceKind.AdHocOpen
      ?: return@withContext KeepResult.NotAdHoc
    val uri = runCatching { Uri.parse(source.uri) }.getOrNull()
      ?: return@withContext KeepResult.CannotPersist("Stored URI is not parseable.")
    try {
      contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
      // Flip ephemeral → false in place.
      val newJson = DocumentSourceCodec.encode(source.copy(ephemeral = false))
      documentDao.setSourceJson(documentId, newJson)
      KeepResult.Kept
    } catch (se: SecurityException) {
      // Most common with email-attachment URIs that don't ship
      // FLAG_GRANT_PERSISTABLE_URI_PERMISSION. The UI shows the
      // save-to-library-root prompt next.
      KeepResult.CannotPersist(se.message ?: "Sender did not grant persistable access.")
    } catch (t: Throwable) {
      KeepResult.CannotPersist(t.message ?: t::class.simpleName ?: "Persist failed.")
    }
  }

  override suspend fun saveToLibraryRoot(
    documentId: String,
    targetTreeUri: Uri,
    openOutput: suspend (Uri) -> Uri?,
  ): KeepResult {
    val entity = documentDao.findById(documentId)
      ?: return KeepResult.CannotPersist("Document not found.")
    val source = entity.toSourceKind() as? DocumentSourceKind.AdHocOpen
      ?: return KeepResult.NotAdHoc

    val targetDocumentUri = openOutput(targetTreeUri)
      ?: return KeepResult.CannotPersist("Could not create destination file.")

    val ok = withContext(Dispatchers.IO) {
      runCatching {
        contentResolver.openInputStream(Uri.parse(source.uri))?.use { input ->
          contentResolver.openOutputStream(targetDocumentUri)?.use { output ->
            input.copyTo(output)
          } ?: error("Output stream null")
        } ?: error("Input stream null")
        true
      }.getOrElse { return@withContext false }
    }
    if (!ok) return KeepResult.CannotPersist("Copy failed.")

    // Replace the source with LibraryRoot pointing at the picked tree.
    withContext(Dispatchers.IO) {
      val newSource = DocumentSourceKind.LibraryRoot(rootTreeUriString = targetTreeUri.toString())
      documentDao.setSourceJson(documentId, DocumentSourceCodec.encode(newSource))
    }
    return KeepResult.Kept
  }

  /**
   * Stable id derived from the URI. Same SHA-256 pattern Phase B uses
   * for library docs (with a leading "adhoc:" namespace so the two id
   * spaces never collide).
   */
  private fun adHocId(uri: Uri): String {
    val raw = "adhoc:${uri}"
    val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
    return digest.joinToString("") { "%02x".format(it) }
  }
}
