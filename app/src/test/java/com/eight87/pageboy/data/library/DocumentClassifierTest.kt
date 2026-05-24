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

  /**
   * Real ZIP local-file-header magic: 0x50 0x4B 0x03 0x04. Distinct from
   * the printable "PK" string (only the first two bytes), so we encode
   * the four-byte prefix explicitly.
   */
  private fun zipPrefix(): ByteArray = byteArrayOf(0x50, 0x4B, 0x03, 0x04)
}
