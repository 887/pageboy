package com.eight87.pageboy.data.library

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Phase B.7 — narrow interface for triggering and observing library
 * rescans. Composables / ViewModels depend on this contract; the concrete
 * [AndroidLibraryRescanCoordinator] wires the Android-side lifecycle
 * hooks (root-change observer, foreground re-scan, manual trigger).
 *
 * No `ContentObserver` on the SAF tree — SAF doesn't surface change
 * events the way `MediaStore` does. Rescan-on-signal, not on observation.
 */
interface LibraryRescanCoordinator {

  val state: StateFlow<ScanState>

  /** One-shot stream of completion summaries — fires once per successful scan. */
  val scanSummaries: SharedFlow<ScanSummary>

  /**
   * Trigger a rescan. Conflated — calling repeatedly while a scan is
   * running queues at most one follow-up.
   */
  fun requestRescan()
}

/** Lifecycle of a single rescan pass. */
sealed class ScanState {
  data object Idle : ScanState()

  /**
   * Active scan. [documentsFound] ticks up continuously as the walker
   * emits per-folder batches; [currentFolder] is the folder currently
   * being walked, surfaced by the in-library progress banner.
   *
   * [total] is non-null when the scanner knows the expected document count
   * upfront (e.g. a filesystem scan that pre-counts files). SAF scanning
   * cannot determine the total in advance, so it stays null.
   */
  data class Scanning(
    val documentsFound: Int = 0,
    val total: Int? = null,
    val currentFolder: String? = null,
  ) : ScanState()

  data class Failed(val cause: Throwable) : ScanState()
}

/** Summary of a completed scan. [newDocuments] = ids seen this scan that weren't in the DB before. */
data class ScanSummary(val newDocuments: Int, val totalDocuments: Int)
