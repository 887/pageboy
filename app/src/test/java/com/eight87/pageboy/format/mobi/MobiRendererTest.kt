package com.eight87.pageboy.format.mobi

import com.eight87.pageboy.data.library.DocumentFormat
import com.eight87.pageboy.format.api.DocumentBytesSource
import com.eight87.pageboy.format.mobi.internal.MobiCompressionMode
import com.eight87.pageboy.format.mobi.internal.MobiTestFixtures
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream

/**
 * Phase Q.7 — DocumentRenderer contract for [MobiRenderer].
 */
class MobiRendererTest {

  private class BytesSource(
    private val bytes: ByteArray,
    private val name: String? = "book.mobi",
  ) : DocumentBytesSource {
    override suspend fun openStream(): InputStream = ByteArrayInputStream(bytes)
    override suspend fun length(): Long = bytes.size.toLong()
    override suspend fun displayName(): String? = name
  }

  private val renderer = MobiRenderer()

  @Test
  fun `format property is Mobi`() {
    assertEquals(DocumentFormat.Mobi, renderer.format)
  }

  @Test
  fun `open returns a MobiHandle with EXTH title`() = runBlocking {
    val htmlBytes = "<html><body><p>Hi</p></body></html>".toByteArray(Charsets.UTF_8)
    val record0 = MobiTestFixtures.mobiRecord0(
      compressionMode = MobiCompressionMode.UNCOMPRESSED,
      bodyRecordCount = 1,
      title = "Phase Q Reader",
    )
    val bytes = MobiTestFixtures.palmDb(listOf(record0, htmlBytes))
    val handle = renderer.open(BytesSource(bytes)) as MobiHandle
    assertEquals(DocumentFormat.Mobi, handle.format)
    assertEquals("Phase Q Reader", handle.title)
    assertNull("MOBI is reflowable, no page count", handle.pageCount)
    assertTrue(handle.content.html.contains("Hi"))
  }

  @Test
  fun `open falls back to filename when EXTH has no title`() = runBlocking {
    val htmlBytes = "<p>Body</p>".toByteArray(Charsets.UTF_8)
    val record0 = MobiTestFixtures.mobiRecord0(
      compressionMode = MobiCompressionMode.UNCOMPRESSED,
      bodyRecordCount = 1,
    )
    val bytes = MobiTestFixtures.palmDb(listOf(record0, htmlBytes))
    val handle = renderer.open(BytesSource(bytes, name = "mybook.mobi")) as MobiHandle
    assertEquals("mybook", handle.title)
  }

  @Test
  fun `open falls back to default title when no title and no filename`() = runBlocking {
    val htmlBytes = "<p>X</p>".toByteArray(Charsets.UTF_8)
    val record0 = MobiTestFixtures.mobiRecord0(
      compressionMode = MobiCompressionMode.UNCOMPRESSED,
      bodyRecordCount = 1,
    )
    val bytes = MobiTestFixtures.palmDb(listOf(record0, htmlBytes))
    val handle = renderer.open(BytesSource(bytes, name = null)) as MobiHandle
    assertTrue(handle.title.isNotBlank())
  }

  @Test
  fun `extractTitle returns the EXTH title without body decompress`() = runBlocking {
    val record0 = MobiTestFixtures.mobiRecord0(title = "Cheap Title")
    val bytes = MobiTestFixtures.palmDb(listOf(record0, ByteArray(0)))
    val title = renderer.extractTitle(BytesSource(bytes))
    assertNotNull(title)
    assertEquals("Cheap Title", title)
  }

  @Test
  fun `extractTitle returns null when no title is present`() = runBlocking {
    val record0 = MobiTestFixtures.mobiRecord0()
    val bytes = MobiTestFixtures.palmDb(listOf(record0, ByteArray(0)))
    val title = renderer.extractTitle(BytesSource(bytes))
    assertNull(title)
  }
}
