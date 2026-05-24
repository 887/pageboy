package com.eight87.pageboy.ui.reader

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eight87.pageboy.data.annotation.AnnotationSource
import com.eight87.pageboy.domain.render.RendererReadingPrefs
import com.eight87.pageboy.domain.render.annotation.AnnotationCommands
import com.eight87.pageboy.format.pdf.PdfHandle
import com.eight87.pageboy.format.pdf.export.PdfAnnotationExporter
import com.eight87.pageboy.format.registry.FormatRegistry
import com.eight87.pageboy.ui.reader.control.InMemoryFindInDocCommands
import com.eight87.pageboy.ui.reader.control.ReaderState
import com.eight87.pageboy.ui.reader.control.ReaderStateProjector
import com.eight87.pageboy.ui.reader.control.ScrollPersistence
import com.eight87.pageboy.ui.reader.control.ShareExportCommands
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
  annotationCommands: AnnotationCommands? = null,
  annotationSource: AnnotationSource? = null,
  pdfAnnotationExporter: PdfAnnotationExporter? = null,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val state by readerStateProjector.state.collectAsStateWithLifecycle()
  val findQuery by findInDocCommands.query.collectAsStateWithLifecycle()
  val findMatches by findInDocCommands.matches.collectAsStateWithLifecycle()
  val findCurrent by findInDocCommands.currentMatchIndex.collectAsStateWithLifecycle()

  var findPanelOpen by remember(documentId) { mutableStateOf(false) }
  val scope = rememberCoroutineScope()
  val ctx = LocalContext.current

  // Phase G.6 — SAF "save as" launcher for the export-with-annotations
  // path. Mime type application/pdf so the system picker steers the
  // user toward a sensible default location + extension.
  val openHandle = (state as? ReaderState.Open)?.handle
  val pdfHandle = openHandle as? PdfHandle
  val canExport = pdfHandle != null &&
    annotationSource != null &&
    pdfAnnotationExporter != null

  val exportLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.CreateDocument("application/pdf"),
  ) { destUri: Uri? ->
    val pdf = pdfHandle ?: return@rememberLauncherForActivityResult
    val src = pdf.uri
    val out = destUri ?: return@rememberLauncherForActivityResult
    val exporter = pdfAnnotationExporter ?: return@rememberLauncherForActivityResult
    val annoSource = annotationSource ?: return@rememberLauncherForActivityResult
    scope.launch {
      withContext(Dispatchers.IO) {
        runCatching {
          val rows = annoSource.list(documentId)
          val cr = ctx.contentResolver
          cr.openInputStream(src)?.use { inStream ->
            cr.openOutputStream(out)?.use { outStream ->
              exporter.export(inStream, outStream, rows)
            }
          }
        }
      }
    }
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
      onExportWithAnnotations = if (canExport) ({
        val suggested = (pdfHandle?.title ?: "pageboy-export") + "-annotated.pdf"
        exportLauncher.launch(suggested)
      }) else null,
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
      annotationCommands = annotationCommands,
      annotationSource = annotationSource,
      onRetry = { readerStateProjector.open(documentId) },
      modifier = Modifier.fillMaxSize(),
    )
  }
}
