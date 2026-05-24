package com.eight87.pageboy.ui.reader.control

import com.eight87.pageboy.data.openwith.KeepResult

/**
 * Phase N.8 — narrow surface the reader chrome takes for ad-hoc
 * document affordances. Mirror of the other per-axis `ReaderXxxCommands`
 * interfaces (R.X.1 + R.C — each axis its own narrow interface).
 *
 * Only two methods today:
 *  - [isAdHocEphemeralFor] tells the top-bar overflow whether to
 *    surface the "Keep this document" entry for the current document
 *    id (true only for `AdHocOpen(ephemeral = true)` rows).
 *  - [keepAdHoc] tries to upgrade the URI grant to persistable + emits
 *    the sealed [KeepResult] the chrome dispatches on.
 *
 * Production wiring lives in `AppGraph`; the chrome takes the narrow
 * interface so a future renderer-level "Save copy" action can plug
 * into the same surface.
 */
interface AdHocReaderActions {
  suspend fun isAdHocEphemeralFor(documentId: String): Boolean
  suspend fun keepAdHoc(documentId: String): KeepResult
}
