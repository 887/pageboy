package com.eight87.pageboy.ui.reader.control

import com.eight87.pageboy.data.library.DocumentEntity
import com.eight87.pageboy.data.library.DocumentFormat
import com.eight87.pageboy.data.library.DocumentSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Phase C.9 — debounce + record + read for [DefaultScrollPersistence].
 * Uses [kotlinx.coroutines.test.runTest] virtual time so the debounce
 * window is exercised deterministically.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ScrollPersistenceTest {

  private class RecordingDocSource(initial: List<DocumentEntity> = emptyList()) : DocumentSource {
    private val docs = initial.associateBy { it.documentId }.toMutableMap()
    val writes = mutableListOf<Triple<String, Long, Float>>()
    override fun observeDocuments(): Flow<List<DocumentEntity>> = flowOf(docs.values.toList())
    override fun observeRecents(limit: Int): Flow<List<DocumentEntity>> = flowOf(emptyList())
    override fun observeCollections(): Flow<List<String>> = flowOf(emptyList())
    override suspend fun findById(id: String): DocumentEntity? = docs[id]
    override suspend fun setPinned(id: String, pinned: Boolean) {}
    override suspend fun recordOpen(id: String) {}
    override suspend fun setReadProgress(id: String, positionMs: Long, fraction: Float) {
      writes += Triple(id, positionMs, fraction)
      docs[id] = (docs[id] ?: return).copy(
        lastReadPositionMs = positionMs,
        readFraction = fraction,
      )
    }
  }

  private fun entity(id: String, position: Long = 0L, fraction: Float = 0f) = DocumentEntity(
    documentId = id,
    treeUriString = "content://tree",
    relativePath = "x.md",
    documentUriString = "content://doc/$id",
    title = id,
    fileName = "x.md",
    format = DocumentFormat.id(DocumentFormat.Markdown),
    sizeBytes = 0L,
    mtimeMs = 0L,
    collection = null,
    addedAt = 0L,
    lastReadPositionMs = position,
    readFraction = fraction,
  )

  @Test
  fun `lastPosition returns null when the document was never scrolled`() = runTest {
    val source = RecordingDocSource(listOf(entity("doc-1")))
    val persistence = DefaultScrollPersistence(
      applicationScope = TestScope(testScheduler),
      documentSource = source,
      debounceMs = 100L,
    )
    assertNull(persistence.lastPosition("doc-1"))
  }

  @Test
  fun `lastPosition decodes a non-zero stored position`() = runTest {
    val encoded = (3L shl 20) or 524288L // page 3, fraction ≈ 0.5
    val source = RecordingDocSource(listOf(entity("doc-1", position = encoded, fraction = 0.5f)))
    val persistence = DefaultScrollPersistence(
      applicationScope = TestScope(testScheduler),
      documentSource = source,
      debounceMs = 100L,
    )
    val pos = persistence.lastPosition("doc-1")
    assertNotNull(pos)
    assertEquals(3, pos!!.pageIndex)
    assertEquals(0.5f, pos.offsetFraction, 0.001f)
  }

  @Test
  fun `lastPosition returns null for a missing document`() = runTest {
    val source = RecordingDocSource(emptyList())
    val persistence = DefaultScrollPersistence(
      applicationScope = TestScope(testScheduler),
      documentSource = source,
      debounceMs = 100L,
    )
    assertNull(persistence.lastPosition("never-existed"))
  }

  @Test
  fun `bursts of recordPosition collapse into one debounced write`() = runTest {
    val source = RecordingDocSource(listOf(entity("doc-1")))
    val scope = TestScope(testScheduler)
    val persistence = DefaultScrollPersistence(
      applicationScope = scope,
      documentSource = source,
      debounceMs = 200L,
    )
    persistence.recordPosition("doc-1", ScrollPosition(pageIndex = 1, offsetFraction = 0.1f))
    persistence.recordPosition("doc-1", ScrollPosition(pageIndex = 2, offsetFraction = 0.2f))
    persistence.recordPosition("doc-1", ScrollPosition(pageIndex = 3, offsetFraction = 0.3f))
    // Before the debounce window elapses there are no writes.
    advanceTimeBy(50L)
    assertEquals(0, source.writes.size)
    // After the window the last position lands.
    advanceUntilIdle()
    assertEquals(1, source.writes.size)
    val (id, _, fraction) = source.writes.single()
    assertEquals("doc-1", id)
    assertEquals(0.3f, fraction, 0.001f)
  }

  @Test
  fun `different documents debounce independently`() = runTest {
    val source = RecordingDocSource(listOf(entity("doc-1"), entity("doc-2")))
    val scope = TestScope(testScheduler)
    val persistence = DefaultScrollPersistence(
      applicationScope = scope,
      documentSource = source,
      debounceMs = 100L,
    )
    persistence.recordPosition("doc-1", ScrollPosition(0, 0.1f))
    persistence.recordPosition("doc-2", ScrollPosition(0, 0.2f))
    advanceUntilIdle()
    assertEquals(2, source.writes.size)
  }
}
