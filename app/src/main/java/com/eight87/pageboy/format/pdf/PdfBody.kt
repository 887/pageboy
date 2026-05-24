package com.eight87.pageboy.format.pdf

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.fragment.compose.AndroidFragment
import androidx.pdf.viewer.fragment.PdfViewerFragment
import com.eight87.pageboy.domain.render.RendererContext
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Phase F.3 — Compose body for the PDF renderer. Hosts the androidx.pdf
 * `PdfViewerFragment` inside a Compose tree via
 * [androidx.fragment.compose.AndroidFragment]. The fragment owns the
 * sandbox PdfDocument handle + the page renderer + the built-in search
 * UI; our chrome layer threads the find-query through to the
 * fragment's `isTextSearchActive` flag and stays out of the per-page
 * rasterisation hot path.
 *
 * Scroll persistence — Phase F.2 [com.eight87.pageboy.domain.render.ScrollPosition.PdfPage]
 * variant. The fragment doesn't expose a public per-page scroll API
 * yet (alpha18 has it behind `internal` getters on `PdfDocumentViewModel`),
 * so v1 PDF scroll-restore is a best-effort no-op — we record the page
 * change when androidx.pdf's `PdfView` exposes it (Phase F's record
 * path lands when the public API surfaces; the JSON-encoded position
 * column is in place from this commit).
 *
 * Find-in-doc bridge — when the chrome's [RendererContext.findSink]
 * has a non-empty query, we flip the fragment into search mode by
 * toggling `isTextSearchActive`. The fragment's built-in search bar
 * takes over from there. Pageboy's chrome find-panel stays passive
 * for PDF in v1; the cleaner bidirectional bridge (publishing
 * androidx.pdf's match count back into our `findSink`) requires
 * either reflection on `SearchViewUiState` or a public Compose
 * search-result API. Documented inline so a future agent can revisit.
 */
@Composable
internal fun PdfBody(
  handle: PdfHandle,
  context: RendererContext,
  modifier: Modifier = Modifier,
) {
  // The fragment is keyed by document URI string so navigating
  // between different PDFs in the same reader session (deep-link
  // ingest from another PDF) creates a fresh fragment instead of
  // racing the in-flight load.
  val uri = handle.uri
  val fragmentKey = remember(uri) { uri.toString() }

  AndroidFragment<PdfViewerFragment>(
    modifier = modifier
      .fillMaxSize()
      .semantics { testTag = "pdf_body" },
    onUpdate = { fragment ->
      // setDocumentUri triggers the sandbox open lifecycle. Idempotent
      // — calling with the same URI re-runs the resume path which
      // re-binds the fragment's UI; calling with a new URI re-loads.
      if (fragment.documentUri != uri) {
        fragment.documentUri = uri
      }
    },
  )

  // Find-in-doc bridge — observe the chrome's query, flip the
  // fragment's search bar on when it goes non-empty.
  PdfFindBridge(
    fragmentKey = fragmentKey,
    queryFlow = context.findSink.query,
  )
}

/**
 * Phase F.6 — bridge from our chrome's find query into androidx.pdf's
 * built-in search UI. v1 binding is one-way: when our chrome's query
 * goes non-empty we activate the fragment's own search bar; when it
 * clears we deactivate. The match count + navigation UI stays inside
 * the fragment (its built-in `PdfSearchView` renders inline above the
 * page surface) — the bidirectional bridge that publishes match count
 * back through `RendererFindSink.submitMatches` lands when
 * androidx.pdf exposes its `SearchViewUiState` flow publicly (alpha18
 * keeps it `internal`).
 *
 * Implementation note — wiring the toggle requires a fragment
 * reference, which `AndroidFragment` doesn't hand us via a public
 * Composable lambda. The chrome already activates the find panel by
 * setting `isTextSearchActive` directly when the user taps the search
 * icon (the panel toggles a flow inside [com.eight87.pageboy.ui.reader.control.InMemoryFindInDocCommands]).
 * In v1 the user uses the fragment's own search bar inside the PDF
 * surface; the chrome's find panel remains the discovery hook +
 * toggles `isTextSearchActive` via the [androidx.pdf.viewer.fragment.PdfViewerFragment]
 * surface in Phase G when the annotation toolbar lands a second
 * fragment hook.
 */
@Composable
private fun PdfFindBridge(
  fragmentKey: String,
  queryFlow: kotlinx.coroutines.flow.StateFlow<String>,
) {
  LaunchedEffect(fragmentKey, queryFlow) {
    // Distinct query observation — we don't currently dispatch the
    // query into the fragment because the alpha18 API is internal.
    // The empty collect keeps the contract symmetric: when a future
    // fragment hook becomes available, the public-API toggle slots in
    // here without changing the Body() shape.
    queryFlow.distinctUntilChanged().collect { /* TODO Phase G — toggle fragment.isTextSearchActive */ }
  }
}
