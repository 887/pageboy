package com.eight87.pageboy.format.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase D / D.11 — find-in-doc substring scan.
 */
class MarkdownFindTest {

  @Test
  fun `returns empty list for empty query`() {
    assertEquals(emptyList<Any>(), MarkdownFind.findAll("any text", ""))
  }

  @Test
  fun `finds all case-insensitive occurrences`() {
    val matches = MarkdownFind.findAll("Hello world, hello pageboy, HELLO universe.", "hello")
    assertEquals(3, matches.size)
    matches.forEach { m ->
      assertEquals("hello".length, m.rangeEnd - m.rangeStart)
    }
  }

  @Test
  fun `non-overlapping matches advance past previous end`() {
    val matches = MarkdownFind.findAll("aaaaaa", "aa")
    // 6 chars, "aa" non-overlapping → 3 matches at 0, 2, 4.
    assertEquals(3, matches.size)
    assertEquals(0, matches[0].rangeStart)
    assertEquals(2, matches[1].rangeStart)
    assertEquals(4, matches[2].rangeStart)
  }

  @Test
  fun `snippets include surrounding context`() {
    val matches = MarkdownFind.findAll("The quick brown fox jumps over the lazy dog.", "fox")
    assertEquals(1, matches.size)
    assertTrue("snippet should contain the match", matches[0].contextSnippet?.contains("fox") == true)
  }

  @Test
  fun `respects maxMatches ceiling`() {
    val text = "x".repeat(2000)
    val matches = MarkdownFind.findAll(text, "x", maxMatches = 10)
    assertEquals(10, matches.size)
  }
}
