package com.eight87.pageboy.format.mobi.internal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Phase Q.7 — pure JVM tests for [MobipocketHeaderReader].
 *
 * Includes the DRM-detection verification noted in the seed prompt's
 * "Report back" requirement (EXTH record 401 -> DrmDetected error).
 */
class MobipocketHeaderReaderTest {

  @Test
  fun `parses PalmDOC compression mode and body record count`() {
    val record0 = MobiTestFixtures.mobiRecord0(
      compressionMode = MobiCompressionMode.PALMDOC,
      bodyRecordCount = 3,
    )
    val bytes = MobiTestFixtures.palmDb(listOf(record0, ByteArray(1), ByteArray(1), ByteArray(1)))
    val container = PalmDbReader.read(bytes)
    val header = MobipocketHeaderReader.read(container)
    assertEquals(MobiCompressionMode.PALMDOC, header.compressionModeRaw)
    assertEquals(3, header.bodyRecordCount)
  }

  @Test
  fun `extracts title from fullName field`() {
    val record0 = MobiTestFixtures.mobiRecord0(title = "The Phase Q Reader")
    val bytes = MobiTestFixtures.palmDb(listOf(record0, ByteArray(1)))
    val container = PalmDbReader.read(bytes)
    val header = MobipocketHeaderReader.read(container)
    assertEquals("The Phase Q Reader", header.title)
  }

  @Test
  fun `extracts EXTH author metadata`() {
    val author = "Ada Lovelace".toByteArray(Charsets.UTF_8)
    val record0 = MobiTestFixtures.mobiRecord0(
      title = "Notes",
      exth = mapOf(MobipocketHeaderReader.EXTH_AUTHOR to author),
    )
    val bytes = MobiTestFixtures.palmDb(listOf(record0, ByteArray(1)))
    val container = PalmDbReader.read(bytes)
    val header = MobipocketHeaderReader.read(container)
    val authorBytes = header.exth[MobipocketHeaderReader.EXTH_AUTHOR]
    assertNotNull(authorBytes)
    assertEquals("Ada Lovelace", String(authorBytes!!, Charsets.UTF_8))
  }

  @Test
  fun `EXTH record 401 raises DrmDetected error`() {
    val record0 = MobiTestFixtures.mobiRecord0(
      title = "Encrypted",
      exth = mapOf(MobipocketHeaderReader.EXTH_DRM_SERVER_ID to ByteArray(4) { 0x42 }),
    )
    val bytes = MobiTestFixtures.palmDb(listOf(record0, ByteArray(1)))
    val container = PalmDbReader.read(bytes)
    val ex = runCatching { MobipocketHeaderReader.read(container) }.exceptionOrNull()
    if (ex !is MobiParseException) fail("expected MobiParseException for DRM, got $ex")
    assertEquals(MobiParseError.DrmDetected, (ex as MobiParseException).error)
  }

  @Test
  fun `KF8 boundary record presence flags Combo variant`() {
    val record0 = MobiTestFixtures.mobiRecord0(
      kf8BoundaryRecord = 5,
    )
    // Padding records so the container has at least 6 records (KF8 at index 5).
    val records = mutableListOf(record0)
    repeat(5) { records.add(ByteArray(1)) }
    val bytes = MobiTestFixtures.palmDb(records)
    val container = PalmDbReader.read(bytes)
    val header = MobipocketHeaderReader.read(container)
    assertEquals(MobiVariant.Combo, header.variant)
    assertEquals(5, header.kf8BoundaryRecordIndex)
  }

  @Test
  fun `no KF8 boundary infers Mobi6 variant`() {
    val record0 = MobiTestFixtures.mobiRecord0()
    val bytes = MobiTestFixtures.palmDb(listOf(record0, ByteArray(1)))
    val container = PalmDbReader.read(bytes)
    val header = MobipocketHeaderReader.read(container)
    assertEquals(MobiVariant.Mobi6, header.variant)
    assertNull(header.kf8BoundaryRecordIndex)
  }

  @Test
  fun `mobi type 248 without boundary flags Kf8 variant`() {
    val record0 = MobiTestFixtures.mobiRecord0(mobiType = 248)
    val bytes = MobiTestFixtures.palmDb(listOf(record0, ByteArray(1)))
    val container = PalmDbReader.read(bytes)
    val header = MobipocketHeaderReader.read(container)
    assertEquals(MobiVariant.Kf8, header.variant)
  }

  @Test
  fun `non-MOBI magic in record 0 raises MalformedContainer`() {
    val record0 = MobiTestFixtures.mobiRecord0()
    // Corrupt the "MOBI" magic.
    record0[16] = 'X'.code.toByte()
    val bytes = MobiTestFixtures.palmDb(listOf(record0, ByteArray(1)))
    val container = PalmDbReader.read(bytes)
    val ex = runCatching { MobipocketHeaderReader.read(container) }.exceptionOrNull()
    if (ex !is MobiParseException) fail("expected MobiParseException, got $ex")
    assertTrue((ex as MobiParseException).error is MobiParseError.MalformedContainer)
  }
}
