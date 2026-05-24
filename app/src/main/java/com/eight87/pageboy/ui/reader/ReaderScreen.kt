package com.eight87.pageboy.ui.reader

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eight87.pageboy.data.library.DocumentFormat
import com.eight87.pageboy.domain.render.RendererReadingPrefs
import com.eight87.pageboy.format.registry.FormatRegistry
import com.eight87.pageboy.ui.reader.control.InMemoryFindInDocCommands
import com.eight87.pageboy.ui.reader.control.InMemorySigningCommands
import com.eight87.pageboy.ui.reader.control.ReaderState
import com.eight87.pageboy.ui.reader.control.ReaderStateProjector
import com.eight87.pageboy.ui.reader.control.ScrollPersistence
import com.eight87.pageboy.ui.reader.control.ShareExportCommands
import com.eight87.pageboy.ui.reader.signing.SigningSheet

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
  signingCommands: InMemorySigningCommands? = null,
  modifier: Modifier = Modifier,
) {
  val state by readerStateProjector.state.collectAsStateWithLifecycle()
  val findQuery by findInDocCommands.query.collectAsStateWithLifecycle()
  val findMatches by findInDocCommands.matches.collectAsStateWithLifecycle()
  val findCurrent by findInDocCommands.currentMatchIndex.collectAsStateWithLifecycle()

  var findPanelOpen by remember(documentId) { mutableStateOf(false) }

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

  // Phase M.7 — capability-gated ToC entry. The chrome reads the
  // narrow `DocumentHandle.tocAvailable` flag exposed by the resolved
  // handle (defaults to false on every renderer other than EPUB).
  val tocAvailable = (state as? ReaderState.Open)?.handle?.tocAvailable == true
  // Phase H — sign overflow shows up for PDF only. Other formats keep
  // the Phase C "no actions yet" placeholder in the overflow menu.
  val isPdfOpen = (state as? ReaderState.Open)?.handle?.format == DocumentFormat.Pdf
  val showSign = isPdfOpen && signingCommands != null

  Column(
    modifier = modifier
      .fillMaxSize()
      .windowInsetsPadding(WindowInsets.systemBars)
      .semantics { testTag = "reader_screen" },
  ) {
    ReaderTopBar(
      title = title,
      findActive = findPanelOpen,
      onBack = onBack,
      tocAvailable = tocAvailable,
      onOpenToc = {
        // Phase M.7 — v1 surfaces the affordance but does not yet
        // host a ToC sheet (the navigator-side `go(locator)` wiring
        // for tap-to-jump lives inside the format/epub/ package and
        // needs a chrome ↔ renderer command bridge that's heavier
        // than fits this phase). The capability is correctly gated;
        // the user-visible behaviour completes in a follow-on phase.
      },
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
      showSignAction = showSign,
      onSign = {
        // Phase H — single entry point. The sheet's first sub-page
        // picks visual vs cryptographic. Default is the cryptographic
        // path (key-selecting); the visual-stamp path is the first
        // sub-page of the sheet itself, not a separate entry.
        signingCommands?.start(visualStamp = false)
      },
    )
    if (signingCommands != null) {
      SigningSheet(
        commands = signingCommands,
        onPickKeystore = { /* AppGraph adapter wires the casual path */ },
        onPickPkcs12 = { /* SAF picker + Pkcs12KeyProvider land in the activity */ },
      )
    }
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
}
