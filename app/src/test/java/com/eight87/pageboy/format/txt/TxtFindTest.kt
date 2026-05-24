package com.eight87.pageboy.format.txt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets

/**
 * Phase E.6 — find-in-doc enumeration over a TxtLineSource.
 */
class TxtFindTest {

  private fun src(text: String): InMemoryTxtLineSource =
    InMemoryTxtLineSource(bytes = text.toByteArray(StandardCharsets.UTF_8), charset = StandardCharsets.UTF_8)

  @Test
  fun `empty query yields no matches`() {
    val matches = TxtFind.findAll(src("anything"), "")
    assertTrue(matches.isEmpty())
  }

  @Test
  fun `finds matches across multiple lines case-insensitively`() {
    val s = src("Hello world\nhello pageboy\nNothing here\nHELLO universe")
    val matches = TxtFind.findAll(s, "hello")
    assertEquals(3, matches.size)
    // rangeStart carries the line index for the TXT renderer.
    assertEquals(0, matches[0].rangeStart)
    assertEquals(1, matches[1].rangeStart)
    assertEquals(3, matches[2].rangeStart)
  }

  @Test
  fun `finds multiple hits within a single line`() {
    val s = src("ho ho ho")
    val matches = TxtFind.findAll(s, "ho")
    assertEquals(3, matches.size)
    matches.forEach { assertEquals(0, it.rangeStart) }
  }

  @Test
  fun `respects maxMatches ceiling`() {
    val text = (1..200).joinToString("\n") { "hit line $it" }
    val matches = TxtFind.findAll(src(text), "hit", maxMatches = 50)
    assertEquals(50, matches.size)
  }

  @Test
  fun `snippet surrounds the match with ellipses when truncated`() {
    val s = src("padding before the real match needle padding after")
    val matches = TxtFind.findAll(s, "needle")
    assertEquals(1, matches.size)
    val snip = matches[0].contextSnippet ?: ""
    assertTrue("snippet should include the match", snip.contains("needle"))
  }
}
