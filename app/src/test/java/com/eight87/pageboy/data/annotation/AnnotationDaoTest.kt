package com.eight87.pageboy.data.annotation

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.eight87.pageboy.TestApplication
import com.eight87.pageboy.data.library.LibraryDatabase
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
 * Phase G.7 — Robolectric + in-memory Room test for [AnnotationDao].
 *
 * Covers insert / observe / per-page filter / soft-delete / restore /
 * hard-delete-by-document, exercising the path the chrome relies on:
 *
 *  - Annotations land on the document they belong to and survive the
 *    observe round-trip (per-document + per-page queries return the
 *    rows we inserted).
 *  - Soft-delete hides a row from observers but keeps it on disk;
 *    `findById` still returns it; `restore` brings it back into the
 *    observe stream.
 *  - Hard delete sweeps every row for a documentId, no soft-delete
 *    intermediate state.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = TestApplication::class)
class AnnotationDaoTest {

  private lateinit var db: LibraryDatabase
  private lateinit var dao: AnnotationDao

  @Before
  fun setUp() {
    db = Room.inMemoryDatabaseBuilder(
      ApplicationProvider.getApplicationContext(),
      LibraryDatabase::class.java,
    ).allowMainThreadQueries().build()
    dao = db.annotationDao()
  }

  @After
  fun tearDown() {
    db.close()
  }

  private fun row(
    id: String,
    documentId: String = "doc-1",
    pageIndex: Int = 0,
    kind: AnnotationKind = AnnotationKind.Highlight,
    payload: String = """{"kind":"HighlightPayload","rect":{"left":0,"bottom":0,"right":10,"top":10}}""",
    color: Int = 0xFFFFEB3B.toInt(),
    created: Long = 1_000L,
  ) = AnnotationEntity(
    id = id,
    documentId = documentId,
    pageIndex = pageIndex,
    kind = kind.name,
    payloadJson = payload,
    colorArgb = color,
    pageWidthPt = 612f,
    pageHeightPt = 792f,
    createdAt = created,
    modifiedAt = created,
  )

  @Test
  fun `insert and observe per document`() = runBlocking {
    dao.insert(row("a"))
    dao.insert(row("b", pageIndex = 1))
    dao.insert(row("c", documentId = "doc-2"))

    val docOne = dao.observeForDocument("doc-1").first()
    assertEquals(2, docOne.size)
    assertTrue(docOne.any { it.id == "a" })
    assertTrue(docOne.any { it.id == "b" })

    val docTwo = dao.observeForDocument("doc-2").first()
    assertEquals(1, docTwo.size)
    assertEquals("c", docTwo.single().id)
  }

  @Test
  fun `observe per page filters by pageIndex`() = runBlocking {
    dao.insert(row("a", pageIndex = 0))
    dao.insert(row("b", pageIndex = 1))
    dao.insert(row("c", pageIndex = 0))

    val page0 = dao.observeForPage("doc-1", 0).first()
    val page1 = dao.observeForPage("doc-1", 1).first()
    assertEquals(2, page0.size)
    assertEquals(1, page1.size)
    assertEquals("b", page1.single().id)
  }

  @Test
  fun `soft delete hides from observe but keeps on disk`() = runBlocking {
    dao.insert(row("a"))
    dao.softDelete("a", now = 5_000L)

    val visible = dao.observeForDocument("doc-1").first()
    assertTrue(visible.isEmpty())

    val on_disk = dao.findById("a")
    assertNotNull(on_disk)
    assertTrue(on_disk!!.isDeleted)
    assertEquals(5_000L, on_disk.modifiedAt)
  }

  @Test
  fun `restore brings a soft-deleted row back`() = runBlocking {
    dao.insert(row("a"))
    dao.softDelete("a", now = 5_000L)
    dao.restore("a", now = 6_000L)

    val visible = dao.observeForDocument("doc-1").first()
    assertEquals(1, visible.size)
    assertFalse(visible.single().isDeleted)
  }

  @Test
  fun `hard delete sweeps every row for a document`() = runBlocking {
    dao.insert(row("a"))
    dao.insert(row("b", pageIndex = 1))
    dao.insert(row("c", documentId = "doc-2"))
    dao.hardDeleteAllForDocument("doc-1")

    assertNull(dao.findById("a"))
    assertNull(dao.findById("b"))
    assertNotNull(dao.findById("c"))
  }

  @Test
  fun `listForDocument is a snapshot read`() = runBlocking {
    dao.insert(row("a"))
    dao.insert(row("b", pageIndex = 1))
    val list = dao.listForDocument("doc-1")
    assertEquals(2, list.size)
  }

  @Test
  fun `update replaces row payload but keeps id`() = runBlocking {
    dao.insert(row("a", payload = """{"kind":"HighlightPayload","rect":{"left":0,"bottom":0,"right":1,"top":1}}"""))
    val original = dao.findById("a")!!
    val replaced = original.copy(payloadJson = """{"kind":"HighlightPayload","rect":{"left":5,"bottom":5,"right":15,"top":15}}""", modifiedAt = 9_000L)
    dao.update(replaced)
    val after = dao.findById("a")!!
    assertEquals(replaced.payloadJson, after.payloadJson)
    assertEquals(9_000L, after.modifiedAt)
  }

  @Test
  fun `ordering is page-then-creation`() = runBlocking {
    dao.insert(row("a", pageIndex = 1, created = 5_000L))
    dao.insert(row("b", pageIndex = 0, created = 7_000L))
    dao.insert(row("c", pageIndex = 0, created = 6_000L))

    val docs = dao.observeForDocument("doc-1").first()
    assertEquals(listOf("c", "b", "a"), docs.map { it.id })
  }
}
