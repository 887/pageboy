package com.eight87.pageboy.format.mobi.internal

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Phase Q.7 — pure JVM tests for [PalmDocDecompressor].
 *
 * Covers the four PalmDOC byte-class branches:
 *  - `0x00`: literal NUL.
 *  - `0x01..0x08`: literal-run prefix.
 *  - `0x09..0x7F`: single literal ASCII byte.
 *  - `0x80..0xBF`: 14-bit LZ77 back-reference.
 *  - `0xC0..0xFF`: space + low-7-bit literal.
 */
class PalmDocDecompressorTest {

  @Test
  fun `single literal ASCII byte passes through unchanged`() {
    val input = byteArrayOf('A'.code.toByte(), 'B'.code.toByte(), 'C'.code.toByte())
    val output = PalmDocDecompressor.decompress(input)
    assertArrayEquals(input, output)
  }

  @Test
  fun `literal NUL byte emits zero`() {
    val output = PalmDocDecompressor.decompress(byteArrayOf(0x00))
    assertArrayEquals(byteArrayOf(0x00), output)
  }

  @Test
  fun `literal-run prefix copies following count bytes`() {
    // 0x03 means "copy next 3 bytes literally".
    val input = byteArrayOf(0x03, 'X'.code.toByte(), 'Y'.code.toByte(), 'Z'.code.toByte())
    val output = PalmDocDecompressor.decompress(input)
    assertArrayEquals(byteArrayOf('X'.code.toByte(), 'Y'.code.toByte(), 'Z'.code.toByte()), output)
  }

  @Test
  fun `high-bit literal pair emits space plus low-7-bit char`() {
    // 0xC0 | 'a' = 0xE1 -> emit space then 'a'.
    val input = byteArrayOf(0xE1.toByte())
    val output = PalmDocDecompressor.decompress(input)
    assertArrayEquals(byteArrayOf(' '.code.toByte(), 'a'.code.toByte()), output)
  }

  @Test
  fun `LZ77 back-reference duplicates earlier output bytes`() {
    // Seed: "Hello" (5 bytes), then a back-reference of distance=5, length=3.
    // pair = (5 << 3) | (3 - 3) = 40. High byte 0x80 | (40 >> 8) = 0x80.
    // Low byte = 40 & 0xFF = 0x28.
    val seed = "Hello".toByteArray(Charsets.US_ASCII)
    val backref = byteArrayOf(0x80.toByte(), 0x28)
    val input = seed + backref
    val output = PalmDocDecompressor.decompress(input)
    assertEquals("HelloHel", String(output, Charsets.US_ASCII))
  }

  @Test
  fun `decompressAll concatenates multiple records`() {
    val records = listOf(
      "ABC".toByteArray(Charsets.US_ASCII),
      "DEF".toByteArray(Charsets.US_ASCII),
    )
    val output = PalmDocDecompressor.decompressAll(records)
    assertEquals("ABCDEF", String(output, Charsets.US_ASCII))
  }

  @Test
  fun `truncated back-reference at end of input is dropped`() {
    // Single 0x80 byte without its low byte — should drop and stop.
    val input = byteArrayOf('X'.code.toByte(), 0x80.toByte())
    val output = PalmDocDecompressor.decompress(input)
    assertArrayEquals(byteArrayOf('X'.code.toByte()), output)
  }
}
