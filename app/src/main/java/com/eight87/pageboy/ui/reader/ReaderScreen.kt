package com.eight87.pageboy.ui.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eight87.pageboy.R
import com.eight87.pageboy.data.openwith.KeepResult
import com.eight87.pageboy.domain.render.RendererReadingPrefs
import com.eight87.pageboy.format.registry.FormatRegistry
import com.eight87.pageboy.ui.reader.control.AdHocReaderActions
import com.eight87.pageboy.ui.reader.control.InMemoryFindInDocCommands
import com.eight87.pageboy.ui.reader.control.ReaderState
import com.eight87.pageboy.ui.reader.control.ReaderStateProjector
import com.eight87.pageboy.ui.reader.control.ScrollPersistence
import com.eight87.pageboy.ui.reader.control.ShareExportCommands
import kotlinx.coroutines.launch

/**
 * Phase C.5 — reader screen orchestrator. Holds no business logic; just
 * the layout (top bar + optional find panel + body) and the wiring
 * between the per-axis controllers (R.C) and the chrome pieces
 * (top bar / find panel / body / error state — each in its own file
 * per R.X.4 / R.D).
 *
 * Takes narrow interfaces (R.X.1): [ReaderStateProjector],
 * [FormatRegistry], [FindInDocCommands], [ShareExportCommands]. No god
 * controller, no concrete repository import. The chrome reads the
 * projected state + dispatches via the registry; the projector owns
 * the open/close lifecycle.
 *
 * Edge-to-edge insets honoured via `Modifier.windowInsetsPadding(systemBars)`
 * — top inset eaten by the top bar, bottom inset eaten by the body so
 * the per-format renderers don't have to think about it. The
 * `WindowCompat.setDecorFitsSystemWindows(window, false)` is already
 * set in [com.eight87.pageboy.PageboyActivity]'s `onCreate` (Phase A
 * shipped that wiring).
 */
@Composable
fun ReaderScreen(
  documentId: String,
  readerStateProjector: ReaderStateProjector,
  formatRegistry: FormatRegistry,
  findInDocCommands: InMemoryFindInDocCommands,
  shareExportCommands: ShareExportCommands,
  scrollPersistence: ScrollPersistence,
  readingPrefs: RendererReadingPrefs,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
  adHocActions: AdHocReaderActions? = null,
) {
  val state by readerStateProjector.state.collectAsStateWithLifecycle()
  val findQuery by findInDocCommands.query.collectAsStateWithLifecycle()
  val findMatches by findInDocCommands.matches.collectAsStateWithLifecycle()
  val findCurrent by findInDocCommands.currentMatchIndex.collectAsStateWithLifecycle()

  var findPanelOpen by remember(documentId) { mutableStateOf(false) }

  // Phase N.8 — Keep-this-document affordance state. Recomputed on
  // documentId change; absent (false) when AdHocReaderActions is not
  // wired (e.g. legacy smoke tests).
  var keepVisible by remember(documentId) { mutableStateOf(false) }
  val snackbarHostState = remember { SnackbarHostState() }
  val coroutineScope = rememberCoroutineScope()
  val context = LocalContext.current
  val keptMessage = stringResource(R.string.reader_keep_document_kept)
  val cannotPersistMessage = stringResource(R.string.reader_keep_document_cannot_persist)
  val saveToLibraryLabel = stringResource(R.string.reader_keep_save_to_library)

  LaunchedEffect(documentId, adHocActions) {
    keepVisible = adHocActions?.isAdHocEphemeralFor(documentId) == true
  }

  // Open the document on first compose for this id. Closing handled by
  // the DisposableEffect below so leaving the screen releases the handle.
  LaunchedEffect(documentId) {
    readerStateProjector.open(documentId)
  }
  DisposableEffect(documentId) {
    onDispose {
      readerStateProjector.close()
      findInDocCommands.clear()
    }
  }

  val title = when (val s = state) {
    is ReaderState.Open -> s.handle.title
    is ReaderState.Failed -> ""
    else -> ""
  }

  Box(modifier = modifier.fillMaxSize()) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .windowInsetsPadding(WindowInsets.systemBars)
        .semantics { testTag = "reader_screen" },
    ) {
    ReaderTopBar(
      title = title,
      findActive = findPanelOpen,
      onBack = onBack,
      onToggleFind = {
        findPanelOpen = !findPanelOpen
        if (!findPanelOpen) findInDocCommands.clear()
      },
      onShare = {
        val open = state as? ReaderState.Open
        if (open != null) {
          // The chrome doesn't carry the SAF URI directly; the projector
          // already resolved it to open the renderer, but it's not on
          // the handle. For Phase C the share affordance defers to the
          // entity's display name and a no-op URI; once Phase D lands a
          // renderer that carries the source on its handle, the
          // share-current-document call will pick that up. For Phase C
          // the share path is wired but inert.
          shareExportCommands.shareCurrentDocument(
            documentUriString = "",
            displayName = open.handle.title,
          )
        }
      },
      showKeepDocument = keepVisible,
      onKeepDocument = {
        val actions = adHocActions ?: return@ReaderTopBar
        coroutineScope.launch {
          when (val r = actions.keepAdHoc(documentId)) {
            is KeepResult.Kept -> {
              keepVisible = false
              snackbarHostState.showSnackbar(message = keptMessage)
            }
            is KeepResult.CannotPersist -> {
              // Phase N.9 — the snackbar's action is the prompt entry
              // point for the save-to-library-root fallback. The
              // complete fallback (root picker + bytes copy) lands as
              // a follow-up bottom sheet; for the v1 surface here we
              // surface the prompt copy + leave the action a no-op
              // until the root-picker bottom sheet is wired in
              // (tracked under open-with.md N.9 follow-up). The
              // snackbar action result still lets the user opt out.
              val res = snackbarHostState.showSnackbar(
                message = cannotPersistMessage,
                actionLabel = saveToLibraryLabel,
              )
              if (res == SnackbarResult.ActionPerformed) {
                snackbarHostState.showSnackbar(
                  message = context.getString(R.string.reader_keep_no_roots),
                )
              }
            }
            is KeepResult.NotAdHoc -> keepVisible = false
          }
        }
      },
    )
    if (findPanelOpen) {
      ReaderFindPanel(
        query = findQuery,
        currentMatchIndex = findCurrent,
        matchCount = findMatches.size,
        onQueryChange = findInDocCommands::setQuery,
        onNext = findInDocCommands::next,
        onPrevious = findInDocCommands::previous,
        onClose = {
          findPanelOpen = false
          findInDocCommands.clear()
        },
      )
    }
    ReaderBody(
      state = state,
      formatRegistry = formatRegistry,
      documentId = documentId,
      scrollPersistence = scrollPersistence,
      findCommands = findInDocCommands,
      readingPrefs = readingPrefs,
      onRetry = { readerStateProjector.open(documentId) },
      modifier = Modifier.fillMaxSize(),
    )
    }
    SnackbarHost(
      hostState = snackbarHostState,
      modifier = Modifier
        .align(Alignment.BottomCenter)
        .windowInsetsPadding(WindowInsets.systemBars)
        .semantics { testTag = "reader_snackbar_host" },
      snackbar = { data -> Snackbar(snackbarData = data) },
    )
  }
}
