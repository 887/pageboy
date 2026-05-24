package com.eight87.pageboy.ui.reader.control

import com.eight87.pageboy.data.settings.ReaderSettings
import com.eight87.pageboy.domain.render.RendererContext
import com.eight87.pageboy.domain.render.RendererFindSink
import com.eight87.pageboy.domain.render.RendererReadingPrefs
import com.eight87.pageboy.domain.render.RendererScrollSink
import com.eight87.pageboy.domain.render.ScrollPosition
import com.eight87.pageboy.domain.render.FindMatch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * Phase E.1 — chrome-side adapters that present the chrome's existing
 * per-axis controllers as the narrow renderer-facing surfaces in
 * `domain/render/`.
 *
 * The chrome's [ScrollPersistence] / [FindInDocCommands] / [ReaderSettings]
 * stay rich (they carry the chrome's own ergonomic surface, e.g.
 * `next()` / `previous()` / `clear()`); the renderer-facing
 * [RendererScrollSink] / [RendererFindSink] / [RendererReadingPrefs] are
 * the thin contracts a renderer's `Body()` actually consumes (R.X.7 ISP).
 *
 * The adapter layer is the only place that crosses the chrome /
 * renderer interface — both can import `domain/render/`, neither
 * crosses into the other (R.X.6).
 */
class DefaultRendererScrollSink(
  private val documentId: String,
  private val persistence: ScrollPersistence,
) : RendererScrollSink {

  override suspend fun load(): ScrollPosition? = persistence.lastPosition(documentId)

  override fun record(position: ScrollPosition) {
    persistence.recordPosition(documentId, position)
  }
}

/**
 * Adapter that exposes the chrome's [InMemoryFindInDocCommands] as a
 * [RendererFindSink]. The renderer-side write path goes through
 * [submitMatches] on the concrete chrome class; this adapter forwards
 * to it.
 */
class DefaultRendererFindSink(
  private val commands: InMemoryFindInDocCommands,
) : RendererFindSink {

  override val query: StateFlow<String> get() = commands.query
  override val currentMatchIndex: StateFlow<Int> get() = commands.currentMatchIndex

  override fun submitMatches(matches: List<FindMatch>) {
    commands.submitMatches(matches)
  }
}

/**
 * Adapter that exposes [ReaderSettings] facet flows as a narrow
 * read-only [RendererReadingPrefs] for renderers. Each preference is
 * projected as a `StateFlow<Boolean>` via `stateIn` on the supplied
 * application scope so renderers do not subscribe to DataStore Flows
 * directly.
 */
class DefaultRendererReadingPrefs(
  scope: CoroutineScope,
  settings: ReaderSettings,
) : RendererReadingPrefs {

  // Default is the same `true` the chrome settings facet declares.
  override val continuousScrolling: StateFlow<Boolean> =
    settings.continuousScrolling.flow
      .stateIn(scope, SharingStarted.Eagerly, initialValue = true)
}

/**
 * Convenience factory that builds the per-document [RendererContext]
 * the chrome threads into `DocumentRenderer.Body(...)`.
 */
fun buildRendererContext(
  documentId: String,
  scrollPersistence: ScrollPersistence,
  findCommands: InMemoryFindInDocCommands,
  readingPrefs: RendererReadingPrefs,
): RendererContext = RendererContext(
  documentId = documentId,
  scrollSink = DefaultRendererScrollSink(documentId, scrollPersistence),
  findSink = DefaultRendererFindSink(findCommands),
  readingPrefs = readingPrefs,
)

/**
 * No-op adapters used by Robolectric Compose smoke tests that don't
 * need scroll-restore / find-in-doc. Lets a renderer's `Body()` test
 * stay tight without spinning up DataStore + Room.
 */
class NoopRendererScrollSink : RendererScrollSink {
  override suspend fun load(): ScrollPosition? = null
  override fun record(position: ScrollPosition) {}
}

class NoopRendererFindSink : RendererFindSink {
  private val q = MutableStateFlow("")
  private val idx = MutableStateFlow(-1)
  override val query: StateFlow<String> = q.asStateFlow()
  override val currentMatchIndex: StateFlow<Int> = idx.asStateFlow()
  override fun submitMatches(matches: List<FindMatch>) {}
}

class NoopRendererReadingPrefs : RendererReadingPrefs {
  override val continuousScrolling: StateFlow<Boolean> =
    MutableStateFlow(true).asStateFlow()
}

/**
 * Test/preview helper — assembles a no-op [RendererContext]. Real
 * production wiring goes through [buildRendererContext].
 */
fun noopRendererContext(documentId: String = "noop"): RendererContext = RendererContext(
  documentId = documentId,
  scrollSink = NoopRendererScrollSink(),
  findSink = NoopRendererFindSink(),
  readingPrefs = NoopRendererReadingPrefs(),
)
