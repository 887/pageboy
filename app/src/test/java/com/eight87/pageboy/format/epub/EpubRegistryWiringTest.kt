package com.eight87.pageboy.format.epub

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.eight87.pageboy.data.library.DocumentFormat
import com.eight87.pageboy.domain.render.RendererContext
import com.eight87.pageboy.format.api.DocumentBytesSource
import com.eight87.pageboy.format.api.DocumentHandle
import com.eight87.pageboy.format.api.DocumentRenderer
import com.eight87.pageboy.format.placeholder.PlaceholderRenderer
import com.eight87.pageboy.format.registry.CompiledFormatRegistry
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase M.5 — verifies that once an EPUB renderer is wired into the
 * [CompiledFormatRegistry] the registry no longer falls back to the
 * [PlaceholderRenderer] for [DocumentFormat.Epub]. Mirrors the
 * [com.eight87.pageboy.format.registry.FormatRegistryTest] pattern.
 *
 * This test does not instantiate the real [EpubRenderer] (which
 * requires a live Readium parser) — it uses a tiny fake mapped to the
 * EPUB format slot, asserting only the registry-dispatch contract.
 */
class EpubRegistryWiringTest {

  private class FakeEpubRenderer : DocumentRenderer {
    override val format = DocumentFormat.Epub
    override suspend fun open(source: DocumentBytesSource): DocumentHandle = throw NotImplementedError()
    @Composable override fun Body(handle: DocumentHandle, context: RendererContext, modifier: Modifier) = Unit
  }

  @Test
  fun `registry routes Epub to the registered renderer when present`() {
    val fake = FakeEpubRenderer()
    val registry = CompiledFormatRegistry(mapOf(DocumentFormat.Epub to fake))
    assertSame(fake, registry.rendererFor(DocumentFormat.Epub))
    assertTrue(
      "no placeholder fallback when EPUB is wired",
      registry.rendererFor(DocumentFormat.Epub) !is PlaceholderRenderer,
    )
  }

  @Test
  fun `placeholder fallback still kicks in for unmapped EPUB slot`() {
    val registry = CompiledFormatRegistry(emptyMap())
    val renderer = registry.rendererFor(DocumentFormat.Epub)
    assertTrue("empty registry falls back to placeholder", renderer is PlaceholderRenderer)
    assertNotEquals(
      "placeholder should be a different renderer than a real impl would be",
      renderer.format.name,
      "Markdown",
    )
  }
}
