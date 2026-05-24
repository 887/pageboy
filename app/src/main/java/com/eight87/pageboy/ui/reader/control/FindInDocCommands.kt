package com.eight87.pageboy.ui.reader.control

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Phase C.4 / R.C — find-in-document state + commands. Phase C ships
 * the contract; real per-format match emission lands per renderer
 * (Phase D for Markdown, Phase F for PDF, etc.).
 *
 * Narrow surface (R.X.1): the find panel reads [query] + [matches] +
 * [currentMatchIndex], writes through [setQuery] / [next] / [previous] /
 * [clear]. Each per-format renderer wires its own search by observing
 * [query] and emitting its match list back through an impl-side setter.
 *
 * Per-document scoped — one [FindInDocCommands] per open document
 * (lifecycle matches the reader screen instance, not the AppGraph).
 */
interface FindInDocCommands {

  val query: StateFlow<String>
  val matches: StateFlow<List<FindMatch>>
  val currentMatchIndex: StateFlow<Int>

  fun setQuery(query: String)
  fun next()
  fun previous()
  fun clear()
}

/**
 * Single match the renderer surfaced. Generic across formats —
 * [pageIndex] is null for reflowable formats; [contextSnippet] is the
 * short surrounding-text hint the find panel can render in a results
 * list. Phase C doesn't surface a results list; the contract leaves
 * room for one.
 */
data class FindMatch(
  val rangeStart: Int,
  val rangeEnd: Int,
  val pageIndex: Int? = null,
  val contextSnippet: String? = null,
)

/**
 * Default in-memory [FindInDocCommands]. The reader chrome wires this
 * per open document; renderers push their match lists back via the
 * [submitMatches] hook (which is not on the public interface — chrome
 * sees the read-only [FindInDocCommands] only).
 */
class InMemoryFindInDocCommands : FindInDocCommands {

  private val _query = MutableStateFlow("")
  override val query: StateFlow<String> = _query.asStateFlow()

  private val _matches = MutableStateFlow<List<FindMatch>>(emptyList())
  override val matches: StateFlow<List<FindMatch>> = _matches.asStateFlow()

  private val _current = MutableStateFlow(-1)
  override val currentMatchIndex: StateFlow<Int> = _current.asStateFlow()

  override fun setQuery(query: String) {
    _query.value = query
    if (query.isEmpty()) {
      _matches.value = emptyList()
      _current.value = -1
    }
  }

  override fun next() {
    val total = _matches.value.size
    if (total == 0) return
    _current.value = ((_current.value + 1).coerceAtLeast(0)) % total
  }

  override fun previous() {
    val total = _matches.value.size
    if (total == 0) return
    val next = _current.value - 1
    _current.value = if (next < 0) total - 1 else next
  }

  override fun clear() {
    _query.value = ""
    _matches.value = emptyList()
    _current.value = -1
  }

  /**
   * Renderer-side hook to publish new matches for the current query.
   * Not part of the [FindInDocCommands] interface — only the per-format
   * renderer sees this concrete class. Phase D+ wires it up; Phase C
   * leaves matches empty.
   */
  fun submitMatches(matches: List<FindMatch>) {
    _matches.value = matches
    _current.value = if (matches.isEmpty()) -1 else 0
  }
}
