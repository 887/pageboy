package com.eight87.pageboy.format.placeholder

import com.eight87.pageboy.data.library.DocumentFormat
import com.eight87.pageboy.format.api.DocumentBytesSource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream

/**
 * Phase C.9 — placeholder renderer contract: open + extractTitle.
 * The Body composable is exercised by the [com.eight87.pageboy.ui.reader.ReaderScreenSmokeTest].
 */
class PlaceholderRendererTest {

  private class FakeBytes(
    private val name: String? = "sample.pdf",
    private val len: Long = 1024L,
  ) : DocumentBytesSource {
    override suspend fun openStream(): InputStream = ByteArrayInputStream(ByteArray(0))
    override suspend fun length(): Long = len
    override suspend fun displayName(): String? = name
  }

  @Test
  fun `open returns a PlaceholderHandle carrying the source display name`() = runBlocking {
    val renderer = PlaceholderRenderer(DocumentFormat.Pdf)
    val handle = renderer.open(FakeBytes(name = "manual.pdf"))
    assertEquals(DocumentFormat.Pdf, handle.format)
    assertEquals("manual.pdf", handle.title)
    assertEquals(null, handle.pageCount)
  }

  @Test
  fun `open falls back to a default title when the source has no display name`() = runBlocking {
    val renderer = PlaceholderRenderer(DocumentFormat.Markdown)
    val handle = renderer.open(FakeBytes(name = null))
    // Default title is the renderer's "Document" sentinel — not blank.
    assert(handle.title.isNotEmpty())
  }

  @Test
  fun `extractTitle returns null so the scanner falls back to the filename`() = runBlocking {
    val renderer = PlaceholderRenderer(DocumentFormat.Epub)
    assertNull(renderer.extractTitle(FakeBytes()))
  }

  @Test
  fun `format property matches the renderer's constructor argument`() {
    assertEquals(DocumentFormat.Xlsx, PlaceholderRenderer(DocumentFormat.Xlsx).format)
    assertEquals(DocumentFormat.Unknown, PlaceholderRenderer(DocumentFormat.Unknown).format)
  }
}
