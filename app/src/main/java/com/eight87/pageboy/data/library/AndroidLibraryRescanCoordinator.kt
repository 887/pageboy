package com.eight87.pageboy.data.library

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Phase B.7 — Android-side coordinator. Owns one long-running coroutine
 * that consumes scan requests from a conflated channel, runs the scanner,
 * persists the snapshot via [ScanWriter], updates the [state] / emits a
 * summary.
 *
 * Wiring (`start` is called once from `AppGraph`):
 *  - `PersistedUriPermissionStore.observeRoots()` — `distinctUntilChanged`
 *    + each change fires a [requestRescan]. Adding a folder triggers a
 *    scan automatically; removing one triggers a scan that will see the
 *    other roots' documents stay present and the removed root's
 *    documents go missing → soft-deleted.
 *  - Initial scan: one [requestRescan] on `start()` so the library
 *    populates without the user having to add a folder for the first
 *    rescan to fire.
 */
class AndroidLibraryRescanCoordinator(
  private val scanner: LibraryScanner,
  private val rootStore: PersistedUriPermissionStore,
  private val writer: ScanWriter,
  private val applicationScope: CoroutineScope,
) : LibraryRescanCoordinator {

  private val _state = MutableStateFlow<ScanState>(ScanState.Idle)
  override val state: StateFlow<ScanState> = _state.asStateFlow()

  private val _scanSummaries = MutableSharedFlow<ScanSummary>(extraBufferCapacity = 4)
  override val scanSummaries: SharedFlow<ScanSummary> = _scanSummaries.asSharedFlow()

  // Conflated channel — at most one pending request, drops any extras while
  // a scan is in flight. Conflated semantics match whisperboy's pattern.
  private val requests = Channel<Unit>(capacity = Channel.CONFLATED)

  private var workerJob: Job? = null

  /**
   * Start the coordinator: spin up the worker loop and wire the
   * root-change observer. Idempotent — calling twice is a no-op (the
   * existing worker stays).
   */
  fun start() {
    if (workerJob != null) return
    workerJob = applicationScope.launch {
      for (signal in requests) {
        runScanCatching()
      }
    }
    // Root-change → request rescan. distinctUntilChanged so a no-op
    // emission (same list) doesn't trigger a redundant walk.
    rootStore.observeRoots()
      .distinctUntilChanged()
      .onEach { requestRescan() }
      .launchIn(applicationScope)
    // Initial scan on start so the library populates without the user
    // having to add a folder for the first rescan to fire.
    requestRescan()
  }

  override fun requestRescan() {
    // trySend on a CONFLATED channel just drops if already pending.
    requests.trySend(Unit)
  }

  private suspend fun runScanCatching() {
    try {
      runScan()
    } catch (t: Throwable) {
      Log.w("pageboy.scan", "SCAN_FAILED: ${t.javaClass.simpleName}: ${t.message}")
      _state.value = ScanState.Failed(t)
    }
  }

  private suspend fun runScan() {
    val roots = rootStore.observeRoots().first()
    if (roots.isEmpty()) {
      // Nothing to do; still emit an empty state so the UI can stop
      // showing a stale Scanning banner if any callers left one up.
      _state.value = ScanState.Idle
      return
    }
    _state.value = ScanState.Scanning()
    val priorIds = writer.allDocumentIds()
    val snapshot = scanner.scan(roots) { documentsFound, currentFolder ->
      _state.value = ScanState.Scanning(
        documentsFound = documentsFound,
        currentFolder = currentFolder,
      )
    }
    writer.applyScan(snapshot, touchedRoots = roots.map { it.treeUri.toString() }.toSet())
    val seenIds = snapshot.documents.map { it.documentId }.toSet()
    val newCount = (seenIds - priorIds).size
    _state.value = ScanState.Idle
    _scanSummaries.tryEmit(
      ScanSummary(newDocuments = newCount, totalDocuments = snapshot.documents.size),
    )
  }
}
