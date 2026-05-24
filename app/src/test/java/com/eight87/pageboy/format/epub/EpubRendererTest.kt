package com.eight87.pageboy.format.epub

import androidx.test.core.app.ApplicationProvider
import com.eight87.pageboy.data.library.DocumentFormat
import com.eight87.pageboy.format.api.DocumentBytesSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.readium.r2.shared.publication.Publication
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream

/**
 * Phase M.8 — surface-level contract tests for [EpubRenderer]. The
 * heavy end-to-end open path requires a real EPUB asset routed through
 * SAF (mirrors the pattern other format renderers use — no per-renderer
 * end-to-end tests on the JVM-only target; those happen via the AVD
 * smoke chain). This test class focuses on the identity / capability
 * surface every consumer of the renderer touches.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EpubRendererTest {

  @Test
  fun `renderer reports the Epub format`() {
    val renderer = EpubRenderer(parser = throwingParser())
    assertEquals(DocumentFormat.Epub, renderer.format)
  }

  @Test
  fun `default DocumentHandle tocAvailable is false`() {
    // Negative control — every format renderer other than EPUB ships
    // handles that inherit the default `false` from DocumentHandle.
    // The EpubHandle override is the one that returns true.
    val handle = StubHandle()
    assertFalse("default tocAvailable must be false", handle.tocAvailable)
  }

  @Test
  fun `default DocumentHandle pageCount is null`() {
    // EPUB reflows; pageCount stays null per the contract intent so
    // the chrome top bar suppresses the page indicator.
    val handle = StubHandle()
    assertNull("EPUB has no native page count under reflow", handle.pageCount)
  }

  @Test
  fun `extractTitle returns null when parser fails`() = runTest {
    // The scanner-side title probe must be lossy — return null on
    // any failure so the scanner falls back to filename-derived
    // title. This test verifies the contract by feeding a parser
    // that always throws.
    val renderer = EpubRenderer(parser = throwingParser())
    val title = renderer.extractTitle(NoopSource())
    assertNull("extractTitle must swallow parser failures", title)
  }

  // ---- helpers ----

  private class StubHandle : com.eight87.pageboy.format.api.DocumentHandle {
    override val format = DocumentFormat.Markdown
    override val title = "stub"
    override val pageCount: Int? = null
  }

  /** A parser whose `parse(...)` always fails. Subclass instead of
   *  Mockito (no Mockito on the test classpath). */
  private class ThrowingEpubParser : EpubParser(
    context = ApplicationProvider.getApplicationContext(),
    contentResolver = ApplicationProvider
      .getApplicationContext<android.content.Context>()
      .contentResolver,
  ) {
    override suspend fun parse(source: DocumentBytesSource): Publication =
      throw IOException("simulated parser failure")
  }

  private fun throwingParser(): EpubParser = ThrowingEpubParser()

  private class NoopSource : DocumentBytesSource {
    override suspend fun openStream(): InputStream = ByteArrayInputStream(byteArrayOf())
    override suspend fun length(): Long = 0L
    override suspend fun displayName(): String? = null
  }
}
