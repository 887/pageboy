package com.eight87.pageboy.data.library

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Phase B.1 — Room DAO for [DocumentEntity].
 *
 * Read shape supports the four tabs the library UI offers:
 *  - `observeAll()` — All / Started / Pinned tabs all derive from this flow
 *    (UI filters in-process so the chip-stack + tab change instantly without
 *    a fresh query round-trip).
 *  - `observeRecents()` — dedicated query because it caps results + orders
 *    by `lastOpenedAt`; cheap and surfaces only the recent stack.
 *  - `observeCollections()` — distinct non-null collections, feeds the
 *    filter chip row in `LibraryScreen`.
 *
 * Writes split between the scanner (bulk upsert + soft-delete sweep) and
 * the UI repository (per-document `setPinned` / `recordOpen` /
 * `setReadProgress`).
 */
@Dao
interface DocumentDao {

  @Query("SELECT * FROM documents WHERE is_missing = 0 ORDER BY title COLLATE NOCASE")
  fun observeAll(): Flow<List<DocumentEntity>>

  @Query(
    """
    SELECT * FROM documents
    WHERE is_missing = 0 AND last_opened_at IS NOT NULL
    ORDER BY last_opened_at DESC
    LIMIT :limit
    """
  )
  fun observeRecents(limit: Int = 30): Flow<List<DocumentEntity>>

  @Query("SELECT DISTINCT collection FROM documents WHERE is_missing = 0 AND collection IS NOT NULL ORDER BY collection COLLATE NOCASE")
  fun observeCollections(): Flow<List<String>>

  @Query("SELECT * FROM documents WHERE documentId = :id LIMIT 1")
  suspend fun findById(id: String): DocumentEntity?

  @Query("SELECT documentId FROM documents WHERE treeUriString = :treeUriString")
  suspend fun documentIdsForRoot(treeUriString: String): List<String>

  @Query("SELECT documentId FROM documents")
  suspend fun allDocumentIds(): List<String>

  @Upsert
  suspend fun upsertAll(documents: List<DocumentEntity>)

  @Upsert
  suspend fun upsert(document: DocumentEntity)

  /**
   * Soft-delete sweep used after a scan: every row from `treeUriString` is
   * flagged missing, then the scanner's per-document upserts flip the seen
   * ones back. The rows that stay `is_missing = 1` are the ones the file
   * actually disappeared for — their per-document state survives because
   * we never delete them.
   */
  @Query("UPDATE documents SET is_missing = 1 WHERE treeUriString = :treeUriString")
  suspend fun markRootMissing(treeUriString: String)

  @Query("UPDATE documents SET is_missing = 0 WHERE documentId IN (:ids)")
  suspend fun markPresentByIds(ids: List<String>)

  /**
   * Hard delete — only fired when the user explicitly removes a root,
   * so per-document state for that root genuinely goes away. The soft-delete
   * sweep above never calls this.
   */
  @Query("DELETE FROM documents WHERE treeUriString = :treeUriString")
  suspend fun deleteByRoot(treeUriString: String)

  @Query("UPDATE documents SET pinned = :pinned WHERE documentId = :id")
  suspend fun setPinned(id: String, pinned: Boolean)

  @Query("UPDATE documents SET last_opened_at = :timestamp WHERE documentId = :id")
  suspend fun setLastOpenedAt(id: String, timestamp: Long)

  @Query(
    """
    UPDATE documents
       SET last_read_position_ms = :positionMs,
           read_fraction = :fraction
     WHERE documentId = :id
    """
  )
  suspend fun setReadProgress(id: String, positionMs: Long, fraction: Float)
}
