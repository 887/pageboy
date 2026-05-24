package com.eight87.pageboy.format.mobi.internal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Phase Q.7 — pure JVM tests for [MobiImageExtractor]. Covers JPEG /
 * PNG / GIF magic sniffing + the no-image short-circuit when the
 * header's firstImageRecordIndex is null.
 */
class MobiImageExtractorTest {

  @Test
  fun `JPEG magic surfaces image with image-jpeg mime`() {
    val jpegBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0x00, 0x42)
    val (container, header) = buildContainerWithImageAt(record = 2, imageBytes = jpegBytes)
    val images = MobiImageExtractor.extract(container, header)
    val image = images["1"]
    assertNotNull(image)
    assertEquals("image/jpeg", image!!.mime)
  }

  @Test
  fun `PNG magic surfaces image with image-png mime`() {
    val pngBytes = byteArrayOf(
      0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00,
    )
    val (container, header) = buildContainerWithImageAt(record = 2, imageBytes = pngBytes)
    val images = MobiImageExtractor.extract(container, header)
    val image = images["1"]
    assertNotNull(image)
    assertEquals("image/png", image!!.mime)
  }

  @Test
  fun `null firstImageRecordIndex returns empty map`() {
    val record0 = MobiTestFixtures.mobiRecord0()
    val bytes = MobiTestFixtures.palmDb(listOf(record0, ByteArray(8)))
    val container = PalmDbReader.read(bytes)
    val header = MobipocketHeaderReader.read(container)
    val images = MobiImageExtractor.extract(container, header)
    assertEquals(0, images.size)
  }

  @Test
  fun `non-image record bytes are silently skipped`() {
    // Random non-magic bytes shouldn't be returned as an image.
    val notImage = "random aux record data".toByteArray(Charsets.UTF_8)
    val (container, header) = buildContainerWithImageAt(record = 2, imageBytes = notImage)
    val images = MobiImageExtractor.extract(container, header)
    assertNull(images["1"])
  }

  /**
   * Build a minimal MOBI container where record `record` carries the
   * given image bytes and the Mobipocket header points at it as the
   * first image record. The container has 3 records total: header,
   * body, image.
   */
  private fun buildContainerWithImageAt(
    record: Int,
    imageBytes: ByteArray,
  ): Pair<PalmDbContainer, MobipocketHeader> {
    require(record == 2) // helper only handles the 3-record layout
    val record0 = mobiRecord0WithFirstImage(2)
    val bytes = MobiTestFixtures.palmDb(listOf(record0, ByteArray(4), imageBytes))
    val container = PalmDbReader.read(bytes)
    val header = MobipocketHeaderReader.read(container)
    return container to header
  }

  /**
   * Variant of [MobiTestFixtures.mobiRecord0] that writes a non-null
   * firstImageRecordIndex. Inline here so the fixture stays generic.
   */
  private fun mobiRecord0WithFirstImage(firstImageIndex: Int): ByteArray {
    val record0 = MobiTestFixtures.mobiRecord0()
    // first image index sits at offset 84 of record 0 = byte 84.
    record0[84] = ((firstImageIndex ushr 24) and 0xFF).toByte()
    record0[85] = ((firstImageIndex ushr 16) and 0xFF).toByte()
    record0[86] = ((firstImageIndex ushr 8) and 0xFF).toByte()
    record0[87] = (firstImageIndex and 0xFF).toByte()
    return record0
  }
}
