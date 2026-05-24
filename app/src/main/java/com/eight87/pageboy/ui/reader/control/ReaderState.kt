package com.eight87.pageboy.ui.reader.control

import com.eight87.pageboy.format.api.DocumentHandle

/**
 * Phase C.4 — sealed lifecycle of an opened document (R.X.2 — sealed
 * dispatch, not enum + when-chain). The reader chrome branches on this
 * type via exhaustive `when`; adding a state is a new variant + a
 * compiler-flagged handful of sites.
 */
sealed class ReaderState {

  /** No document opened yet (initial state, also after `close()`). */
  data object Idle : ReaderState()

  /** Open is in progress. The chrome shows a progress indicator. */
  data object Opening : ReaderState()

  /** Document open and ready to render. */
  data class Open(val handle: DocumentHandle) : ReaderState()

  /** Open failed. Reason surfaced in the error UI. */
  data class Failed(val reason: String) : ReaderState()
}
