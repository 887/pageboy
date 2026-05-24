package com.eight87.pageboy.domain.render

import kotlinx.coroutines.flow.StateFlow

/**
 * Phase E.1 — renderer-facing chrome handles bundle. Threaded into
 * [com.eight87.pageboy.format.api.DocumentRenderer.Body] so per-format
 * renderers can wire scroll-position restore + find-in-doc + reading
 * prefs without the chrome reaching into renderer internals (and
 * without the renderer reaching into the chrome's concrete controllers).
 *
 * Closes Phase D audit observations O.D.1 + O.D.3 — the markdown
 * renderer can now consume both [scrollSink] and [findSink] through
 * stable narrow interfaces, while staying inside the `format/` package
 * (no `format/` → `ui/` import).
 *
 * **Design tradeoff:** picked a single value type over expanding the
 * `Body()` parameter list so adding future renderer handles (annotation
 * commands at Phase G, signature commands at Phase H) is one field on
 * this data class — not another widening of every renderer's signature.
 * Each renderer reads only the fields it needs (R.X.7 ISP within
 * Compose — small read-only contract, no god-state).
 *
 * The fields ship as nullable / no-op-tolerant interfaces from the
 * chrome's adapter layer, so unit-test fakes can pass a minimal context
 * (see `NoopRendererContext` test helpers).
 */
data class RendererContext(
  val documentId: String,
  val scrollSink: RendererScrollSink,
  val findSink: RendererFindSink,
  val readingPrefs: RendererReadingPrefs,
)

/**
 * Narrow renderer-facing surface over the chrome's `ScrollPersistence`.
 * Renderers read [load] once on first compose to restore the saved
 * position; they call [record] on every scroll-stop tick (debounced
 * inside the chrome's impl).
 */
interface RendererScrollSink {
  suspend fun load(): ScrollPosition?
  fun record(position: ScrollPosition)
}

/**
 * Narrow renderer-facing surface over the chrome's `FindInDocCommands`.
 *
 * Renderers observe [query] to know what to search for, observe
 * [currentMatchIndex] to know which match to scroll to, and call
 * [submitMatches] when they've run their per-format search against the
 * latest query. The chrome's `FindInDocCommands` interface stays
 * read-only on the chrome side — only the adapter pushed into here can
 * write matches back.
 */
interface RendererFindSink {
  val query: StateFlow<String>
  val currentMatchIndex: StateFlow<Int>
  fun submitMatches(matches: List<FindMatch>)
}

/**
 * Narrow renderer-facing read-only view of reader-side reading
 * preferences. Each renderer reads only the fields it cares about
 * (Markdown ignores `continuousScrolling` in v1, TXT honours wrap mode,
 * PDF at Phase F will read `continuousScrolling` for the paged/scroll
 * toggle).
 *
 * Stays a narrow value type (R.X.1 — no god `SettingsRepository`
 * threaded into renderers).
 */
interface RendererReadingPrefs {
  val continuousScrolling: StateFlow<Boolean>
}
