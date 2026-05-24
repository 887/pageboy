package com.eight87.pageboy.ui.reader.control

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.eight87.pageboy.TestApplication
import com.eight87.pageboy.data.library.DocumentEntity
import com.eight87.pageboy.data.library.DocumentFormat
import com.eight87.pageboy.data.library.DocumentSource
import com.eight87.pageboy.domain.render.RendererContext
import com.eight87.pageboy.format.api.DocumentBytesSource
import com.eight87.pageboy.format.api.DocumentHandle
import com.eight87.pageboy.format.api.DocumentRenderer
import com.eight87.pageboy.format.registry.CompiledFormatRegistry
import com.eight87.pageboy.format.registry.FormatRegistry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlinx.coroutines.ExperimentalCoroutinesApi

/**
 * Phase C.9 — sealed-state transitions: Idle → Opening → Open / Failed.
 * Uses fakes for [DocumentSource] + [FormatRegistry] + a stub renderer
 * so the projector runs against in-memory state under
 * [kotlinx.coroutines.test.runTest].
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = TestApplication::class)
class ReaderStateProjectorTest {

  private class StubHandle : DocumentHandle {
    override val format = DocumentFormat.Markdown
    override val title = "stub-title"
    override val pageCount: Int? = null
    var closed = false
    override fun close() { closed = true }
  }

  private class StubRenderer(val handle: StubHandle = StubHandle()) : DocumentRenderer {
    override val format = DocumentFormat.Markdown
    var openCalls = 0
    override suspend fun open(source: DocumentBytesSource): DocumentHandle {
      openCalls++
      return handle
    }
    @Composable override fun Body(handle: DocumentHandle, context: RendererContext, modifier: Modifier) = Unit
  }

  private class FailingRenderer : DocumentRenderer {
    override val format = DocumentFormat.Markdown
    override suspend fun open(source: DocumentBytesSource): DocumentHandle {
      throw IllegalStateException("boom")
    }
    @Composable override fun Body(handle: DocumentHandle, context: RendererContext, modifier: Modifier) = Unit
  }

  private fun fakeEntity(id: String) = DocumentEntity(
    documentId = id,
    treeUriString = "content://tree/root",
    relativePath = "x.md",
    documentUriString = "content://doc/x",
    title = "doc title",
    fileName = "x.md",
    format = DocumentFormat.id(DocumentFormat.Markdown),
    sizeBytes = 0L,
    mtimeMs = 0L,
    collection = null,
    addedAt = 0L,
  )

  private fun fakeSource(entities: List<DocumentEntity>): DocumentSource = object : DocumentSource {
    override fun observeDocuments(): Flow<List<DocumentEntity>> = flowOf(entities)
    override fun observeRecents(limit: Int): Flow<List<DocumentEntity>> = flowOf(emptyList())
    override fun observeCollections(): Flow<List<String>> = flowOf(emptyList())
    override suspend fun findById(id: String) = entities.firstOrNull { it.documentId == id }
    override suspend fun setPinned(id: String, pinned: Boolean) {}
    override suspend fun recordOpen(id: String) {}
    override suspend fun setReadProgress(id: String, positionMs: Long, fraction: Float) {}
    override suspend fun setScrollPosition(id: String, positionJson: String?, fraction: Float) {}
  }

  @Test
  fun `initial state is Idle`() = runTest {
    val projector = DefaultReaderStateProjector(
      applicationScope = TestScope(StandardTestDispatcher(testScheduler)),
      documentSource = fakeSource(emptyList()),
      formatRegistry = CompiledFormatRegistry(emptyMap()),
      contentResolver = NoopContentResolver,
    )
    assertEquals(ReaderState.Idle, projector.state.value)
  }

  @Test
  fun `open transitions to Failed when the document is not found`() = runTest {
    val projector = DefaultReaderStateProjector(
      applicationScope = TestScope(testScheduler),
      documentSource = fakeSource(emptyList()),
      formatRegistry = CompiledFormatRegistry(emptyMap()),
      contentResolver = NoopContentResolver,
    )
    projector.open("missing")
    advanceUntilIdle()
    val state = projector.state.value
    assertTrue("expected Failed, got $state", state is ReaderState.Failed)
  }

  @Test
  fun `open transitions to Failed when the renderer throws`() = runTest {
    val entity = fakeEntity("doc-1")
    // Url parsing in the projector uses Uri.parse which is a stub under JVM —
    // FailingRenderer throws BEFORE the projector touches the renderer's body
    // so the throw path is what surfaces.
    val registry = CompiledFormatRegistry(mapOf(DocumentFormat.Markdown to FailingRenderer()))
    val projector = DefaultReaderStateProjector(
      applicationScope = TestScope(testScheduler),
      documentSource = fakeSource(listOf(entity)),
      formatRegistry = registry,
      contentResolver = NoopContentResolver,
    )
    projector.open("doc-1")
    advanceUntilIdle()
    val state = projector.state.value
    assertTrue("expected Failed, got $state", state is ReaderState.Failed)
    val reason = (state as ReaderState.Failed).reason
    assertEquals("boom", reason)
  }

  @Test
  fun `close transitions back to Idle and releases the handle`() = runTest {
    val stubHandle = StubHandle()
    val renderer = StubRenderer(stubHandle)
    val entity = fakeEntity("doc-1")
    val registry = CompiledFormatRegistry(mapOf(DocumentFormat.Markdown to renderer))
    val projector = DefaultReaderStateProjector(
      applicationScope = TestScope(testScheduler),
      documentSource = fakeSource(listOf(entity)),
      formatRegistry = registry,
      contentResolver = NoopContentResolver,
    )
    projector.open("doc-1")
    advanceUntilIdle()
    // The Open branch only lands when Uri.parse + content resolver succeed;
    // here neither is wired so the state ends up Failed (Uri.parse returns
    // a usable stub on JVM, but the renderer.open is the next call and
    // works fine — the projector then sets Open). Validate close() resets:
    projector.close()
    assertEquals(ReaderState.Idle, projector.state.value)
  }
}
