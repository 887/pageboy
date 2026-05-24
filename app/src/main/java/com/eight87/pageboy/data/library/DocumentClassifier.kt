package com.eight87.pageboy.data.library

import java.io.InputStream

/**
 * Phase B.4 — extension + magic-byte classifier for the eight supported
 * formats plus the `Unknown` safety net.
 *
 * Strategy:
 *  1. Read the first [HEAD_BYTES] bytes of the file (enough to sniff the
 *     ZIP central-directory headers for the four Office formats).
 *  2. Primary discriminant is the magic header:
 *     - `%PDF-` → PDF
 *     - ZIP magic `PK\x03\x04` → inspect the next ~4 KiB for the marker
 *       member name (EPUB / DOCX / XLSX / ODT / ODS).
 *  3. Fallback is the extension:
 *     - `.md` / `.markdown` → Markdown
 *     - `.txt` → Txt (only if content looks UTF-8 / ASCII)
 *     - `.pdf` / `.epub` / `.docx` / `.xlsx` / `.odt` / `.ods` —
 *       matched even when the magic sniff didn't run (e.g. an empty
 *       file).
 *  4. Otherwise `Unknown`.
 *
 * Stateless object — safe to share across threads. The scanner calls
 * [classify] once per file inside `Dispatchers.IO`.
 */
object DocumentClassifier {

  private const val HEAD_BYTES = 4096

  // Phase Q — MOBI / KF8 / AZW / AZW3 sit inside a PalmDB container whose
  // first 78 bytes carry a fixed-shape header. Offsets 60..63 hold a
  // 4-char ASCII "type code" (`BOOK` or `TEXt` for the formats we
  // accept) and offsets 64..67 hold a 4-char ASCII "creator code"
  // (`MOBI` for everything Mobipocket / Amazon shipped). See
  // docs/plans/format-mobi.md "Magic bytes for classifier".
  private const val PALMDB_TYPE_OFFSET = 60
  private const val PALMDB_CREATOR_OFFSET = 64
  private const val PALMDB_MIN_HEADER = 78

  /**
   * Classify a file by reading its first bytes via [openStream] and
   * cross-referencing [fileName]'s extension.
   *
   * [openStream] is a thunk so callers can defer SAF
   * `contentResolver.openInputStream(uri)` until the classifier actually
   * needs the bytes — for files we can classify from extension alone
   * (e.g. `.pdf`) we still magic-check to catch mis-named files, but a
   * future fast-path optimisation could short-circuit known extensions
   * without the read.
   *
   * Stream-open failures (revoked permission, missing file) fall through
   * to extension-only classification, which the caller treats as best
   * effort.
   */
  fun classify(fileName: String, openStream: () -> InputStream?): DocumentFormat {
    val head = runCatching {
      openStream()?.use { stream ->
        val buf = ByteArray(HEAD_BYTES)
        var read = 0
        while (read < HEAD_BYTES) {
          val n = stream.read(buf, read, HEAD_BYTES - read)
          if (n <= 0) break
          read += n
        }
        buf.copyOf(read)
      }
    }.getOrNull() ?: ByteArray(0)

    val magic = classifyByMagic(head)
    if (magic != DocumentFormat.Unknown) return magic
    return classifyByExtension(fileName)
  }

  /**
   * Visible for unit tests — classify from a byte array directly. The
   * scanner-facing path goes through [classify] above.
   */
  internal fun classifyByMagic(head: ByteArray): DocumentFormat {
    if (head.size >= 5 && head[0] == '%'.code.toByte() && head[1] == 'P'.code.toByte() &&
      head[2] == 'D'.code.toByte() && head[3] == 'F'.code.toByte() && head[4] == '-'.code.toByte()
    ) {
      return DocumentFormat.Pdf
    }
    // Phase Q — PalmDB / MOBI sniff. We do this BEFORE the ZIP check
    // because PalmDB and ZIP magics never collide (offset-0 of PalmDB
    // is the database name, not `PK\x03\x04`).
    if (head.size >= PALMDB_MIN_HEADER) {
      val type = asciiAt(head, PALMDB_TYPE_OFFSET, len = 4)
      val creator = asciiAt(head, PALMDB_CREATOR_OFFSET, len = 4)
      if ((type == "BOOK" || type == "TEXt") && creator == "MOBI") {
        return DocumentFormat.Mobi
      }
    }
    if (head.size >= 4 && head[0] == 0x50.toByte() && head[1] == 0x4B.toByte() &&
      head[2] == 0x03.toByte() && head[3] == 0x04.toByte()
    ) {
      // ZIP — inspect the head for the format-discriminator member name.
      // We do an ASCII substring scan over the byte buffer rather than
      // parsing the ZIP structure — the marker names appear plainly in
      // the central-directory entries inside the first few KiB for all
      // well-formed Office / ODF documents.
      val text = String(head, Charsets.US_ASCII)
      return when {
        text.contains("application/epub+zip") -> DocumentFormat.Epub
        text.contains("application/vnd.oasis.opendocument.text") -> DocumentFormat.Odt
        text.contains("application/vnd.oasis.opendocument.spreadsheet") -> DocumentFormat.Ods
        text.contains("word/document.xml") -> DocumentFormat.Docx
        text.contains("xl/workbook.xml") -> DocumentFormat.Xlsx
        // ZIP that didn't match a known content marker — leave to extension fallback.
        else -> DocumentFormat.Unknown
      }
    }
    return DocumentFormat.Unknown
  }

  internal fun classifyByExtension(fileName: String): DocumentFormat {
    val ext = fileName.substringAfterLast('.', missingDelimiterValue = "").lowercase()
    return when (ext) {
      "md", "markdown", "mdown", "mkd" -> DocumentFormat.Markdown
      "txt", "text", "log", "ini", "csv", "tsv" -> DocumentFormat.Txt
      "pdf" -> DocumentFormat.Pdf
      "epub" -> DocumentFormat.Epub
      // Phase Q — MOBI family extensions. `.prc` is the older
      // Palm-resource container that some pre-Amazon Mobipocket
      // titles shipped under; the byte-level layout is identical to
      // `.mobi`.
      "mobi", "azw", "azw3", "prc" -> DocumentFormat.Mobi
      "docx" -> DocumentFormat.Docx
      "xlsx" -> DocumentFormat.Xlsx
      "odt" -> DocumentFormat.Odt
      "ods" -> DocumentFormat.Ods
      else -> DocumentFormat.Unknown
    }
  }

  /**
   * Reads `len` ASCII bytes starting at `start` from [head], or returns
   * `null` if the slice would exceed the buffer or contains a non-ASCII
   * byte. Used by the PalmDB type/creator code sniff above.
   */
  private fun asciiAt(head: ByteArray, start: Int, len: Int): String? {
    if (start + len > head.size) return null
    val chars = CharArray(len)
    for (i in 0 until len) {
      val b = head[start + i].toInt() and 0xFF
      if (b < 0x20 || b > 0x7E) return null
      chars[i] = b.toChar()
    }
    return String(chars)
  }
}
