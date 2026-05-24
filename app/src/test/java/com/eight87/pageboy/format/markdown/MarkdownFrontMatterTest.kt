package com.eight87.pageboy.format.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase D / D.11 — DIY YAML front-matter sniffer.
 */
class MarkdownFrontMatterTest {

  @Test
  fun `strips a canonical YAML preamble`() {
    val raw = """
      ---
      title: Hello
      author: Pageboy
      ---
      # Body
      Content
    """.trimIndent()
    val split = MarkdownFrontMatter.split(raw)
    assertEquals("Hello", split.frontMatter["title"])
    assertEquals("Pageboy", split.frontMatter["author"])
    assertTrue("body should start at the heading", split.body.startsWith("# Body"))
  }

  @Test
  fun `passes through a document without front-matter`() {
    val raw = "# Hello\n\nWorld"
    val split = MarkdownFrontMatter.split(raw)
    assertEquals(emptyMap<String, String>(), split.frontMatter)
    assertEquals(raw, split.body)
  }

  @Test
  fun `does not confuse a thematic break for a fence`() {
    val raw = """
      Some text
      ---
      More text
    """.trimIndent()
    val split = MarkdownFrontMatter.split(raw)
    assertEquals(emptyMap<String, String>(), split.frontMatter)
    assertEquals(raw, split.body)
  }

  @Test
  fun `handles missing closing fence by returning raw`() {
    val raw = """
      ---
      title: Forever Opening
      # Body never starts
    """.trimIndent()
    val split = MarkdownFrontMatter.split(raw)
    assertEquals(emptyMap<String, String>(), split.frontMatter)
    assertEquals(raw, split.body)
  }

  @Test
  fun `strips surrounding quotes from values`() {
    val raw = """
      ---
      title: "Quoted Title"
      tag: 'single'
      ---
      Body
    """.trimIndent()
    val split = MarkdownFrontMatter.split(raw)
    assertEquals("Quoted Title", split.frontMatter["title"])
    assertEquals("single", split.frontMatter["tag"])
  }
}
