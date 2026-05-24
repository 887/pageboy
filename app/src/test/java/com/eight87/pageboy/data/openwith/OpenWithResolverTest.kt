package com.eight87.pageboy.data.openwith

import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.room.Room
import com.eight87.pageboy.TestApplication
import com.eight87.pageboy.data.library.DocumentFormat
import com.eight87.pageboy.data.library.LibraryDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Phase N.13 — Robolectric resolver tests. The Shadow ContentResolver
 * lets us register fake content:// URIs + their declared MIME, then
 * assert the resolver classifies + dispatches into the right
 * [OpenWithResult] variant.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = TestApplication::class)
class OpenWithResolverTest {

  private lateinit var db: LibraryDatabase

  @Before
  fun setUp() {
    val app = ApplicationProvider.getApplicationContext<android.app.Application>()
    db = Room.inMemoryDatabaseBuilder(app, LibraryDatabase::class.java)
      .allowMainThreadQueries()
      .build()
  }

  @After
  fun tearDown() {
    db.close()
  }

  private fun resolver(autoClassify: Boolean = true): AndroidOpenWithResolver {
    val app = ApplicationProvider.getApplicationContext<android.app.Application>()
    val store = RoomAdHocDocumentStore(db.documentDao(), app.contentResolver)
    return AndroidOpenWithResolver(
      contentResolver = app.contentResolver,
      adHocDocumentStore = store,
      autoClassifyUnknownMime = { autoClassify },
    )
  }

  private fun registerStream(uri: Uri, bytes: ByteArray, @Suppress("UNUSED_PARAMETER") mime: String?) {
    val app = ApplicationProvider.getApplicationContext<android.app.Application>()
    shadowOf(app.contentResolver).registerInputStream(uri, bytes.inputStream())
  }

  private fun viewIntent(uri: Uri, mime: String? = null): Intent {
    // setDataAndType is required: setData(uri) then setType(mime) wipes
    // the URI back to null per Android's Intent API.
    val intent = Intent(Intent.ACTION_VIEW).apply {
      if (mime != null) setDataAndType(uri, mime) else data = uri
      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    return intent
  }

  @Test
  fun `pdf with magic header classifies as Ready Pdf`() = runBlocking {
    val uri = Uri.parse("content://test/doc1.pdf")
    val bytes = "%PDF-1.7\n%%EOF".toByteArray(Charsets.US_ASCII)
    registerStream(uri, bytes, "application/pdf")
    val result = resolver().resolve(viewIntent(uri, "application/pdf"))
    assertTrue("expected Ready, got $result", result is OpenWithResult.Ready)
    val ready = result as OpenWithResult.Ready
    assertTrue("ephemeral is true on a fresh intent", ready.ephemeral)
    val row = db.documentDao().findById(ready.documentId)!!
    assertEquals(DocumentFormat.id(DocumentFormat.Pdf), row.format)
  }

  @Test
  fun `markdown extension on octet-stream is classified Markdown`() = runBlocking {
    val uri = Uri.parse("content://test/notes.md")
    val bytes = "# Hello".toByteArray()
    registerStream(uri, bytes, "application/octet-stream")
    val result = resolver().resolve(viewIntent(uri, "application/octet-stream"))
    assertTrue(result is OpenWithResult.Ready)
    val row = db.documentDao().findById((result as OpenWithResult.Ready).documentId)!!
    assertEquals(DocumentFormat.id(DocumentFormat.Markdown), row.format)
  }

  @Test
  fun `file scheme is refused`() = runBlocking {
    val uri = Uri.parse("file:///sdcard/x.pdf")
    val result = resolver().resolve(viewIntent(uri, "application/pdf"))
    assertTrue(result is OpenWithResult.PermissionRefused)
  }

  @Test
  fun `intent without data returns PermissionRefused`() = runBlocking {
    val intent = Intent(Intent.ACTION_VIEW)
    val result = resolver().resolve(intent)
    assertTrue(result is OpenWithResult.PermissionRefused)
  }

  @Test
  fun `unknown format returns UnknownFormat`() = runBlocking {
    val uri = Uri.parse("content://test/payload.bin")
    val bytes = byteArrayOf(0x00, 0x01, 0x02, 0x03)
    registerStream(uri, bytes, "application/octet-stream")
    val result = resolver().resolve(viewIntent(uri, "application/octet-stream"))
    assertTrue(result is OpenWithResult.UnknownFormat)
  }

  @Test
  fun `repeated resolve for same URI returns same documentId`() = runBlocking {
    val uri = Uri.parse("content://test/repeated.pdf")
    val bytes = "%PDF-1.4\n".toByteArray()
    registerStream(uri, bytes, "application/pdf")
    val a = resolver().resolve(viewIntent(uri, "application/pdf")) as OpenWithResult.Ready
    val b = resolver().resolve(viewIntent(uri, "application/pdf")) as OpenWithResult.Ready
    assertEquals(a.documentId, b.documentId)
  }
}
