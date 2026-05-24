package com.eight87.pageboy.format.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase E.6 — `buildLineStartIndex` + `blockIndexForOffset` helpers.
 * Pure JVM; no Compose.
 */
class MarkdownBlockIndexTest {

  @Test
  fun `line-start index includes a leading zero`() {
    val starts = buildLineStartIndex("abc\ndef\nghi")
    assertEquals(0, starts[0])
    assertEquals(4, starts[1])
    assertEquals(8, starts[2])
  }

  @Test
  fun `empty input still produces a single zero entry`() {
    val starts = buildLineStartIndex("")
    assertEquals(intArrayOf(0).toList(), starts.toList())
  }

  @Test
  fun `block-index-for-offset maps offset 0 to block 0`() {
    val parser = MarkdownParser()
    val text = "# A\n\nB\n\nC\n\nD"
    val blocks = flattenBlocks(parser.parse(text))
    val starts = buildLineStartIndex(text)
    assertEquals(0, blockIndexForOffset(0, blocks, starts))
  }

  @Test
  fun `block-index-for-offset maps end-of-document offset near the last block`() {
    val parser = MarkdownParser()
    val text = "# A\n\nB\n\nC\n\nD"
    val blocks = flattenBlocks(parser.parse(text))
    val starts = buildLineStartIndex(text)
    val mid = blockIndexForOffset(text.length, blocks, starts)
    assertTrue("expected late-document offset to land in the back half", mid >= blocks.size / 2)
  }

  @Test
  fun `block-index-for-offset stays in range`() {
    val parser = MarkdownParser()
    val text = "# A\n\nB"
    val blocks = flattenBlocks(parser.parse(text))
    val starts = buildLineStartIndex(text)
    val idx = blockIndexForOffset(text.length + 100, blocks, starts)
    assertTrue(idx in 0 until blocks.size)
  }
}
