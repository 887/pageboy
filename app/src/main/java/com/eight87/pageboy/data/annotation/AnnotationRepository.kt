package com.eight87.pageboy.data.annotation

import kotlinx.coroutines.flow.Flow

/**
 * Phase G.2 — single concrete impl of [AnnotationSource] +
 * [AnnotationStore]. Behind the narrow interfaces when handed out from
 * `AppGraph` — composables never see this concrete type (family
 * SOLID-D pattern, mirrors `LibraryRepository` in Phase B).
 */
class AnnotationRepository(
  private val dao: AnnotationDao,
  private val now: () -> Long = { System.currentTimeMillis() },
) : AnnotationSource, AnnotationStore {

  // ---- AnnotationSource ----

  override fun observe(documentId: String): Flow<List<AnnotationEntity>> =
    dao.observeForDocument(documentId)

  override fun observeForPage(documentId: String, pageIndex: Int): Flow<List<AnnotationEntity>> =
    dao.observeForPage(documentId, pageIndex)

  override suspend fun list(documentId: String): List<AnnotationEntity> =
    dao.listForDocument(documentId)

  // ---- AnnotationStore ----

  override suspend fun add(annotation: AnnotationEntity) {
    dao.insert(annotation)
  }

  override suspend fun update(annotation: AnnotationEntity) {
    dao.update(annotation.copy(modifiedAt = now()))
  }

  override suspend fun delete(id: String) {
    dao.softDelete(id, now())
  }

  override suspend fun restore(id: String) {
    dao.restore(id, now())
  }
}
