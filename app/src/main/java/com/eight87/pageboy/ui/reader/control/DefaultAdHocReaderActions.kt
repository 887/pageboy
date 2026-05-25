package com.eight87.pageboy.ui.reader.control

import com.eight87.pageboy.data.library.DocumentSource
import com.eight87.pageboy.data.library.DocumentSourceKind
import com.eight87.pageboy.data.openwith.AdHocDocumentStore
import com.eight87.pageboy.data.openwith.KeepResult

/**
 * Phase N.8 — concrete [AdHocReaderActions]. Composes the
 * [DocumentSource] (read the entity to know the discriminator) with
 * the [AdHocDocumentStore] (the actual upgrade).
 *
 * Lives in `ui/reader/control/` next to the other per-axis controllers
 * (R.C). Concrete classes wire in `AppGraph`; the chrome takes the
 * narrow [AdHocReaderActions] interface.
 */
class DefaultAdHocReaderActions(
  private val documentSource: DocumentSource,
  private val adHocDocumentStore: AdHocDocumentStore,
) : AdHocReaderActions {

  override suspend fun isAdHocEphemeralFor(documentId: String): Boolean {
    val entity = documentSource.findById(documentId) ?: return false
    val source = entity.toSourceKind() as? DocumentSourceKind.AdHocOpen ?: return false
    return source.ephemeral
  }

  override suspend fun keepAdHoc(documentId: String): KeepResult =
    adHocDocumentStore.keepAdHoc(documentId)
}
