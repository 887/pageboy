package com.eight87.pageboy.format.txt

import java.nio.charset.Charset

/**
 * Phase E.4 — line-windowed accessor for a decoded text document.
 *
 * The Phase E TXT renderer takes the simpler path of decoding the
 * entire document into memory once (the bytes-source contract gives us
 * a one-shot `InputStream` and rewinding-via-second-open is not free on
 * SAF). The "windowing" surface is at the LazyColumn level: only the
 * visible lines compose, even though the full line list is in memory
 * as a `List<String>`. For the 50 MB log file flagged in
 * `format-txt.md` we cap memory by chunking very long lines into
 * fixed-width virtual lines (G3 mitigation).
 *
 * The narrow [TxtLineSource] interface lets future phases drop in a
 * disk-backed windowed implementation (with a [LongArray]
 * line-start index over the raw bytes + `RandomAccessFile.seek`) under
 * the same contract without touching `TxtBody`. Phase E ships the
 * in-memory impl; format-txt.md's E.3/E.4 split out the windowed impl
 * as a future-phase refinement.
 */
interface TxtLineSource : AutoCloseable {

  /** Total number of (virtual) lines available. */
  val lineCount: Int

  /** Indicates the source had at least one line longer than the wrap threshold. */
  val isWrapByCharLimit: Boolean

  /** Read a single line by index. Index out of range returns the empty string. */
  fun lineAt(index: Int): String

  /**
   * Walk the lines starting from [fromLine] looking for [query] (case-insensitive).
   * Returns the line index of the next hit, or `-1` if none. Cheap helper for
   * the Phase E find-in-doc wiring; renderer-side bulk match enumeration
   * happens in [TxtFind].
   */
  fun searchFrom(query: String, fromLine: Int = 0): Int

  override fun close() {
    // no-op by default — in-memory impl has nothing to release.
  }
}

/**
 * In-memory [TxtLineSource]. Reads + decodes the supplied bytes once,
 * splits on `\r\n` / `\r` / `\n` (CRLF first so it never decomposes into
 * two lines, per format-txt.md G2), wraps any line over
 * [wrapAfterChars] into fixed-width virtual lines (G3 mitigation).
 *
 * Backing storage is a `List<String>`; line lookup is O(1).
 */
class InMemoryTxtLineSource(
  bytes: ByteArray,
  charset: Charset,
  bomLength: Int = 0,
  wrapAfterChars: Int = DEFAULT_WRAP_THRESHOLD,
) : TxtLineSource {

  private val lines: List<String>
  override val isWrapByCharLimit: Boolean

  init {
    val effectiveOffset = bomLength.coerceIn(0, bytes.size)
    val decoded = String(bytes, effectiveOffset, bytes.size - effectiveOffset, charset)
    val rawLines = splitLines(decoded)
    var anyWrapped = false
    val out = ArrayList<String>(rawLines.size)
    for (line in rawLines) {
      if (line.length <= wrapAfterChars) {
        out += line
      } else {
        anyWrapped = true
        var start = 0
        while (start < line.length) {
          val end = (start + wrapAfterChars).coerceAtMost(line.length)
          out += line.substring(start, end)
          start = end
        }
      }
    }
    lines = out
    isWrapByCharLimit = anyWrapped
  }

  override val lineCount: Int get() = lines.size

  override fun lineAt(index: Int): String =
    if (index in lines.indices) lines[index] else ""

  override fun searchFrom(query: String, fromLine: Int): Int {
    if (query.isEmpty()) return -1
    val needle = query.lowercase()
    val from = fromLine.coerceAtLeast(0)
    for (i in from until lines.size) {
      if (lines[i].lowercase().contains(needle)) return i
    }
    return -1
  }

  internal fun snapshotLines(): List<String> = lines

  companion object {
    /** Default wrap threshold for very-long single lines (format-txt.md G3). */
    const val DEFAULT_WRAP_THRESHOLD = 4096
  }
}

/**
 * Split text on `\r\n | \r | \n`, preserving empty lines. The CRLF-first
 * order matters — splitting on each character independently would
 * decompose CRLF into two adjacent newlines and produce phantom blank
 * lines (format-txt.md G2).
 */
internal fun splitLines(text: String): List<String> {
  if (text.isEmpty()) return listOf("")
  val out = ArrayList<String>()
  var start = 0
  var i = 0
  while (i < text.length) {
    val c = text[i]
    if (c == '\r') {
      out += text.substring(start, i)
      if (i + 1 < text.length && text[i + 1] == '\n') i++
      i++
      start = i
    } else if (c == '\n') {
      out += text.substring(start, i)
      i++
      start = i
    } else {
      i++
    }
  }
  if (start <= text.length) {
    out += text.substring(start, text.length)
  }
  // Drop trailing empty produced by a final terminator (per format-txt.md G2).
  if (out.size > 1 && out.last().isEmpty()) {
    return out.subList(0, out.size - 1)
  }
  return out
}
