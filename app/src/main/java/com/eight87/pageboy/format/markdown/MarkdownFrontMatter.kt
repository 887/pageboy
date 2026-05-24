package com.eight87.pageboy.format.markdown

/**
 * Phase D — DIY YAML front-matter sniffer. Mirrors the format-markdown.md
 * decision to skip `commonmark-ext-yaml-front-matter` (it pulls in
 * snakeyaml-engine, ~150 KB minified). We do not need to parse the YAML
 * here — we only need to strip it from the body before handing it to
 * commonmark and expose the raw lines to whatever later phase wants to
 * read Obsidian metadata.
 *
 * Recognises the canonical Jekyll / Hugo / Obsidian fence:
 *
 *     ---
 *     key: value
 *     other: 42
 *     ---
 *     # Document body starts here
 *
 * The fence must be the very first thing in the file (after an optional
 * UTF-8 BOM). The closing fence must appear on its own line, at column 0.
 * Both `---` and `...` (YAML stream-end marker) close the block.
 *
 * Anything that does not match the canonical shape passes through
 * untouched — false-positive stripping of `---` thematic breaks would be
 * worse than missing a malformed front-matter block.
 */
internal object MarkdownFrontMatter {

  data class Split(
    val frontMatter: Map<String, String>,
    val body: String,
  )

  private const val FENCE = "---"
  private const val FENCE_ALT = "..."

  fun split(raw: String): Split {
    val text = raw.removePrefix("﻿")
    if (!startsWithFence(text)) return Split(emptyMap(), raw)

    // Position immediately past the opening fence + its newline.
    val firstLineEnd = text.indexOf('\n', 0)
    if (firstLineEnd < 0) return Split(emptyMap(), raw)

    val lines = text.lineSequence().drop(1).toList()
    var closeIndex = -1
    for ((i, line) in lines.withIndex()) {
      val trimmed = line.trimEnd()
      if (trimmed == FENCE || trimmed == FENCE_ALT) {
        closeIndex = i
        break
      }
    }
    if (closeIndex < 0) return Split(emptyMap(), raw)

    val fmLines = lines.subList(0, closeIndex)
    val bodyLines = lines.subList(closeIndex + 1, lines.size)
    val fm = parseSimplePairs(fmLines)
    val body = bodyLines.joinToString("\n")
    return Split(fm, body)
  }

  private fun startsWithFence(text: String): Boolean {
    if (text.length < FENCE.length) return false
    if (!text.startsWith(FENCE)) return false
    // The line must end right after the fence (possibly with trailing
    // whitespace) — `---foo` is NOT a fence.
    val afterFence = text.substring(FENCE.length)
    val firstNewline = afterFence.indexOf('\n')
    val firstLineRest = if (firstNewline < 0) afterFence else afterFence.substring(0, firstNewline)
    return firstLineRest.all { it == ' ' || it == '\t' || it == '\r' }
  }

  /**
   * Best-effort key/value parser. Recognises `key: value` and
   * `key: "value"`; ignores blank lines and comments. Anything more
   * exotic (nested mappings, arrays, multi-line scalars) is intentionally
   * dropped — this is a sniffer for the Obsidian / Jekyll "metadata
   * preamble" idiom, not a YAML implementation. Downstream consumers
   * that need real YAML can re-parse `body` themselves with snakeyaml
   * if they ever wire it.
   */
  private fun parseSimplePairs(lines: List<String>): Map<String, String> {
    val out = LinkedHashMap<String, String>()
    for (rawLine in lines) {
      val line = rawLine.trim()
      if (line.isEmpty() || line.startsWith("#")) continue
      val colon = line.indexOf(':')
      if (colon <= 0) continue
      val key = line.substring(0, colon).trim()
      if (key.isEmpty()) continue
      val value = line.substring(colon + 1).trim()
        .removeSurrounding("\"")
        .removeSurrounding("'")
      out[key] = value
    }
    return out
  }
}
