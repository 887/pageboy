package com.eight87.pageboy.format.mobi

import com.eight87.pageboy.format.mobi.internal.MobiCompressionMode
import com.eight87.pageboy.format.mobi.internal.MobiParseError
import com.eight87.pageboy.format.mobi.internal.MobiParseException
import com.eight87.pageboy.format.mobi.internal.MobiTestFixtures
import com.eight87.pageboy.format.mobi.internal.MobiVariant
import com.eight87.pageboy.format.mobi.internal.MobipocketHeaderReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Phase Q.7 — end-to-end MOBI parser tests.
 *
 * Includes:
 *  - happy-path MOBI 6 parse with uncompressed body.
 *  - DRM-detection (EXTH 401) verification.
 *  - Combo file KF8 dispatch verification (per format-mobi.md spec
 *    gotcha: KF8 section preferred over MOBI 6 when both present).
 *  - UnsupportedCompression sealed-error case (mode 17480 HUFF/CDIC).
 */
class MobiParserTest {

  private val parser = MobiParser()

  @Test
  fun `parses uncompressed MOBI 6 body and returns HTML`() {
    val htmlBytes = "<html><body><p>Phase Q smoke</p></body></html>"
      .toByteArray(Charsets.UTF_8)
    val record0 = MobiTestFixtures.mobiRecord0(
      compressionMode = MobiCompressionMode.UNCOMPRESSED,
      bodyRecordCount = 1,
      title = "Smoke",
    )
    val bytes = MobiTestFixtures.palmDb(listOf(record0, htmlBytes))
    val content = parser.parse(bytes)
    assertTrue(content.html.contains("Phase Q smoke"))
    assertEquals("Smoke", content.metadata.title)
    assertEquals(MobiVariant.Mobi6, content.variant)
  }

  @Test
  fun `DRM-detected MOBI raises sealed DrmDetected error`() {
    val record0 = MobiTestFixtures.mobiRecord0(
      title = "Locked",
      exth = mapOf(MobipocketHeaderReader.EXTH_DRM_SERVER_ID to ByteArray(4) { 1 }),
    )
    val bytes = MobiTestFixtures.palmDb(listOf(record0, ByteArray(1)))
    val ex = runCatching { parser.parse(bytes) }.exceptionOrNull()
    if (ex !is MobiParseException) fail("expected DRM exception, got $ex")
    assertEquals(MobiParseError.DrmDetected, (ex as MobiParseException).error)
  }

  @Test
  fun `combo MOBI prefers KF8 segment over MOBI 6 body`() {
    // MOBI 6 body says "FALLBACK"; KF8 segment says "KF8 PREFERRED".
    // Container layout:
    //   record 0: MOBI 6 header (compression = uncompressed, body = 1, kf8Boundary = 2)
    //   record 1: MOBI 6 body bytes (FALLBACK)
    //   record 2: KF8 header (compression = uncompressed, body = 1)
    //   record 3: KF8 body bytes (KF8 PREFERRED)
    val fallback = "<p>FALLBACK</p>".toByteArray(Charsets.UTF_8)
    val preferred = "<p>KF8 PREFERRED</p>".toByteArray(Charsets.UTF_8)
    val mobi6Record0 = MobiTestFixtures.mobiRecord0(
      compressionMode = MobiCompressionMode.UNCOMPRESSED,
      bodyRecordCount = 1,
      kf8BoundaryRecord = 2,
    )
    val kf8Record0 = MobiTestFixtures.mobiRecord0(
      compressionMode = MobiCompressionMode.UNCOMPRESSED,
      bodyRecordCount = 1,
    )
    val bytes = MobiTestFixtures.palmDb(
      listOf(mobi6Record0, fallback, kf8Record0, preferred),
    )
    val content = parser.parse(bytes)
    assertEquals(MobiVariant.Combo, content.variant)
    assertTrue(
      "expected KF8-preferred content but got: ${content.html}",
      content.html.contains("KF8 PREFERRED"),
    )
  }

  @Test
  fun `HUFF CDIC compression mode raises UnsupportedCompression`() {
    val record0 = MobiTestFixtures.mobiRecord0(
      compressionMode = MobiCompressionMode.HUFF_CDIC,
      bodyRecordCount = 1,
    )
    val bytes = MobiTestFixtures.palmDb(listOf(record0, ByteArray(1)))
    val ex = runCatching { parser.parse(bytes) }.exceptionOrNull()
    if (ex !is MobiParseException) fail("expected UnsupportedCompression exception, got $ex")
    val error = (ex as MobiParseException).error
    assertTrue(error is MobiParseError.UnsupportedCompression)
    assertEquals(MobiCompressionMode.HUFF_CDIC, (error as MobiParseError.UnsupportedCompression).mode)
  }

  @Test
  fun `non-MOBI PalmDB raises MalformedContainer`() {
    val record0 = MobiTestFixtures.mobiRecord0()
    val bytes = MobiTestFixtures.palmDb(
      listOf(record0, ByteArray(8)),
      creatorCode = "OTHR",
    )
    val ex = runCatching { parser.parse(bytes) }.exceptionOrNull()
    if (ex !is MobiParseException) fail("expected MalformedContainer, got $ex")
    assertTrue((ex as MobiParseException).error is MobiParseError.MalformedContainer)
  }

  @Test
  fun `MobiTitleExtractor returns title without body decompress`() {
    val record0 = MobiTestFixtures.mobiRecord0(title = "Probe Title")
    val bytes = MobiTestFixtures.palmDb(listOf(record0, ByteArray(0)))
    val title = MobiTitleExtractor.extractFrom(bytes)
    assertNotNull(title)
    assertEquals("Probe Title", title)
  }
}
