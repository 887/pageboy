package com.eight87.pageboy.format.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase D / D.11 — block flattening covers each top-level kind and
 * collects footnote definitions to the end.
 */
class MarkdownBlockModelTest {

  private val parser = MarkdownParser()

  @Test
  fun `flattens headings paragraphs blockquotes and lists`() {
    val ast = parser.parse(
      """
      # Title

      A paragraph.

      > A quote

      - one
      - two

      1. first
      2. second
      """.trimIndent(),
    )
    val blocks = flattenBlocks(ast)
    assertTrue(blocks.any { it is MarkdownBlock.Heading })
    assertTrue(blocks.any { it is MarkdownBlock.Paragraph })
    assertTrue(blocks.any { it is MarkdownBlock.BlockQuote })
    assertTrue(blocks.any { it is MarkdownBlock.BulletList })
    assertTrue(blocks.any { it is MarkdownBlock.OrderedList })
  }

  @Test
  fun `recognises fenced code thematic break and table`() {
    val ast = parser.parse(
      """
      ```kotlin
      val x = 1
      ```

      ---

      | a | b |
      | --- | --- |
      | 1 | 2 |
      """.trimIndent(),
    )
    val blocks = flattenBlocks(ast)
    assertTrue(blocks.any { it is MarkdownBlock.FencedCode })
    assertTrue(blocks.any { it is MarkdownBlock.Thematic })
    assertTrue(blocks.any { it is MarkdownBlock.Table })
  }

  @Test
  fun `lifts a standalone image paragraph into its own block`() {
    val ast = parser.parse("![alt](https://example.com/x.png)")
    val blocks = flattenBlocks(ast)
    assertEquals(1, blocks.size)
    assertTrue(blocks[0] is MarkdownBlock.StandaloneImage)
  }

  @Test
  fun `paragraphs with text plus image stay as paragraph blocks`() {
    val ast = parser.parse("See ![alt](https://example.com/x.png) here")
    val blocks = flattenBlocks(ast)
    assertEquals(1, blocks.size)
    assertTrue(blocks[0] is MarkdownBlock.Paragraph)
  }

  @Test
  fun `footnote definitions are collected to the end`() {
    val ast = parser.parse(
      """
      Body refers to [^one] something.

      [^one]: First footnote.

      More body.

      [^two]: Second footnote.
      """.trimIndent(),
    )
    val blocks = flattenBlocks(ast)
    // Last entries should be the footnote definitions.
    val footnoteIndices = blocks.withIndex().filter { it.value is MarkdownBlock.Footnote }.map { it.index }
    assertTrue("at least two footnote definitions expected", footnoteIndices.size >= 2)
    // Trailing block(s) should be footnotes.
    assertTrue("footnotes should come last", footnoteIndices.last() == blocks.size - 1)
  }
}
