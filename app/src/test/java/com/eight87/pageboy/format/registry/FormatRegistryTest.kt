package com.eight87.pageboy.format.registry

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.eight87.pageboy.data.library.DocumentFormat
import com.eight87.pageboy.format.api.DocumentBytesSource
import com.eight87.pageboy.format.api.DocumentHandle
import com.eight87.pageboy.format.api.DocumentRenderer
import com.eight87.pageboy.format.placeholder.PlaceholderHandle
import com.eight87.pageboy.format.placeholder.PlaceholderRenderer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase C.9 — registry dispatch + placeholder fallback contract.
 */
class FormatRegistryTest {

  private class FakePdfHandle : DocumentHandle {
    override val format = DocumentFormat.Pdf
    override val title = "fake-pdf"
    override val pageCount: Int? = 12
  }

  private class FakePdfRenderer : DocumentRenderer {
    override val format = DocumentFormat.Pdf
    override suspend fun open(source: DocumentBytesSource): DocumentHandle = FakePdfHandle()
    @Composable override fun Body(handle: DocumentHandle, modifier: Modifier) = Unit
  }

  @Test
  fun `mapped format dispatches to its registered renderer`() {
    val pdfRenderer = FakePdfRenderer()
    val registry = CompiledFormatRegistry(mapOf(DocumentFormat.Pdf to pdfRenderer))
    assertSame(pdfRenderer, registry.rendererFor(DocumentFormat.Pdf))
  }

  @Test
  fun `unmapped format falls back to a PlaceholderRenderer`() {
    val registry = CompiledFormatRegistry(emptyMap())
    val renderer = registry.rendererFor(DocumentFormat.Markdown)
    assertNotNull(renderer)
    assertTrue("expected placeholder renderer", renderer is PlaceholderRenderer)
    assertEquals(DocumentFormat.Markdown, renderer.format)
  }

  @Test
  fun `placeholder fallback is cached per format`() {
    val registry = CompiledFormatRegistry(emptyMap())
    val a = registry.rendererFor(DocumentFormat.Epub)
    val b = registry.rendererFor(DocumentFormat.Epub)
    assertSame("cached lookup should return the same instance", a, b)
  }

  @Test
  fun `different unmapped formats get distinct placeholder instances`() {
    val registry = CompiledFormatRegistry(emptyMap())
    val a = registry.rendererFor(DocumentFormat.Epub)
    val b = registry.rendererFor(DocumentFormat.Docx)
    assertEquals(DocumentFormat.Epub, a.format)
    assertEquals(DocumentFormat.Docx, b.format)
  }

  @Test
  fun `placeholder handle round-trips through the renderer's format`() {
    val registry = CompiledFormatRegistry(emptyMap())
    val placeholder = registry.rendererFor(DocumentFormat.Ods) as PlaceholderRenderer
    val handle = PlaceholderHandle(format = placeholder.format, title = "x")
    assertEquals(DocumentFormat.Ods, handle.format)
  }
}
