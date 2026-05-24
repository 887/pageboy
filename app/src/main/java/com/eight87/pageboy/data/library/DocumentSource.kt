package com.eight87.pageboy.data.library

import kotlinx.coroutines.flow.Flow

/**
 * Phase B.8 — narrow data interface (SOLID-I) the UI depends on. The
 * concrete [LibraryRepository] implements this plus [ScanWriter]; the
 * UI never sees the whole repo.
 */
interface DocumentSource {

  fun observeDocuments(): Flow<List<DocumentEntity>>

  fun observeRecents(limit: Int = 30): Flow<List<DocumentEntity>>

  fun observeCollections(): Flow<List<String>>

  suspend fun findById(id: String): DocumentEntity?

  suspend fun setPinned(id: String, pinned: Boolean)

  suspend fun recordOpen(id: String)

  suspend fun setReadProgress(id: String, positionMs: Long, fraction: Float)
}

/**
 * Phase B.8 — narrow write surface used only by the rescan coordinator.
 * Keeps the UI repository handle from carrying write authority the UI
 * shouldn't have.
 */
interface ScanWriter {

  suspend fun allDocumentIds(): Set<String>

  suspend fun applyScan(snapshot: ScanSnapshot, touchedRoots: Set<String>)

  /** Hard-delete every document for a removed root (used when the user removes the root). */
  suspend fun deleteRoot(treeUriString: String)
}
