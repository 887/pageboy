package com.eight87.pageboy.format.pdf

import java.io.InputStream

/**
 * Phase F.3 — lightweight PDF Info-dictionary title sniffer.
 *
 * Why hand-rolled and not the system [android.graphics.pdf.PdfRenderer]:
 * the framework class doesn't expose the Info dict, and androidx.pdf's
 * higher-level [androidx.pdf.PdfDocument] runs in a sandboxed process
 * which is too heavy a hook for a scanner-side title-only probe. The
 * PDF spec's Info-dict `/Title` is a string literal in the file's
 * trailer dictionary — a 100-line byte scan handles the 95 % case
 * (uncompressed Info entries, plain ASCII / UTF-16 BE titles).
 *
 * Out of scope:
 *  - Compressed object streams (PDF 1.5+ `/ObjStm`). Most authoring
 *    tools (LaTeX, MS Word, Pages) write the Info dict uncompressed at
 *    the document trailer, so the 95 % heuristic still lands.
 *  - Encrypted PDFs. The decoder returns null and the scanner falls
 *    back to the filename.
 *  - Hex-encoded strings inside `<...>`. We only decode literal `(...)`
 *    strings + UTF-16 BE BOM-prefixed strings.
 *
 * The chrome's smarter title lookup happens via androidx.pdf at open
 * time (the [androidx.pdf.PdfDocument] does expose `getMetadata()` —
 * see [PdfRenderer.open]). This file's sniffer is the scanner's
 * pre-open probe so the library card shows the right title without
 * touching the sandbox loader.
 */
internal object PdfTitleExtractor {

  /** Hard cap on bytes read. Most Info dicts live in the first 64 KiB; 1 MiB is comfortable headroom. */
  private const val MAX_READ_BYTES = 1 * 1024 * 1024

  /**
   * Probe the PDF stream for the Info-dict `/Title` value. Returns the
   * decoded title or null when no title is present / the file is
   * malformed / the title is in a compressed object stream we can't
   * see.
   *
   * Caller-supplied [stream] is read but not closed; the caller owns
   * the stream lifecycle.
   */
  fun extractTitle(stream: InputStream): String? {
    val bytes = readUpTo(stream, MAX_READ_BYTES) ?: return null
    if (!looksLikePdf(bytes)) return null
    return findTitle(bytes)
  }

  private fun readUpTo(stream: InputStream, cap: Int): ByteArray? = runCatching {
    val buf = ByteArray(cap)
    var off = 0
    while (off < cap) {
      val n = stream.read(buf, off, cap - off)
      if (n <= 0) break
      off += n
    }
    buf.copyOf(off)
  }.getOrNull()

  private fun looksLikePdf(bytes: ByteArray): Boolean =
    bytes.size >= 5 &&
      bytes[0] == '%'.code.toByte() &&
      bytes[1] == 'P'.code.toByte() &&
      bytes[2] == 'D'.code.toByte() &&
      bytes[3] == 'F'.code.toByte() &&
      bytes[4] == '-'.code.toByte()

  /**
   * Walk the bytes looking for `/Title (` or `/Title <FEFF...>`. The
   * PDF spec allows both literal-string + hex-string forms; we handle
   * literal strings (95 % case) including UTF-16 BE BOM titles via
   * [decodeLiteral].
   */
  internal fun findTitle(bytes: ByteArray): String? {
    val key = "/Title".toByteArray(Charsets.US_ASCII)
    var i = indexOf(bytes, key, 0)
    while (i >= 0) {
      var p = i + key.size
      // Skip whitespace between /Title and the string opener.
      while (p < bytes.size && bytes[p].toInt().toChar().isWhitespace()) p++
      if (p >= bytes.size) return null
      val opener = bytes[p].toInt().toChar()
      when (opener) {
        '(' -> decodeLiteral(bytes, p + 1)?.let { return it.ifBlank { null } }
        '<' -> decodeHexString(bytes, p + 1)?.let { return it.ifBlank { null } }
        else -> Unit // not a string — try the next occurrence
      }
      i = indexOf(bytes, key, p)
    }
    return null
  }

