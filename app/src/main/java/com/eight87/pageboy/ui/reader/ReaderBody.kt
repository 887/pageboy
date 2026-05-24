package com.eight87.pageboy.ui.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import com.eight87.pageboy.R
import com.eight87.pageboy.domain.render.RendererContext
import com.eight87.pageboy.domain.render.RendererReadingPrefs
import com.eight87.pageboy.format.registry.FormatRegistry
import com.eight87.pageboy.ui.reader.control.InMemoryFindInDocCommands
import com.eight87.pageboy.ui.reader.control.ReaderState
import com.eight87.pageboy.ui.reader.control.ScrollPersistence
import com.eight87.pageboy.ui.reader.control.buildRendererContext

/**
 * Phase C.5 — body slot. Branches on the sealed [ReaderState] (R.X.2) +
 * dispatches the renderer's `Body()` via the [FormatRegistry] (R.X.9).
 *
 * No `when (format)` switch here — the registry returns whichever
 * renderer was wired in [com.eight87.pageboy.AppGraph].
 *
 * Phase E.3 — when the state is [ReaderState.Open] the body builds a
 * [RendererContext] from the chrome handles (scroll persistence + the
 * concrete find-commands instance for this reader + reading prefs) and
 * threads it into the renderer's `Body(...)`. Future renderer handles
 * (annotation commands at Phase G, signature commands at Phase H) join
 * via additional fields on [RendererContext], not by widening this
 * call site.
 */
@Composable
internal fun ReaderBody(
  state: ReaderState,
  formatRegistry: FormatRegistry,
  documentId: String,
  scrollPersistence: ScrollPersistence,
  findCommands: InMemoryFindInDocCommands,
  readingPrefs: RendererReadingPrefs,
  onRetry: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Box(
    modifier = modifier
      .fillMaxSize()
      .semantics { testTag = "reader_body" },
  ) {
    when (state) {
      is ReaderState.Idle -> {
        // Nothing to render. Shouldn't be visible long; the projector
        // flips to Opening immediately on the screen's first compose.
      }
      is ReaderState.Opening -> {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .semantics { testTag = "reader_opening" },
          contentAlignment = Alignment.Center,
        ) {
          CircularProgressIndicator()
        }
      }
      is ReaderState.Open -> {
        val handle = state.handle
        val renderer = formatRegistry.rendererFor(handle.format)
        val context = remember(documentId, scrollPersistence, findCommands, readingPrefs) {
          buildRendererContext(
            documentId = documentId,
            scrollPersistence = scrollPersistence,
            findCommands = findCommands,
            readingPrefs = readingPrefs,
          )
        }
        renderer.Body(handle = handle, context = context, modifier = Modifier.fillMaxSize())
      }
      is ReaderState.Failed -> {
        ReaderErrorState(
          reason = state.reason,
          onRetry = onRetry,
        )
      }
    }
    // Tiny accessible label for the opening / idle states so the test
    // tree always has something meaningful.
    if (state is ReaderState.Opening) {
      Text(
        text = stringResource(R.string.reader_opening_label),
        modifier = Modifier
          .align(Alignment.BottomCenter)
          .semantics { testTag = "reader_opening_label" },
      )
    }
  }
}
