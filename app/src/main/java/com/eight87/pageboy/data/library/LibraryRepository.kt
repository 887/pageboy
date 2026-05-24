package com.eight87.pageboy.data.library

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow

/**
 * Phase B.8 — single concrete implementation of [DocumentSource] +
 * [ScanWriter]. Lives behind the narrow interfaces when handed out from
 * `AppGraph` — composables never see this concrete type (family SOLID-D
 * pattern).
 */
class LibraryRepository(
  private val database: LibraryDatabase,
) : DocumentSource, ScanWriter {

  private val documentDao = database.documentDao()

  // ---- DocumentSource ----

  override fun observeDocuments(): Flow<List<DocumentEntity>> = documentDao.observeAll()

  override fun observeRecents(limit: Int): Flow<List<DocumentEntity>> =
    documentDao.observeRecents(limit)

  override fun observeCollections(): Flow<List<String>> = documentDao.observeCollections()

  override suspend fun findById(id: String): DocumentEntity? = documentDao.findById(id)

  override suspend fun setPinned(id: String, pinned: Boolean) {
    documentDao.setPinned(id, pinned)
  }

  override suspend fun recordOpen(id: String) {
    documentDao.setLastOpenedAt(id, System.currentTimeMillis())
  }

  override suspend fun setReadProgress(id: String, positionMs: Long, fraction: Float) {
    documentDao.setReadProgress(id, positionMs, fraction.coerceIn(0f, 1f))
  }

  // ---- ScanWriter ----

  override suspend fun allDocumentIds(): Set<String> =
    documentDao.allDocumentIds().toSet()

  override suspend fun applyScan(snapshot: ScanSnapshot, touchedRoots: Set<String>) {
    val now = System.currentTimeMillis()
    val seenIds = snapshot.documents.map { it.documentId }
    database.withTransaction {
      // 1. Soft-delete sweep — every row from every touched root flagged
      //    missing; the per-document upserts below flip the seen ones
      //    back. Rows that stay missing represent files that disappeared.
      for (root in touchedRoots) {
        documentDao.markRootMissing(root)
      }
      // 2. Per-document upsert. Preserve per-document state for existing
      //    rows (pinned / last opened / read progress) by deriving from
      //    the existing entity; new rows seed with default state.
      val entities = snapshot.documents.map { scanned ->
        val existing = documentDao.findById(scanned.documentId)
        DocumentEntity(
          documentId = scanned.documentId,
          treeUriString = scanned.treeUriString,
          relativePath = scanned.relativePath,
          documentUriString = scanned.documentUriString,
          title = scanned.title,
          fileName = scanned.fileName,
          format = DocumentFormat.id(scanned.format),
          sizeBytes = scanned.sizeBytes,
          mtimeMs = scanned.mtimeMs,
          collection = scanned.collection,
          addedAt = existing?.addedAt ?: now,
          lastOpenedAt = existing?.lastOpenedAt,
          lastReadPositionMs = existing?.lastReadPositionMs ?: 0L,
          readFraction = existing?.readFraction ?: 0f,
          pinned = existing?.pinned ?: false,
          isMissing = false,
        )
      }
      if (entities.isNotEmpty()) documentDao.upsertAll(entities)
      // 3. Re-mark present any seen-but-already-extant rows that may have
      //    been swept above before their upsert ran (the upsert sets
      //    is_missing = 0 anyway, but being explicit makes the
      //    invariant easier to reason about). The upserts above already
      //    set is_missing = 0, so this is a guard-rail in case Room
      //    short-circuits unchanged upserts.
      if (seenIds.isNotEmpty()) documentDao.markPresentByIds(seenIds)
    }
  }

  override suspend fun deleteRoot(treeUriString: String) {
    database.withTransaction { documentDao.deleteByRoot(treeUriString) }
  }
}
