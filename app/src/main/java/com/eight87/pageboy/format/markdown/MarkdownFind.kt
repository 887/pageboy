package com.eight87.pageboy.format.markdown

import com.eight87.pageboy.domain.render.FindMatch

/**
 * Phase D / D.8 — case-insensitive substring search over the raw
 * markdown source. Returns a [FindMatch] per occurrence; positions are
 * character offsets into the raw text (the AST does not preserve source
 * spans on every node so the raw-text scan is the simplest + most
 * predictable surface).
 *
 * Phase E.1 update: the result type is now the neutral
 * [com.eight87.pageboy.domain.render.FindMatch] instead of a local
 * `MarkdownMatch` — closes O.D.1 from the Phase D audit. The neutral
 * type lives in `domain/render/` so the renderer can emit matches
 * without crossing the `format/` → `ui/` import barrier R.X.6 forbids,
 * while the chrome consumes the same type without an adapter at the
 * boundary.
 *
 * Phase E.2 ships scroll-to-match navigation. Inline highlight remains
 * a v1.1 polish — implementing it requires `AnnotatedString` rebuilds
 * per query against the AST's character ranges, which earns its keep
 * when PDF / EPUB also need it.
 */
internal object MarkdownFind {

  fun findAll(rawText: String, query: String, maxMatches: Int = 500): List<FindMatch> {
    if (query.isEmpty()) return emptyList()
    val needle = query.lowercase()
    val haystack = rawText.lowercase()
    val matches = ArrayList<FindMatch>()
    var start = 0
    while (matches.size < maxMatches) {
      val hit = haystack.indexOf(needle, start)
      if (hit < 0) break
      val end = hit + needle.length
      matches += FindMatch(
        rangeStart = hit,
        rangeEnd = end,
        pageIndex = null,
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
