package com.eight87.pageboy.format.odt

import com.eight87.pageboy.domain.render.FindMatch

/**
 * Phase K.6 — find-in-doc for the ODT renderer. Walks the parsed block
 * model collecting case-insensitive substring matches against the
 * flattened text of each top-level block. `rangeStart` carries the
 * block index so [OdtBody] can `scrollToItem` directly; `rangeEnd` is
 * the same value (v1 doesn't draw per-character highlight — the panel
 * surfaces count + jump only).
 *
 * Tables expand to their cell contents; lists expand to their items;
 * embedded placeholders carry their `kind` label so a search for
 * "chart" still finds the placeholder's surface text. Same shape as
 * the markdown / txt finders so the chrome consumes results without an
 * adapter at the boundary.
 */
internal object OdtFind {

  fun findAll(blocks: List<OdfTextBlock>, query: String, maxMatches: Int = 500): List<FindMatch> {
    if (query.isEmpty() || blocks.isEmpty()) return emptyList()
    val needle = query.lowercase()
    val out = ArrayList<FindMatch>()
    blocks.forEachIndexed { index, block ->
      if (out.size >= maxMatches) return out
      val text = blockText(block)
      val lower = text.lowercase()
      var from = 0
      while (out.size < maxMatches) {
        val hit = lower.indexOf(needle, from)
        if (hit < 0) break
        out += FindMatch(
          rangeStart = index,
          rangeEnd = index,
          pageIndex = null,
          contextSnippet = snippet(text, hit, hit + needle.length),
        )
        from = hit + needle.length
      }
    }
    return out
  }

  internal fun blockText(block: OdfTextBlock): String = when (block) {
    is OdfTextBlock.Paragraph -> block.runs.joinToString(separator = "") { it.text }
    is OdfTextBlock.Heading -> block.runs.joinToString(separator = "") { it.text }
    is OdfTextBlock.ListBlock -> block.items.joinToString(separator = "\n") { item ->
      item.joinToString(separator = "\n") { blockText(it) }
    }
    is OdfTextBlock.Table -> block.rows.joinToString(separator = "\n") { row ->
      row.joinToString(separator = "\t") { cell ->
        cell.joinToString(separator = " ") { blockText(it) }
      }
    }
    is OdfTextBlock.EmbeddedPlaceholder -> "[${block.kind}]"
  }

  private fun snippet(text: String, start: Int, end: Int, around: Int = 24): String {
    val from = (start - around).coerceAtLeast(0)
    val to = (end + around).coerceAtMost(text.length)
    val prefix = if (from > 0) "…" else ""
    val suffix = if (to < text.length) "…" else ""
    return prefix + text.substring(from, to) + suffix
  }
}
