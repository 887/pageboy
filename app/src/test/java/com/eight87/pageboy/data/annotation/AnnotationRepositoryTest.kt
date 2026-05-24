package com.eight87.pageboy.data.annotation

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.eight87.pageboy.TestApplication
import com.eight87.pageboy.data.library.LibraryDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Phase G.7 — Robolectric + in-memory Room test for
 * [AnnotationRepository] (the concrete impl behind
 * [AnnotationSource] + [AnnotationStore]).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = TestApplication::class)
class AnnotationRepositoryTest {

  private lateinit var db: LibraryDatabase
  private lateinit var repo: AnnotationRepository

  @Before
  fun setUp() {
    db = Room.inMemoryDatabaseBuilder(
      ApplicationProvider.getApplicationContext(),
      LibraryDatabase::class.java,
    ).allowMainThreadQueries().build()
    repo = AnnotationRepository(db.annotationDao(), now = { 42_000L })
  }

  @After
  fun tearDown() {
    db.close()
  }

  private fun entity(id: String, doc: String = "doc-x") = AnnotationEntity(
    id = id,
    documentId = doc,
    pageIndex = 0,
    kind = AnnotationKind.Highlight.name,
    payloadJson = "{}",
    colorArgb = 0xFFFFEB3B.toInt(),
    pageWidthPt = 100f,
    pageHeightPt = 100f,
    createdAt = 1_000L,
    modifiedAt = 1_000L,
  )

  @Test
  fun `add and observe`() = runBlocking {
    repo.add(entity("a"))
    val list = repo.observe("doc-x").first()
    assertEquals(1, list.size)
    assertEquals("a", list.single().id)
  }

  @Test
  fun `delete soft-deletes via source observe`() = runBlocking {
    repo.add(entity("a"))
    repo.delete("a")
    val list = repo.observe("doc-x").first()
    assertTrue(list.isEmpty())
  }

  @Test
  fun `update bumps modifiedAt using injected clock`() = runBlocking {
    repo.add(entity("a"))
    val original = repo.observe("doc-x").first().single()
    repo.update(original.copy(colorArgb = 0xFF000000.toInt()))
    val after = db.annotationDao().findById("a")!!
    assertEquals(42_000L, after.modifiedAt)
    assertEquals(0xFF000000.toInt(), after.colorArgb)
  }

  @Test
  fun `list returns a snapshot`() = runBlocking {
    repo.add(entity("a"))
    repo.add(entity("b"))
    val snap = repo.list("doc-x")
    assertEquals(2, snap.size)
  }

  @Test
  fun `restore undoes a soft-delete`() = runBlocking {
    repo.add(entity("a"))
    repo.delete("a")
    repo.restore("a")
    assertEquals(1, repo.observe("doc-x").first().size)
  }

  @Test
  fun `per-page observe scopes by page index`() = runBlocking {
    repo.add(entity("a").copy(pageIndex = 0))
    repo.add(entity("b").copy(pageIndex = 1))
    val page0 = repo.observeForPage("doc-x", 0).first()
    val page1 = repo.observeForPage("doc-x", 1).first()
    assertEquals(1, page0.size)
    assertEquals("a", page0.single().id)
    assertEquals("b", page1.single().id)
  }

  @Test
  fun `documents are scoped by documentId`() = runBlocking {
    repo.add(entity("a", doc = "doc-x"))
    repo.add(entity("b", doc = "doc-y"))
    assertEquals(1, repo.observe("doc-x").first().size)
    assertEquals(1, repo.observe("doc-y").first().size)
    assertNotNull(db.annotationDao().findById("b"))
  }
}
