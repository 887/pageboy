package com.eight87.pageboy.format.txt

import com.eight87.pageboy.domain.render.FindMatch

/**
 * Phase E.4 — case-insensitive find-in-document for the TXT renderer.
 * Mirrors `MarkdownFind` shape; emits the neutral
 * [com.eight87.pageboy.domain.render.FindMatch] type so the chrome
 * consumes matches without an adapter at the boundary (closes O.D.1
 * for TXT in lockstep with the Phase E.1 Markdown migration).
 *
 * Returns up to [maxMatches] hits across the whole document. Each
 * match carries the absolute line index in [FindMatch.rangeStart] so
 * the renderer can scroll the `LazyColumn` to it directly — TXT's
 * coordinate space is "line index", not "character offset", so we lean
 * on `rangeStart` as the line number and leave `rangeEnd = rangeStart`
 * (the v1 renderer doesn't draw per-character highlight; the find
 * panel surfaces the count + jump).
 */
internal object TxtFind {

  fun findAll(source: TxtLineSource, query: String, maxMatches: Int = 500): List<FindMatch> {
    if (query.isEmpty()) return emptyList()
    val needle = query.lowercase()
    val matches = ArrayList<FindMatch>()
    var i = 0
    while (i < source.lineCount && matches.size < maxMatches) {
      val line = source.lineAt(i)
      val lower = line.lowercase()
      var from = 0
      while (matches.size < maxMatches) {
        val hit = lower.indexOf(needle, from)
        if (hit < 0) break
        matches += FindMatch(
          rangeStart = i,
          rangeEnd = i,
          pageIndex = null,
          contextSnippet = snippet(line, hit, hit + needle.length),
        )
        from = hit + needle.length
      }
      i++
    }
    return matches
  }

  private fun snippet(line: String, start: Int, end: Int, around: Int = 24): String {
    val from = (start - around).coerceAtLeast(0)
    val to = (end + around).coerceAtMost(line.length)
    val prefix = if (from > 0) "…" else ""
    val suffix = if (to < line.length) "…" else ""
    return prefix + line.substring(from, to) + suffix
  }
}
