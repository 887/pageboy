package com.eight87.pageboy.format.markdown

import com.eight87.pageboy.data.library.DocumentFormat
import com.eight87.pageboy.format.api.DocumentBytesSource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream

/**
 * Phase D / D.11 — DocumentRenderer contract for MarkdownRenderer.
 * Verifies open() builds a MarkdownHandle with the right fields and
 * extractTitle() reuses the first-H1 / front-matter `title` lookup.
 */
class MarkdownRendererTest {

  private class StringBytes(
    private val text: String,
    private val name: String? = "doc.md",
  ) : DocumentBytesSource {
    override suspend fun openStream(): InputStream = ByteArrayInputStream(text.toByteArray(Charsets.UTF_8))
    override suspend fun length(): Long = text.toByteArray(Charsets.UTF_8).size.toLong()
    override suspend fun displayName(): String? = name
  }

  private val renderer = MarkdownRenderer(MarkdownParser())

  @Test
  fun `format property is Markdown`() {
    assertEquals(DocumentFormat.Markdown, renderer.format)
  }

  @Test
  fun `open returns a MarkdownHandle with H1 title and parsed AST`() = runBlocking {
    val handle = renderer.open(StringBytes("# Hello\n\nA paragraph.")) as MarkdownHandle
    assertEquals(DocumentFormat.Markdown, handle.format)
    assertEquals("Hello", handle.title)
    assertNull("Markdown is reflowable, no page count", handle.pageCount)
    assertNotNull(handle.ast)
    assertEquals(emptyMap<String, String>(), handle.frontMatter)
  }

  @Test
  fun `open falls back to front-matter title when no H1 exists`() = runBlocking {
    val raw = """
      ---
      title: From Front Matter
      ---
      ## Just an H2
      Body
    """.trimIndent()
    val handle = renderer.open(StringBytes(raw)) as MarkdownHandle
    assertEquals("From Front Matter", handle.title)
    assertEquals("From Front Matter", handle.frontMatter["title"])
  }

  @Test
  fun `open falls back to display name when no H1 and no front-matter title`() = runBlocking {
    val handle = renderer.open(StringBytes("Just body text.", name = "notes.md")) as MarkdownHandle
    assertEquals("notes.md", handle.title)
  }

  @Test
  fun `open falls back to default title when source is anonymous and body has no headings`() = runBlocking {
    val handle = renderer.open(StringBytes("Just body text.", name = null)) as MarkdownHandle
    assertTrue("default title is non-empty", handle.title.isNotEmpty())
  }

  @Test
  fun `extractTitle returns the first H1 plain text`() = runBlocking {
    val title = renderer.extractTitle(StringBytes("# Doc\n\nBody"))
    assertEquals("Doc", title)
  }

  @Test
  fun `extractTitle returns null when no title is discoverable`() = runBlocking {
    val title = renderer.extractTitle(StringBytes("Just body, no heading.", name = "n.md"))
    assertNull(title)
  }

  @Test
  fun `front-matter is stripped from the rendered body`() = runBlocking {
    val raw = """
      ---
      title: Hi
      ---
      # Real Title
      body
    """.trimIndent()
    val handle = renderer.open(StringBytes(raw)) as MarkdownHandle
    assertEquals("Real Title", handle.title)
    // Raw text on the handle reflects the body post-strip (so find-in-doc
    // doesn't scan the front-matter region).
    assertTrue(!handle.rawText.contains("---"))
  }
}
