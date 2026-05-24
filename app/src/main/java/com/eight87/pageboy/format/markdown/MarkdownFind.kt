package com.eight87.pageboy.format.markdown

/**
 * Phase D / D.8 — case-insensitive substring search over the raw
 * markdown source. Returns a [MarkdownMatch] per occurrence; positions
 * are character offsets into the raw text (the AST does not preserve
 * source spans on every node so the raw-text scan is the simplest +
 * most predictable surface).
 *
 * The result type lives here in `format/markdown/` (NOT
 * `ui.reader.control.FindMatch`) per R.X.6 — the format layer doesn't
 * import the ui layer. When the chrome wires find-in-doc through to
 * the renderer in a later phase it will adapt these matches to its
 * own `FindMatch` type at the boundary.
 *
 * Highlight rendering is the v1.1 polish work — Phase D ships
 * navigation without per-occurrence visual highlight (the user can read
 * the context snippet in the find panel; tapping next/prev moves the
 * focus). Implementing inline highlight via an `AnnotatedString`
 * rebuild per query needs to traverse the AST + cross-reference
 * ranges, which is its own surface that earns its keep when other
 * formats (PDF, EPUB) also need it.
 */
internal object MarkdownFind {

  data class MarkdownMatch(
    val rangeStart: Int,
    val rangeEnd: Int,
    val contextSnippet: String,
  )

  fun findAll(rawText: String, query: String, maxMatches: Int = 500): List<MarkdownMatch> {
    if (query.isEmpty()) return emptyList()
    val needle = query.lowercase()
    val haystack = rawText.lowercase()
    val matches = ArrayList<MarkdownMatch>()
    var start = 0
    while (matches.size < maxMatches) {
      val hit = haystack.indexOf(needle, start)
      if (hit < 0) break
      val end = hit + needle.length
      matches += MarkdownMatch(
        rangeStart = hit,
        rangeEnd = end,
        contextSnippet = snippet(rawText, hit, end),
      )
      start = end
    }
    return matches
  }

  private fun snippet(text: String, start: Int, end: Int, around: Int = 24): String {
    val from = (start - around).coerceAtLeast(0)
    val to = (end + around).coerceAtMost(text.length)
    val prefix = if (from > 0) "…" else ""
    val suffix = if (to < text.length) "…" else ""
    return prefix + text.substring(from, to).replace('\n', ' ') + suffix
  }
}
