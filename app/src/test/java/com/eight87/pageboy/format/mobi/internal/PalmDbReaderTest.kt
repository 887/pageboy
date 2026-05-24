package com.eight87.pageboy.format.mobi.internal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Phase Q.7 — pure JVM tests for [PalmDbReader].
 */
class PalmDbReaderTest {

  @Test
  fun `reads three-record PalmDB envelope with correct offsets`() {
    val records = listOf(
      ByteArray(10) { 0x01 },
      ByteArray(20) { 0x02 },
      ByteArray(5) { 0x03 },
    )
    val bytes = MobiTestFixtures.palmDb(records)
    val container = PalmDbReader.read(bytes)

    assertEquals(3, container.recordCount)
    assertEquals(78 + 3 * 8, container.recordOffsets[0])
    assertEquals("BOOK", container.typeCode)
    assertEquals("MOBI", container.creatorCode)
    assertTrue(container.isMobiFamily())
  }

  @Test
  fun `slices record bytes via recordRange`() {
    val records = listOf(
      ByteArray(4) { 0xAA.toByte() },
      ByteArray(7) { 0xBB.toByte() },
    )
    val bytes = MobiTestFixtures.palmDb(records)
    val container = PalmDbReader.read(bytes)

    val r0 = container.recordBytes(0)
    val r1 = container.recordBytes(1)
    assertEquals(4, r0.size)
    assertEquals(7, r1.size)
    assertEquals(0xAA.toByte(), r0[0])
    assertEquals(0xBB.toByte(), r1[0])
  }

  @Test
  fun `non-MOBI creator code reports isMobiFamily false`() {
    val records = listOf(ByteArray(8))
    val bytes = MobiTestFixtures.palmDb(records, typeCode = "BOOK", creatorCode = "OTHR")
    val container = PalmDbReader.read(bytes)
    assertFalse(container.isMobiFamily())
  }

  @Test
  fun `truncated file raises MalformedContainer`() {
    val truncated = ByteArray(40) // smaller than 78-byte header
    val ex = runCatching { PalmDbReader.read(truncated) }.exceptionOrNull()
    assertTrue(ex is MobiParseException)
    val error = (ex as MobiParseException).error
    assertTrue("expected MalformedContainer, got $error", error is MobiParseError.MalformedContainer)
  }

  @Test
  fun `zero-record PalmDB raises MalformedContainer`() {
    val emptyHeader = ByteArray(78)
    "TestBook".toByteArray(Charsets.US_ASCII).copyInto(emptyHeader, 0)
    "BOOK".toByteArray(Charsets.US_ASCII).copyInto(emptyHeader, 60)
    "MOBI".toByteArray(Charsets.US_ASCII).copyInto(emptyHeader, 64)
    // numRecords at offset 76..77 stays zero.
    val ex = runCatching { PalmDbReader.read(emptyHeader) }.exceptionOrNull()
    if (ex !is MobiParseException) fail("expected MobiParseException, got $ex")
    assertTrue((ex as MobiParseException).error is MobiParseError.MalformedContainer)
  }
}
