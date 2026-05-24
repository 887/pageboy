package com.eight87.pageboy.format.pdf.signing

import com.lowagie.text.pdf.PdfReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * Phase H.7 — JVM verification of the visual-stamp burn-in path. Burns
 * a 1×1 transparent PNG into a tiny generated PDF; asserts the output
 * re-opens and the page count is preserved.
 */
class PdfStampBurnInTest {

  @Test
  fun `burn produces a valid PDF that reloads with the original page count`() {
    val burnIn = PdfStampBurnIn()
    val src = SigningTestFixtures.makeTinyPdf("Stamp here")
    val out = ByteArrayOutputStream()
    burnIn.burn(
      input = ByteArrayInputStream(src),
      output = out,
      pngBytes = ONE_BY_ONE_TRANSPARENT_PNG,
      pageIndex = 1,
      rectInPoints = PdfStampBurnIn.Rect(100f, 100f, 300f, 200f),
    )
    val outBytes = out.toByteArray()
    assertTrue("output is non-empty", outBytes.isNotEmpty())
    val reader = PdfReader(outBytes)
    assertEquals(1, reader.numberOfPages)
    reader.close()
  }

  @Test
  fun `burn does not mutate the source bytes`() {
    val burnIn = PdfStampBurnIn()
    val src = SigningTestFixtures.makeTinyPdf("Preserve me")
    val srcCopy = src.copyOf()
    burnIn.burn(
      input = ByteArrayInputStream(src),
      output = ByteArrayOutputStream(),
      pngBytes = ONE_BY_ONE_TRANSPARENT_PNG,
      pageIndex = 1,
      rectInPoints = PdfStampBurnIn.Rect(0f, 0f, 50f, 50f),
    )
    org.junit.Assert.assertArrayEquals(srcCopy, src)
  }

  /** 1×1 transparent PNG — minimal valid PNG payload. */
  private val ONE_BY_ONE_TRANSPARENT_PNG: ByteArray = byteArrayOf(
    0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
    0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
    0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
    0x08, 0x06, 0x00, 0x00, 0x00, 0x1F, 0x15.toByte(), 0xC4.toByte(), 0x89.toByte(),
    0x00, 0x00, 0x00, 0x0D, 0x49, 0x44, 0x41, 0x54,
    0x78, 0x9C.toByte(), 0x63, 0x00, 0x01, 0x00, 0x00, 0x05,
    0x00, 0x01, 0x0D, 0x0A, 0x2D, 0xB4.toByte(),
    0x00, 0x00, 0x00, 0x00, 0x49, 0x45, 0x4E, 0x44,
    0xAE.toByte(), 0x42, 0x60, 0x82.toByte(),
  )
}
