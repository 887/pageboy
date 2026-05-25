package com.eight87.pageboy.data.annotation

import kotlinx.coroutines.flow.Flow

/**
 * Phase G.2 — observe-only narrow data interface (R.X.1) over the
 * annotation table. The overlay Composable + the exporter both depend
 * on this; neither sees the writer side.
 *
 * Separating observe from write follows the same shape `DocumentSource`
 * uses in Phase B (and the family-wide R.A pattern in
 * `docs/plans/refactor-solid.md`).
 */
interface AnnotationSource {

  /** All non-deleted annotations for the document, ordered by page then time. */
  fun observe(documentId: String): Flow<List<AnnotationEntity>>

  /** Per-page subset — hot path for the overlay drawer. */
  fun observeForPage(documentId: String, pageIndex: Int): Flow<List<AnnotationEntity>>

  /** Snapshot read for the OpenPDF exporter. */
  suspend fun list(documentId: String): List<AnnotationEntity>
}

/**
 * Phase G.2 — write-only narrow data interface (R.X.1) over the
 * annotation table. `AndroidAnnotationCommands` (Phase G.3) depends
 * on this; the overlay rendering path does not.
 */
interface AnnotationStore {

  suspend fun add(annotation: AnnotationEntity)

  suspend fun update(annotation: AnnotationEntity)

  /** Soft-delete — row stays for undo until the v1+ vacuum sweeps it. */
  suspend fun delete(id: String)

  /** Restore a soft-deleted row (undo affordance hook). */
  suspend fun restore(id: String)
}
