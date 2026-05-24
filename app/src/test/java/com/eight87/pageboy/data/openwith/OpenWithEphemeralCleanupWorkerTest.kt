package com.eight87.pageboy.data.openwith

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.eight87.pageboy.TestApplication
import com.eight87.pageboy.data.library.DocumentEntity
import com.eight87.pageboy.data.library.DocumentFormat
import com.eight87.pageboy.data.library.DocumentSourceCodec
import com.eight87.pageboy.data.library.DocumentSourceKind
import com.eight87.pageboy.data.library.LibraryDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Phase N.13 — verify the 7-day retention window. Builds the worker
 * with a fixed `now()` and three rows: a fresh ephemeral, a stale
 * ephemeral, and a stale-but-non-ephemeral (`Kept`). Only the stale
 * ephemeral should be deleted.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = TestApplication::class)
class OpenWithEphemeralCleanupWorkerTest {

  private lateinit var db: LibraryDatabase

  private val fixedNow = 1_700_000_000_000L
  private val sevenDaysMs = 7L * 24L * 60L * 60L * 1000L

  @Before
  fun setUp() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    db = Room.inMemoryDatabaseBuilder(app, LibraryDatabase::class.java)
      .allowMainThreadQueries()
      .build()
  }

  @After
  fun tearDown() {
    db.close()
  }

  private fun row(
    id: String,
    source: DocumentSourceKind,
    lastOpened: Long?,
    pinned: Boolean = false,
  ): DocumentEntity = DocumentEntity(
    documentId = id,
    treeUriString = "",
    relativePath = "$id.pdf",
    documentUriString = "content://test/$id",
    title = id,
    fileName = "$id.pdf",
    format = DocumentFormat.id(DocumentFormat.Pdf),
    addedAt = fixedNow - 30L * 24L * 60L * 60L * 1000L, // 30 days ago
    lastOpenedAt = lastOpened,
    sourceJson = DocumentSourceCodec.encode(source),
    pinned = pinned,
  )

  @Test
  fun `stale ephemeral rows are deleted and fresh ephemeral rows survive`() = runBlocking {
    val dao = db.documentDao()
    // 8 days ago — stale.
    dao.insertOne(row("stale", DocumentSourceKind.AdHocOpen("uri-stale", ephemeral = true), fixedNow - 8L * 24L * 60L * 60L * 1000L))
    // 1 day ago — fresh.
    dao.insertOne(row("fresh", DocumentSourceKind.AdHocOpen("uri-fresh", ephemeral = true), fixedNow - 1L * 24L * 60L * 60L * 1000L))
    // 8 days ago but ephemeral = false (user tapped Keep).
    dao.insertOne(row("kept", DocumentSourceKind.AdHocOpen("uri-kept", ephemeral = false), fixedNow - 8L * 24L * 60L * 60L * 1000L))
    // 8 days ago, ephemeral = true, but pinned — preserved.
    dao.insertOne(row("pinned", DocumentSourceKind.AdHocOpen("uri-pinned", ephemeral = true), fixedNow - 8L * 24L * 60L * 60L * 1000L, pinned = true))
    // Library row (Phase B-style scan).
    dao.insertOne(row("lib", DocumentSourceKind.LibraryRoot("content://tree/lib"), fixedNow - 100L * 24L * 60L * 60L * 1000L))

    val worker = TestListenableWorkerBuilder<OpenWithEphemeralCleanupWorker>(
      ApplicationProvider.getApplicationContext(),
    )
      .setWorkerFactory(object : androidx.work.WorkerFactory() {
        override fun createWorker(
          appContext: android.content.Context,
          workerClassName: String,
          workerParameters: androidx.work.WorkerParameters,
        ) = OpenWithEphemeralCleanupWorker(
          context = appContext,
          params = workerParameters,
          documentDao = dao,
          retentionDaysProvider = { 7 },
          now = { fixedNow },
        )
      })
      .build()

    val result = worker.doWork()
    assertEquals(ListenableWorker.Result.success(), result)
    assertNull("stale ephemeral row should be deleted", dao.findById("stale"))
    assertNotNull("fresh ephemeral row should remain", dao.findById("fresh"))
    assertNotNull("kept (non-ephemeral) row should remain", dao.findById("kept"))
    assertNotNull("pinned row should remain even if stale + ephemeral", dao.findById("pinned"))
    assertNotNull("library row should remain", dao.findById("lib"))
  }

  @Test
  fun `worker is no-op when no AdHoc rows exist`() = runBlocking {
    val dao = db.documentDao()
    dao.insertOne(row("lib", DocumentSourceKind.LibraryRoot("content://tree/lib"), null))
    val worker = TestListenableWorkerBuilder<OpenWithEphemeralCleanupWorker>(
      ApplicationProvider.getApplicationContext(),
    )
      .setWorkerFactory(object : androidx.work.WorkerFactory() {
        override fun createWorker(
          appContext: android.content.Context,
          workerClassName: String,
          workerParameters: androidx.work.WorkerParameters,
        ) = OpenWithEphemeralCleanupWorker(
          appContext, workerParameters, dao, { 7 }, { fixedNow },
        )
      })
      .build()
    val result = worker.doWork()
    assertEquals(ListenableWorker.Result.success(), result)
    assertNotNull(dao.findById("lib"))
  }
}
