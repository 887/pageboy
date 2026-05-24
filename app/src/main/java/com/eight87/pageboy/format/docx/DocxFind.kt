package com.eight87.pageboy.format.docx

import com.eight87.pageboy.domain.render.FindMatch
import com.eight87.pageboy.format.docx.internal.RichTextBlock
import com.eight87.pageboy.format.docx.internal.RichTextRun
import com.eight87.pageboy.format.docx.internal.TableCell

/**
 * Phase I.6 — find-in-doc for DOCX. Walks the parsed [RichTextBlock]
 * list, lowering each block to its plain-text projection, then runs
 * case-insensitive substring search.
 *
 * Each match carries the top-level block index in [FindMatch.rangeStart]
 * so the renderer can `animateScrollToItem` directly. [FindMatch.rangeEnd]
 * is the within-block char offset of the hit start — surfaced for v1.1
 * inline highlight (the chrome's find panel only consumes the count +
 * jump in v1).
 *
 * Emits neutral [com.eight87.pageboy.domain.render.FindMatch] so the
 * chrome consumes it without an adapter at the boundary (same pattern
 * Markdown + TXT use).
 */
internal object DocxFind {

  fun findAll(blocks: List<RichTextBlock>, query: String, maxMatches: Int = 500): List<FindMatch> {
    if (query.isEmpty()) return emptyList()
    val needle = query.lowercase()
    val matches = ArrayList<FindMatch>()
    for ((index, block) in blocks.withIndex()) {
      if (matches.size >= maxMatches) break
      val plain = blockPlainText(block)
      val lower = plain.lowercase()
      var from = 0
      while (matches.size < maxMatches) {
        val hit = lower.indexOf(needle, from)
        if (hit < 0) break
        matches += FindMatch(
          rangeStart = index,
          rangeEnd = hit,
          pageIndex = null,
          contextSnippet = snippet(plain, hit, hit + needle.length),
        )
        from = hit + needle.length
      }
    }
    return matches
  }

  /**
   * Lower one block to a flat string for substring matching. Tables
   * flatten to space-separated cell text; lists join their items with
   * newlines.
   */
  internal fun blockPlainText(block: RichTextBlock): String = when (block) {
    is RichTextBlock.Paragraph -> runsText(block.runs)
    is RichTextBlock.Heading -> runsText(block.runs)
    is RichTextBlock.BlockQuote -> runsText(block.runs)
    is RichTextBlock.BulletList -> block.items.joinToString("\n") { runsText(it) }
    is RichTextBlock.NumberedList -> block.items.joinToString("\n") { runsText(it) }
    is RichTextBlock.Table -> block.rows.joinToString("\n") { row ->
      row.joinToString(" ") { cell: TableCell -> runsText(cell.runs) }
    }
    is RichTextBlock.ImagePlaceholder -> block.altText
    is RichTextBlock.TextBox -> runsText(block.runs)
    is RichTextBlock.Placeholder -> block.label
    is RichTextBlock.SectionBreak -> ""
  }

  private fun runsText(runs: List<RichTextRun>): String = buildString {
    for (run in runs) {
      when (run) {
        is RichTextRun.Text -> append(run.value)
        is RichTextRun.Hyperlink -> append(run.text)
        is RichTextRun.FieldCode -> append(run.cachedText)
        is RichTextRun.Tab -> append('\t')
        is RichTextRun.SoftBreak -> append('\n')
      }
    }
  }

  private fun snippet(text: String, start: Int, end: Int, around: Int = 24): String {
    val from = (start - around).coerceAtLeast(0)
    val to = (end + around).coerceAtMost(text.length)
    val prefix = if (from > 0) "…" else ""
    val suffix = if (to < text.length) "…" else ""
    return prefix + text.substring(from, to).replace('\n', ' ') + suffix
  }
}
