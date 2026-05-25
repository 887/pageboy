package com.eight87.pageboy.data.openwith

import android.app.Application
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.eight87.pageboy.TestApplication
import com.eight87.pageboy.data.library.DocumentFormat
import com.eight87.pageboy.data.library.DocumentSourceKind
import com.eight87.pageboy.data.library.LibraryDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Phase N.13 — `AdHocDocumentStore` Robolectric + in-memory Room test.
 * Covers createAdHoc + keepAdHoc paths (Kept + CannotPersist surfaces).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = TestApplication::class)
class AdHocDocumentStoreTest {

  private lateinit var db: LibraryDatabase
  private lateinit var store: RoomAdHocDocumentStore

  @Before
  fun setUp() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    db = Room.inMemoryDatabaseBuilder(app, LibraryDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    store = RoomAdHocDocumentStore(
      documentDao = db.documentDao(),
      contentResolver = app.contentResolver,
    )
  }

  @After
  fun tearDown() {
    db.close()
  }

  @Test
  fun `createAdHoc inserts row with AdHocOpen ephemeral source`() = runBlocking {
    val uri = Uri.parse("content://test/note.md")
    val id = store.createAdHoc(uri = uri, format = DocumentFormat.Markdown, displayName = "note.md")
    val row = db.documentDao().findById(id)
    assertNotNull(row)
    val source = row!!.toSourceKind()
    assertTrue(source is DocumentSourceKind.AdHocOpen)
    val ad = source as DocumentSourceKind.AdHocOpen
    assertTrue(ad.ephemeral)
    assertEquals(uri.toString(), ad.uri)
    assertEquals("note.md", row.fileName)
    assertEquals(DocumentFormat.id(DocumentFormat.Markdown), row.format)
  }

  @Test
  fun `createAdHoc twice for same URI returns same id and preserves per-doc state`() = runBlocking {
    val uri = Uri.parse("content://test/twice.pdf")
    val id1 = store.createAdHoc(uri, DocumentFormat.Pdf, "twice.pdf")
    db.documentDao().setPinned(id1, true)
    db.documentDao().setLastOpenedAt(id1, 12345L)
    val id2 = store.createAdHoc(uri, DocumentFormat.Pdf, "twice.pdf")
    assertEquals(id1, id2)
    val row = db.documentDao().findById(id2)!!
    assertTrue("pinned preserved across recreate", row.pinned)
    assertEquals(12345L, row.lastOpenedAt)
  }

  @Test
  fun `keepAdHoc that succeeds flips ephemeral to false`() = runBlocking {
    // Robolectric's ShadowContentResolver does not throw on
    // takePersistableUriPermission for any URI; that lets us assert the
    // happy-path side-effect (the row's source flips ephemeral=false).
    // The SecurityException path is unit-tested at the SDK boundary
    // via the surrounding try/catch in RoomAdHocDocumentStore.keepAdHoc.
    val uri = Uri.parse("content://test/keep.pdf")
    val id = store.createAdHoc(uri, DocumentFormat.Pdf, "keep.pdf")
    val result = store.keepAdHoc(id)
    assertEquals(KeepResult.Kept, result)
    val row = db.documentDao().findById(id)!!
    val source = row.toSourceKind() as DocumentSourceKind.AdHocOpen
    assertFalse("ephemeral should flip to false after a successful keep", source.ephemeral)
  }

  @Test
  fun `keepAdHoc on a non-existent documentId returns CannotPersist`() = runBlocking {
    val result = store.keepAdHoc("does-not-exist")
    assertTrue("expected CannotPersist, got $result", result is KeepResult.CannotPersist)
  }

  @Test
  fun `keepAdHoc on a library-root row returns NotAdHoc`() = runBlocking {
    val uri = Uri.parse("content://test/lib.pdf")
    val id = store.createAdHoc(uri, DocumentFormat.Pdf, "lib.pdf")
    // Flip the row's source to LibraryRoot.
    db.documentDao().setSourceJson(
      id,
      com.eight87.pageboy.data.library.DocumentSourceCodec.encode(
        DocumentSourceKind.LibraryRoot(rootTreeUriString = "content://tree/x"),
      ),
    )
    val result = store.keepAdHoc(id)
    assertEquals(KeepResult.NotAdHoc, result)
  }

  @Test
  fun `entity sourceJson defaults to LibraryRoot for legacy rows`() {
    val entity = com.eight87.pageboy.data.library.DocumentEntity(
      documentId = "legacy",
      treeUriString = "content://tree/legacy",
      relativePath = "old.pdf",
      documentUriString = "content://tree/legacy/old.pdf",
      title = "old",
      fileName = "old.pdf",
      format = DocumentFormat.id(DocumentFormat.Pdf),
      // sourceJson = null (legacy path) — toSourceKind treats as LibraryRoot.
    )
    val source = entity.toSourceKind()
    assertTrue(source is DocumentSourceKind.LibraryRoot)
    assertEquals("content://tree/legacy", (source as DocumentSourceKind.LibraryRoot).rootTreeUriString)
    assertFalse(entity.sourceJson != null)
  }
}
