package com.eight87.pageboy.data.library

import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Phase B.16 — pure JVM test for [DocumentClassifier]. Feeds known byte
 * prefixes for each format; asserts the classifier returns the right
 * [DocumentFormat].
 */
class DocumentClassifierTest {

  @Test
  fun `PDF magic header classifies as Pdf`() {
    val bytes = "%PDF-1.7\nrest of file".toByteArray(Charsets.US_ASCII)
    val result = DocumentClassifier.classify("manual.pdf") { ByteArrayInputStream(bytes) }
    assertEquals(DocumentFormat.Pdf, result)
  }

  @Test
  fun `EPUB ZIP with mimetype member classifies as Epub`() {
    val bytes = zipPrefix() +
      "mimetypeapplication/epub+zipPKxx".toByteArray(Charsets.US_ASCII)
    val result = DocumentClassifier.classify("book.epub") { ByteArrayInputStream(bytes) }
    assertEquals(DocumentFormat.Epub, result)
  }

  @Test
  fun `DOCX ZIP with word document marker classifies as Docx`() {
    val bytes = zipPrefix() +
      "PK...word/document.xml...stuff".toByteArray(Charsets.US_ASCII)
    val result = DocumentClassifier.classify("notes.docx") { ByteArrayInputStream(bytes) }
    assertEquals(DocumentFormat.Docx, result)
  }

  @Test
  fun `XLSX ZIP with workbook marker classifies as Xlsx`() {
    val bytes = zipPrefix() + "xl/workbook.xml stuff".toByteArray(Charsets.US_ASCII)
    val result = DocumentClassifier.classify("budget.xlsx") { ByteArrayInputStream(bytes) }
    assertEquals(DocumentFormat.Xlsx, result)
  }

  @Test
  fun `ODT ZIP with mimetype member classifies as Odt`() {
    val bytes = zipPrefix() +
      "mimetypeapplication/vnd.oasis.opendocument.textmore".toByteArray(Charsets.US_ASCII)
    val result = DocumentClassifier.classify("essay.odt") { ByteArrayInputStream(bytes) }
    assertEquals(DocumentFormat.Odt, result)
  }

  @Test
  fun `ODS ZIP with mimetype member classifies as Ods`() {
    val bytes = zipPrefix() +
      "mimetypeapplication/vnd.oasis.opendocument.spreadsheetmore"
        .toByteArray(Charsets.US_ASCII)
    val result = DocumentClassifier.classify("ledger.ods") { ByteArrayInputStream(bytes) }
    assertEquals(DocumentFormat.Ods, result)
  }

  @Test
  fun `Markdown extension classifies as Markdown when no magic header`() {
    val bytes = "# Hello\nWorld".toByteArray()
    val result = DocumentClassifier.classify("readme.md") { ByteArrayInputStream(bytes) }
    assertEquals(DocumentFormat.Markdown, result)
  }

  @Test
  fun `txt extension classifies as Txt`() {
    val bytes = "Lorem ipsum".toByteArray()
    val result = DocumentClassifier.classify("notes.txt") { ByteArrayInputStream(bytes) }
    assertEquals(DocumentFormat.Txt, result)
  }

  @Test
  fun `unrecognised extension and no magic classifies as Unknown`() {
    val bytes = "random bytes".toByteArray()
    val result = DocumentClassifier.classify("payload.bin") { ByteArrayInputStream(bytes) }
    assertEquals(DocumentFormat.Unknown, result)
  }

  @Test
  fun `null stream falls back to extension`() {
    val result = DocumentClassifier.classify("manual.pdf") { null }
    assertEquals(DocumentFormat.Pdf, result)
  }

  @Test
  fun `extension fallback respects md alias markdown`() {
    val result = DocumentClassifier.classify("CHANGES.markdown") { null }
    assertEquals(DocumentFormat.Markdown, result)
  }

  @Test
  fun `ZIP without known marker falls back to extension`() {
    val bytes = zipPrefix()
    val result = DocumentClassifier.classify("mystery.docx") { ByteArrayInputStream(bytes) }
    assertEquals(DocumentFormat.Docx, result)
  }

  // Phase Q — MOBI / KF8 / AZW / AZW3 / PRC classifier cases.

  @Test
  fun `MOBI palmdb BOOK MOBI magic classifies as Mobi`() {
    val bytes = palmDbHeader(typeCode = "BOOK", creatorCode = "MOBI")
    val result = DocumentClassifier.classify("ebook.mobi") { ByteArrayInputStream(bytes) }
    assertEquals(DocumentFormat.Mobi, result)
  }

  @Test
  fun `MOBI palmdb TEXt MOBI magic classifies as Mobi`() {
    val bytes = palmDbHeader(typeCode = "TEXt", creatorCode = "MOBI")
    val result = DocumentClassifier.classify("legacy.prc") { ByteArrayInputStream(bytes) }
    assertEquals(DocumentFormat.Mobi, result)
  }

  @Test
  fun `mobi extension fallback when stream is null`() {
    val result = DocumentClassifier.classify("guide.mobi") { null }
    assertEquals(DocumentFormat.Mobi, result)
  }

  @Test
  fun `azw extension fallback when stream is null`() {
    val result = DocumentClassifier.classify("book.azw") { null }
    assertEquals(DocumentFormat.Mobi, result)
  }

  @Test
  fun `azw3 extension fallback when stream is null`() {
    val result = DocumentClassifier.classify("book.azw3") { null }
    assertEquals(DocumentFormat.Mobi, result)
  }

  @Test
  fun `prc extension fallback when stream is null`() {
    val result = DocumentClassifier.classify("oldebook.prc") { null }
    assertEquals(DocumentFormat.Mobi, result)
  }

  @Test
  fun `palmdb with non-MOBI creator stays Unknown`() {
    val bytes = palmDbHeader(typeCode = "BOOK", creatorCode = "OTHR")
    val result = DocumentClassifier.classify("foreign.pdb") { ByteArrayInputStream(bytes) }
    assertEquals(DocumentFormat.Unknown, result)
  }

  /**
   * Synth PalmDB header with the given 4-char type + creator codes at
   * offsets 60..63 and 64..67. The earlier bytes are the PalmDB
   * database-name field (zero-padded ASCII) plus reserved fields the
   * sniffer doesn't read.
   */
  private fun palmDbHeader(typeCode: String, creatorCode: String): ByteArray {
    require(typeCode.length == 4 && creatorCode.length == 4)
    val buf = ByteArray(78)
    // Database name field (offsets 0..31) — ASCII, zero-padded.
    "TestBook".toByteArray(Charsets.US_ASCII).copyInto(buf, destinationOffset = 0)
    // Type code at offset 60.
    typeCode.toByteArray(Charsets.US_ASCII).copyInto(buf, destinationOffset = 60)
    // Creator code at offset 64.
    creatorCode.toByteArray(Charsets.US_ASCII).copyInto(buf, destinationOffset = 64)
    // Remaining offsets 68..77 left zero (uniqueIDseed + appInfoID +
    // sortInfoID etc. — the sniffer doesn't read them).
    return buf
  }

  /**
   * Real ZIP local-file-header magic: 0x50 0x4B 0x03 0x04. Distinct from
   * the printable "PK" string (only the first two bytes), so we encode
   * the four-byte prefix explicitly.
   */
  private fun zipPrefix(): ByteArray = byteArrayOf(0x50, 0x4B, 0x03, 0x04)
}