  /**
   * Decode a literal-string `(...)` from the buffer starting at
   * [start]. Handles balanced parens + a small set of common escapes;
   * supports the PDF UTF-16 BE convention (a BOM `\xFE\xFF` at the
   * start denotes 16-bit big-endian). Stops at the matching close
   * paren or when [start] runs past the buffer.
   */
  private fun decodeLiteral(bytes: ByteArray, start: Int): String? {
    val raw = ByteArrayBuilder()
    var p = start
    var depth = 1
    while (p < bytes.size) {
      val b = bytes[p]
      when (b) {
        '\\'.code.toByte() -> {
          // Escape — copy the next byte as literal. Skip common
          // 3-digit octal escapes by best-effort treating them as a
          // single byte (the spec's octal handling adds an integer
          // value; we punt and just take the immediate next byte
          // which is close enough for a title sniffer).
          if (p + 1 < bytes.size) {
            raw.append(bytes[p + 1])
            p += 2
          } else {
            p++
          }
        }
        '('.code.toByte() -> { depth++; raw.append(b); p++ }
        ')'.code.toByte() -> {
          depth--
          if (depth == 0) {
            return decodeLiteralBytes(raw.toByteArray())
          }
          raw.append(b); p++
        }
        else -> { raw.append(b); p++ }
      }
      if (raw.size() > MAX_TITLE_BYTES) return null
    }
    return null
  }

  private fun decodeLiteralBytes(payload: ByteArray): String {
    if (payload.size >= 2 && payload[0] == 0xFE.toByte() && payload[1] == 0xFF.toByte()) {
      return String(payload, 2, payload.size - 2, Charsets.UTF_16BE).trim()
    }
    // PDF default for literal strings is PDFDocEncoding, which overlaps
    // ISO-8859-1 in the ASCII range. ISO-8859-1 is the closest
    // single-byte JVM charset.
    return String(payload, Charsets.ISO_8859_1).trim()
  }

  /** Decode a hex string `<...>` (each pair of nibbles = one byte). */
  private fun decodeHexString(bytes: ByteArray, start: Int): String? {
    val sb = StringBuilder()
    var p = start
    var nibble: Int = -1
    while (p < bytes.size) {
      val ch = bytes[p].toInt().toChar()
      if (ch == '>') break
      if (ch.isWhitespace()) { p++; continue }
      val n = hexDigit(ch)
      if (n < 0) return null
      if (nibble < 0) {
        nibble = n
      } else {
        sb.append(((nibble shl 4) or n).toChar())
        nibble = -1
      }
      p++
      if (sb.length > MAX_TITLE_BYTES) return null
    }
    val raw = sb.toString().toByteArray(Charsets.ISO_8859_1)
    return decodeLiteralBytes(raw)
  }

  private fun hexDigit(ch: Char): Int = when (ch) {
    in '0'..'9' -> ch.code - '0'.code
    in 'a'..'f' -> 10 + (ch.code - 'a'.code)
    in 'A'..'F' -> 10 + (ch.code - 'A'.code)
    else -> -1
  }

  private fun indexOf(haystack: ByteArray, needle: ByteArray, fromIndex: Int): Int {
    if (needle.isEmpty()) return fromIndex
    val limit = haystack.size - needle.size + 1
    var i = fromIndex
    while (i < limit) {
      var j = 0
      while (j < needle.size && haystack[i + j] == needle[j]) j++
      if (j == needle.size) return i
      i++
    }
    return -1
  }

  private const val MAX_TITLE_BYTES = 4096

  /**
   * Small growable byte buffer — avoids pulling in `java.io.ByteArrayOutputStream`
   * (which is heavier than the actual problem warrants here).
   */
  private class ByteArrayBuilder {
    private var buf = ByteArray(64)
    private var len = 0
    fun append(b: Byte) {
      if (len >= buf.size) buf = buf.copyOf(buf.size * 2)
      buf[len++] = b
    }
    fun size(): Int = len
    fun toByteArray(): ByteArray = buf.copyOf(len)
  }
}
