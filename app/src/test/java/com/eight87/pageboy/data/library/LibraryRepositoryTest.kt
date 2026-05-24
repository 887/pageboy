package com.eight87.pageboy.data.library

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.eight87.pageboy.TestApplication
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Phase B audit — Robolectric + in-memory Room test for [LibraryRepository].
 *
 * Covers the load-bearing apply-scan path:
 *  - Fresh insert seeds defaults (pinned = false, lastOpenedAt = null).
 *  - Re-scan preserves per-document state (pinned + lastOpenedAt + read
 *    progress + addedAt) for ids that are seen again.
 *  - Disappeared files are soft-deleted (is_missing = 1) but state stays
 *    on the row so a re-add restores it.
 *  - A subsequent scan that re-sees a previously-missing id flips
 *    is_missing back to 0 and the preserved state is intact.
 *  - `deleteRoot` hard-deletes every row from that root.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = TestApplication::class)
class LibraryRepositoryTest {

  private lateinit var db: LibraryDatabase
  private lateinit var repo: LibraryRepository

  private val rootA = "content://tree/A"
  private val rootB = "content://tree/B"

  @Before
  fun setUp() {
    db = Room.inMemoryDatabaseBuilder(
      ApplicationProvider.getApplicationContext(),
      LibraryDatabase::class.java,
    ).allowMainThreadQueries().build()
    repo = LibraryRepository(db)
  }

  @After
  fun tearDown() {
    db.close()
  }

  private fun scanned(id: String, root: String, name: String, format: DocumentFormat = DocumentFormat.Pdf) =
    ScannedDocument(
      documentId = id,
      treeUriString = root,
      relativePath = name,
      documentUriString = "$root/$name",
      title = name.substringBeforeLast('.'),
      fileName = name,
      format = format,
      sizeBytes = 1024L,
      mtimeMs = 1L,
      collection = null,
    )

  @Test
  fun `applyScan inserts new documents with default per-document state`() = runBlocking {
    val snapshot = ScanSnapshot(listOf(scanned("a", rootA, "a.pdf"), scanned("b", rootA, "b.pdf")))
    repo.applyScan(snapshot, touchedRoots = setOf(rootA))

    val docs = repo.observeDocuments().first()
    assertEquals(2, docs.size)
    val a = docs.first { it.documentId == "a" }
    assertFalse(a.pinned)
    assertNull(a.lastOpenedAt)
    assertEquals(0L, a.lastReadPositionMs)
    assertFalse(a.isMissing)
  }

  @Test
  fun `applyScan preserves pinned and lastOpenedAt across rescans`() = runBlocking {
    repo.applyScan(ScanSnapshot(listOf(scanned("a", rootA, "a.pdf"))), touchedRoots = setOf(rootA))
    repo.setPinned("a", pinned = true)
    repo.recordOpen("a")
    repo.setReadProgress("a", positionMs = 500L, fraction = 0.5f)

    val before = repo.findById("a")!!
    assertTrue(before.pinned)
    assertNotNull(before.lastOpenedAt)
    assertEquals(500L, before.lastReadPositionMs)

    // Rescan with the same id (same file, fresh walk).
    repo.applyScan(ScanSnapshot(listOf(scanned("a", rootA, "a.pdf"))), touchedRoots = setOf(rootA))
    val after = repo.findById("a")!!
    assertTrue("pinned must survive rescan", after.pinned)
    assertEquals("lastOpenedAt must survive rescan", before.lastOpenedAt, after.lastOpenedAt)
    assertEquals("read position must survive rescan", 500L, after.lastReadPositionMs)
    assertEquals("addedAt must survive rescan", before.addedAt, after.addedAt)
    assertFalse(after.isMissing)
  }

  @Test
  fun `applyScan soft-deletes documents that disappear from their root`() = runBlocking {
    repo.applyScan(
      ScanSnapshot(listOf(scanned("a", rootA, "a.pdf"), scanned("b", rootA, "b.pdf"))),
      touchedRoots = setOf(rootA),
    )
    // 'b' disappears.
    repo.applyScan(ScanSnapshot(listOf(scanned("a", rootA, "a.pdf"))), touchedRoots = setOf(rootA))

    // observeDocuments filters out is_missing rows.
    val visible = repo.observeDocuments().first()
    assertEquals(listOf("a"), visible.map { it.documentId })
    // But the row is still there with state preserved.
    val b = repo.findById("b")
    assertNotNull("soft-deleted row should still exist", b)
    assertTrue("soft-deleted row should be is_missing", b!!.isMissing)
  }

  @Test
  fun `rescan that re-sees a previously-missing id un-marks it`() = runBlocking {
    repo.applyScan(ScanSnapshot(listOf(scanned("a", rootA, "a.pdf"))), touchedRoots = setOf(rootA))
    repo.setPinned("a", pinned = true)
    // Disappear.
    repo.applyScan(ScanSnapshot(emptyList()), touchedRoots = setOf(rootA))
    assertTrue(repo.findById("a")!!.isMissing)
    // Reappear.
    repo.applyScan(ScanSnapshot(listOf(scanned("a", rootA, "a.pdf"))), touchedRoots = setOf(rootA))
    val after = repo.findById("a")!!
    assertFalse("re-seen row should clear is_missing", after.isMissing)
    assertTrue("pinned should survive the round-trip", after.pinned)
  }

  @Test
  fun `applyScan touching only one root leaves the other root alone`() = runBlocking {
    repo.applyScan(
      ScanSnapshot(listOf(scanned("a", rootA, "a.pdf"), scanned("z", rootB, "z.pdf"))),
      touchedRoots = setOf(rootA, rootB),
    )
    // Rescan only root A; 'a' still present.
    repo.applyScan(ScanSnapshot(listOf(scanned("a", rootA, "a.pdf"))), touchedRoots = setOf(rootA))
    val docs = repo.observeDocuments().first().map { it.documentId }.toSet()
    assertTrue("rootB document must be untouched", "z" in docs)
    assertTrue("rootA document must still be present", "a" in docs)
  }

  @Test
  fun `deleteRoot hard-deletes every document from that root`() = runBlocking {
    repo.applyScan(
      ScanSnapshot(listOf(scanned("a", rootA, "a.pdf"), scanned("z", rootB, "z.pdf"))),
      touchedRoots = setOf(rootA, rootB),
    )
    repo.deleteRoot(rootA)
    val ids = repo.allDocumentIds()
    assertEquals(setOf("z"), ids)
    assertNull(repo.findById("a"))
  }
}
