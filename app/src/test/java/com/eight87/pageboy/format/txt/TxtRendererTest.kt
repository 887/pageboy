package com.eight87.pageboy.format.txt

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
import java.nio.charset.StandardCharsets

/**
 * Phase E.6 — DocumentRenderer contract for TxtRenderer.
 */
class TxtRendererTest {

  private class StringBytes(
    private val text: String,
    private val name: String? = "doc.txt",
    private val bytes: ByteArray = text.toByteArray(StandardCharsets.UTF_8),
  ) : DocumentBytesSource {
    override suspend fun openStream(): InputStream = ByteArrayInputStream(bytes)
    override suspend fun length(): Long = bytes.size.toLong()
    override suspend fun displayName(): String? = name
  }

  private val renderer = TxtRenderer()

  @Test
  fun `format property is Txt`() {
    assertEquals(DocumentFormat.Txt, renderer.format)
  }

  @Test
  fun `open returns a TxtHandle carrying the line source and title`() = runBlocking {
    val handle = renderer.open(StringBytes("hello\nworld")) as TxtHandle
    assertEquals(DocumentFormat.Txt, handle.format)
    assertEquals("doc.txt", handle.title)
    assertNull("plain text is reflowable, no page count", handle.pageCount)
    assertNotNull(handle.lineSource)
    assertEquals(2, handle.lineSource.lineCount)
    assertEquals("hello", handle.lineSource.lineAt(0))
  }

  @Test
  fun `open falls back to default title when display name is null`() = runBlocking {
    val handle = renderer.open(StringBytes("body", name = null)) as TxtHandle
    assertTrue(handle.title.isNotEmpty())
  }

  @Test
  fun `open detects cp1252 high bytes`() = runBlocking {
    val cp1252 = byteArrayOf(
      'c'.code.toByte(), 'a'.code.toByte(), 'f'.code.toByte(), 0xE9.toByte(),
      '\n'.code.toByte(),
    )
    val handle = renderer.open(StringBytes("ignored", bytes = cp1252)) as TxtHandle
    assertEquals("windows-1252", handle.encodingLabel)
    assertEquals("café", handle.lineSource.lineAt(0))
  }

  @Test
  fun `open strips utf-8 BOM from the decoded body`() = runBlocking {
    val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
    val body = "hello".toByteArray(StandardCharsets.UTF_8)
    val handle = renderer.open(StringBytes("ignored", bytes = bom + body)) as TxtHandle
    assertEquals("hello", handle.lineSource.lineAt(0))
  }

  @Test
  fun `extractTitle returns null so the scanner falls back to the filename`() = runBlocking {
    assertNull(renderer.extractTitle(StringBytes("any")))
  }

  @Test
  fun `open handles an empty file without crashing`() = runBlocking {
    val handle = renderer.open(StringBytes("", bytes = ByteArray(0))) as TxtHandle
    assertEquals(1, handle.lineSource.lineCount)
    assertEquals("", handle.lineSource.lineAt(0))
  }

  @Test
  fun `close releases the line source`() = runBlocking {
    val handle = renderer.open(StringBytes("hi")) as TxtHandle
    handle.close() // no-op for in-memory but contract is honoured
  }
}
