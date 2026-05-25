package com.eight87.pageboy.data.annotation

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Phase G.1 — Room DAO for [AnnotationEntity].
 *
 * Observe-only reads for the UI (per [com.eight87.pageboy.data.annotation.AnnotationSource]),
 * insert/update/delete from [com.eight87.pageboy.data.annotation.AnnotationStore]
 * (per refactor-solid R.X.1 — narrow interfaces).
 *
 * Per-document observe scoped to the open reader; per-page observe is
 * the renderer-side hot path (the overlay only needs the visible
 * page's annotations).
 *
 * Soft-delete via [softDelete] (sets is_deleted = 1); hard delete via
 * [hardDeleteAllForDocument] only fires when the user explicitly
 * removes a document from the library (lifecycle owned by Phase B's
 * repository, not the annotation UI).
 */
@Dao
interface AnnotationDao {

  @Query("SELECT * FROM annotations WHERE documentId = :documentId AND is_deleted = 0 ORDER BY page_index ASC, created_at ASC")
  fun observeForDocument(documentId: String): Flow<List<AnnotationEntity>>

  @Query("SELECT * FROM annotations WHERE documentId = :documentId AND page_index = :pageIndex AND is_deleted = 0 ORDER BY created_at ASC")
  fun observeForPage(documentId: String, pageIndex: Int): Flow<List<AnnotationEntity>>

  @Query("SELECT * FROM annotations WHERE documentId = :documentId AND is_deleted = 0 ORDER BY page_index ASC, created_at ASC")
  suspend fun listForDocument(documentId: String): List<AnnotationEntity>

  @Query("SELECT * FROM annotations WHERE id = :id LIMIT 1")
  suspend fun findById(id: String): AnnotationEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insert(annotation: AnnotationEntity)

  @Update
  suspend fun update(annotation: AnnotationEntity)

  @Query("UPDATE annotations SET is_deleted = 1, modified_at = :now WHERE id = :id")
  suspend fun softDelete(id: String, now: Long)

  @Query("UPDATE annotations SET is_deleted = 0, modified_at = :now WHERE id = :id")
  suspend fun restore(id: String, now: Long)

  @Query("DELETE FROM annotations WHERE documentId = :documentId")
  suspend fun hardDeleteAllForDocument(documentId: String)
}
