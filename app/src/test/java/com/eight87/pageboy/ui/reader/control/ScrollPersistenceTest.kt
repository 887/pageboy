package com.eight87.pageboy.ui.reader.control

import com.eight87.pageboy.data.library.DocumentEntity
import com.eight87.pageboy.data.library.DocumentFormat
import com.eight87.pageboy.data.library.DocumentSource
import com.eight87.pageboy.domain.render.ScrollPosition
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
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase C.9 + Phase F.2 — debounce + record + read for
 * [DefaultScrollPersistence]. Uses [kotlinx.coroutines.test.runTest]
 * virtual time so the debounce window is exercised deterministically.
 *
 * Post-Phase F these tests exercise both the new sealed
 * [ScrollPosition] write path (JSON-encoded) and the legacy
 * bit-packed-long fallback used to decode v1 rows that haven't been
 * re-written since the migration.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ScrollPersistenceTest {

  private class RecordingDocSource(initial: List<DocumentEntity> = emptyList()) : DocumentSource {
    private val docs = initial.associateBy { it.documentId }.toMutableMap()
    val writes = mutableListOf<Triple<String, String?, Float>>()
    override fun observeDocuments(): Flow<List<DocumentEntity>> = flowOf(docs.values.toList())
    override fun observeRecents(limit: Int): Flow<List<DocumentEntity>> = flowOf(emptyList())
    override fun observeCollections(): Flow<List<String>> = flowOf(emptyList())
    override suspend fun findById(id: String): DocumentEntity? = docs[id]
    override suspend fun setPinned(id: String, pinned: Boolean) {}
    override suspend fun recordOpen(id: String) {}
    override suspend fun setReadProgress(id: String, positionMs: Long, fraction: Float) {
      // Legacy path retained — Phase F-and-later writes flow through
      // setScrollPosition. This setter stays here for the migration
      // tests + the LibraryRepositoryTest path that touches v1 rows.
      docs[id] = (docs[id] ?: return).copy(
        lastReadPositionMs = positionMs,
        readFraction = fraction,
      )
    }
    override suspend fun setScrollPosition(
      id: String,
      positionJson: String?,
      fraction: Float,
    ) {
      writes += Triple(id, positionJson, fraction)
      docs[id] = (docs[id] ?: return).copy(
        scrollPositionJson = positionJson,
        readFraction = fraction,
        lastReadPositionMs = 0L,
      )
    }
  }

  private fun entity(
    id: String,
    positionLegacy: Long = 0L,
    fraction: Float = 0f,
    scrollJson: String? = null,
  ) = DocumentEntity(
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
    lastReadPositionMs = positionLegacy,
    readFraction = fraction,
    scrollPositionJson = scrollJson,
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
  fun `lastPosition prefers the new JSON column over the legacy long`() = runTest {
    val encoded = ScrollPosition.encode(ScrollPosition.LazyColumn(itemIndex = 7, offset = 42))
    val source = RecordingDocSource(
      listOf(entity("doc-1", scrollJson = encoded, fraction = 0.0f)),
    )
    val persistence = DefaultScrollPersistence(
      applicationScope = TestScope(testScheduler),
      documentSource = source,
      debounceMs = 100L,
    )
    val pos = persistence.lastPosition("doc-1") as? ScrollPosition.LazyColumn
    assertNotNull(pos)
    assertEquals(7, pos!!.itemIndex)
    assertEquals(42, pos.offset)
  }

  @Test
  fun `lastPosition falls back to the legacy bit-packed long for v1 rows`() = runTest {
    // v1 row — no JSON column, only the bit-packed long. Decoder
    // produces a LazyColumn variant (Markdown / TXT were the only
    // pre-Phase F writers).
    val packed = (3L shl 20) or 524288L // page 3, offset 524288
    val source = RecordingDocSource(
      listOf(entity("doc-1", positionLegacy = packed, fraction = 0.5f, scrollJson = null)),
    )
    val persistence = DefaultScrollPersistence(
      applicationScope = TestScope(testScheduler),
      documentSource = source,
      debounceMs = 100L,
    )
    val pos = persistence.lastPosition("doc-1") as? ScrollPosition.LazyColumn
    assertNotNull(pos)
    assertEquals(3, pos!!.itemIndex)
    assertEquals(524288, pos.offset)
  }

  @Test
  fun `lastPosition decodes a stored PdfPage variant`() = runTest {
    val encoded = ScrollPosition.encode(ScrollPosition.PdfPage(page = 17, ratio = 0.75f))
    val source = RecordingDocSource(
      listOf(entity("doc-1", scrollJson = encoded, fraction = 0.75f)),
    )
    val persistence = DefaultScrollPersistence(
      applicationScope = TestScope(testScheduler),
      documentSource = source,
      debounceMs = 100L,
    )
    val pos = persistence.lastPosition("doc-1") as? ScrollPosition.PdfPage
    assertNotNull(pos)
    assertEquals(17, pos!!.page)
    assertEquals(0.75f, pos.ratio, 0.0001f)
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
    persistence.recordPosition("doc-1", ScrollPosition.LazyColumn(itemIndex = 1, offset = 10))
    persistence.recordPosition("doc-1", ScrollPosition.LazyColumn(itemIndex = 2, offset = 20))
    persistence.recordPosition("doc-1", ScrollPosition.LazyColumn(itemIndex = 3, offset = 30))
    // Before the debounce window elapses there are no writes.
    advanceTimeBy(50L)
    assertEquals(0, source.writes.size)
    // After the window the last position lands as a JSON-encoded
    // LazyColumn payload.
    advanceUntilIdle()
    assertEquals(1, source.writes.size)
    val (id, json, _) = source.writes.single()
    assertEquals("doc-1", id)
    val decoded = ScrollPosition.decode(json) as? ScrollPosition.LazyColumn
    assertNotNull(decoded)
    assertEquals(3, decoded!!.itemIndex)
    assertEquals(30, decoded.offset)
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
    persistence.recordPosition("doc-1", ScrollPosition.LazyColumn(0, 10))
    persistence.recordPosition("doc-2", ScrollPosition.PdfPage(4, 0.5f))
    advanceUntilIdle()
    assertEquals(2, source.writes.size)
  }

  @Test
  fun `PDF fraction-complete derives from the page ratio by default`() = runTest {
    val source = RecordingDocSource(listOf(entity("doc-1")))
    val scope = TestScope(testScheduler)
    val persistence = DefaultScrollPersistence(
      applicationScope = scope,
      documentSource = source,
      debounceMs = 100L,
    )
    persistence.recordPosition("doc-1", ScrollPosition.PdfPage(page = 5, ratio = 0.3f))
    advanceUntilIdle()
    val (_, _, fraction) = source.writes.single()
    assertTrue("fraction should reflect ratio default", fraction in 0.2f..0.4f)
  }
}
