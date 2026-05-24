package com.eight87.pageboy.format.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Phase D / D.11 — first-H1 plain-text title extraction.
 */
class MarkdownTitleExtractorTest {

  private val parser = MarkdownParser()

  @Test
  fun `extracts first H1 title`() {
    val ast = parser.parse("# Hello World\n\nBody")
    assertEquals("Hello World", MarkdownTitleExtractor.extract(ast))
  }

  @Test
  fun `returns null when no H1 exists`() {
    val ast = parser.parse("## Sub-heading only\n\nBody")
    assertNull(MarkdownTitleExtractor.extract(ast))
  }

  @Test
  fun `flattens bold and italic inside H1`() {
    val ast = parser.parse("# Hello **bold** *italic* `code`")
    assertEquals("Hello bold italic code", MarkdownTitleExtractor.extract(ast))
  }

  @Test
  fun `skips empty H1`() {
    val ast = parser.parse("#\n\nBody")
    assertNull(MarkdownTitleExtractor.extract(ast))
  }

  @Test
  fun `picks the first H1 even when H2 comes earlier`() {
    val ast = parser.parse("## Pre-amble\n\n# Real Title\n\n# Second")
    assertEquals("Real Title", MarkdownTitleExtractor.extract(ast))
  }
}
