package com.eight87.pageboy.format.txt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets

/**
 * Phase E.6 — line splitter + windowed access contract.
 */
class TxtLineSourceTest {

  private fun src(text: String): InMemoryTxtLineSource =
    InMemoryTxtLineSource(bytes = text.toByteArray(StandardCharsets.UTF_8), charset = StandardCharsets.UTF_8)

  @Test
  fun `splits on LF`() {
    val s = src("a\nb\nc")
    assertEquals(3, s.lineCount)
    assertEquals("a", s.lineAt(0))
    assertEquals("b", s.lineAt(1))
    assertEquals("c", s.lineAt(2))
  }

  @Test
  fun `splits on CRLF without producing phantom blank lines`() {
    val s = src("a\r\nb\r\nc")
    assertEquals(3, s.lineCount)
    assertEquals("a", s.lineAt(0))
    assertEquals("b", s.lineAt(1))
  }

  @Test
  fun `splits on lone CR`() {
    val s = src("a\rb\rc")
    assertEquals(3, s.lineCount)
    assertEquals("a", s.lineAt(0))
  }

  @Test
  fun `drops trailing empty produced by a final terminator`() {
    val s = src("only-one\n")
    assertEquals(1, s.lineCount)
    assertEquals("only-one", s.lineAt(0))
  }

  @Test
  fun `preserves middle empty lines as separate items`() {
    val s = src("a\n\nb")
    assertEquals(3, s.lineCount)
    assertEquals("", s.lineAt(1))
  }

  @Test
  fun `wraps very-long single lines when they exceed the threshold`() {
    val line = "x".repeat(10_000)
    val s = InMemoryTxtLineSource(
      bytes = line.toByteArray(StandardCharsets.UTF_8),
      charset = StandardCharsets.UTF_8,
      wrapAfterChars = 4096,
    )
    assertTrue(s.isWrapByCharLimit)
    assertTrue(s.lineCount >= 2)
    // Each virtual line should not exceed the wrap threshold.
    s.snapshotLines().forEach { assertTrue(it.length <= 4096) }
  }

  @Test
  fun `does not wrap when no line exceeds the threshold`() {
    val s = src("short line\nanother\n")
    assertFalse(s.isWrapByCharLimit)
  }

  @Test
  fun `searchFrom finds the next case-insensitive hit`() {
    val s = src("apple\nbanana\nORANGE\napricot")
    assertEquals(2, s.searchFrom("orange"))
    assertEquals(3, s.searchFrom("Apricot"))
    assertEquals(-1, s.searchFrom("nothing-here"))
  }

  @Test
  fun `searchFrom returns -1 for an empty query`() {
    val s = src("anything")
    assertEquals(-1, s.searchFrom(""))
  }

  @Test
  fun `lineAt returns empty string for out-of-range index`() {
    val s = src("only")
    assertEquals("", s.lineAt(-1))
    assertEquals("", s.lineAt(100))
  }

  @Test
  fun `decodes utf-8 content correctly`() {
    val s = src("café\n你好")
    assertEquals("café", s.lineAt(0))
    assertEquals("你好", s.lineAt(1))
  }

  @Test
  fun `respects BOM offset when constructing`() {
    val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
    val body = "hello".toByteArray(StandardCharsets.UTF_8)
    val s = InMemoryTxtLineSource(bytes = bom + body, charset = StandardCharsets.UTF_8, bomLength = 3)
    assertEquals("hello", s.lineAt(0))
  }

  @Test
  fun `large line count is iterable without OOM`() {
    val text = (1..10_000).joinToString("\n") { "line $it" }
    val s = src(text)
    assertEquals(10_000, s.lineCount)
    assertEquals("line 1", s.lineAt(0))
    assertEquals("line 10000", s.lineAt(9_999))
  }
}
