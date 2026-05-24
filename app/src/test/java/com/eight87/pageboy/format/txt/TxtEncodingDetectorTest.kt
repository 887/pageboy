package com.eight87.pageboy.format.txt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets

/**
 * Phase E.6 — encoding detection contract. Pure JVM (no Robolectric);
 * the detector has zero Android dependencies.
 */
class TxtEncodingDetectorTest {

  @Test
  fun `UTF-8 BOM is detected and bomLength is 3`() {
    val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) +
      "hello world".toByteArray(StandardCharsets.UTF_8)
    val det = TxtEncodingDetector.detect(bytes)
    assertEquals(StandardCharsets.UTF_8, det.charset)
    assertEquals(TxtEncodingDetector.Source.Bom, det.source)
    assertEquals(3, det.bomLength)
  }

  @Test
  fun `UTF-16 LE BOM is detected and bomLength is 2`() {
    val bytes = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + "hi".toByteArray(Charsets.UTF_16LE)
    val det = TxtEncodingDetector.detect(bytes)
    assertEquals(StandardCharsets.UTF_16LE, det.charset)
    assertEquals(TxtEncodingDetector.Source.Bom, det.source)
    assertEquals(2, det.bomLength)
  }

  @Test
  fun `UTF-16 BE BOM is detected and bomLength is 2`() {
    val bytes = byteArrayOf(0xFE.toByte(), 0xFF.toByte()) + "hi".toByteArray(Charsets.UTF_16BE)
    val det = TxtEncodingDetector.detect(bytes)
    assertEquals(StandardCharsets.UTF_16BE, det.charset)
    assertEquals(TxtEncodingDetector.Source.Bom, det.source)
  }

  @Test
  fun `clean UTF-8 without a BOM is detected via trial-decode`() {
    val bytes = "café naïve résumé 你好".toByteArray(StandardCharsets.UTF_8)
    val det = TxtEncodingDetector.detect(bytes)
    assertEquals(StandardCharsets.UTF_8, det.charset)
    assertEquals(TxtEncodingDetector.Source.Utf8Trial, det.source)
    assertEquals(0, det.bomLength)
  }

  @Test
  fun `cp1252 high bytes fall back to windows-1252`() {
    // \xe9 = é in cp1252; valid solo byte is not valid UTF-8.
    val bytes = byteArrayOf(
      'c'.code.toByte(), 'a'.code.toByte(), 'f'.code.toByte(), 0xE9.toByte(),
      '\n'.code.toByte(),
    )
    val det = TxtEncodingDetector.detect(bytes)
    assertEquals(TxtEncodingDetector.Source.Cp1252Fallback, det.source)
    assertEquals("windows-1252", det.charset.name())
  }

  @Test
  fun `empty input falls into the UTF-8 default`() {
    val det = TxtEncodingDetector.detect(ByteArray(0))
    assertEquals(StandardCharsets.UTF_8, det.charset)
    assertEquals(0, det.bomLength)
  }

  @Test
  fun `BOM-less UTF-16 LE pattern triggers the heuristic`() {
    // "abcdefghij" in UTF-16 LE has every other byte 0x00.
    val bytes = "abcdefghij".toByteArray(Charsets.UTF_16LE)
    val det = TxtEncodingDetector.detect(bytes)
    assertEquals(StandardCharsets.UTF_16LE, det.charset)
    assertEquals(TxtEncodingDetector.Source.Utf16Heuristic, det.source)
  }

  @Test
  fun `BOM-less UTF-16 BE pattern triggers the heuristic`() {
    val bytes = "abcdefghij".toByteArray(Charsets.UTF_16BE)
    val det = TxtEncodingDetector.detect(bytes)
    assertEquals(StandardCharsets.UTF_16BE, det.charset)
    assertEquals(TxtEncodingDetector.Source.Utf16Heuristic, det.source)
  }

  @Test
  fun `pure ASCII is reported as UTF-8 via trial`() {
    val bytes = "The quick brown fox jumps over the lazy dog.\n".toByteArray(StandardCharsets.US_ASCII)
    val det = TxtEncodingDetector.detect(bytes)
    assertEquals(StandardCharsets.UTF_8, det.charset)
    assertTrue(det.source == TxtEncodingDetector.Source.Utf8Trial)
  }
}
