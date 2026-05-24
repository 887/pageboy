package com.eight87.pageboy.format.mobi

import android.net.Uri
import android.webkit.WebResourceRequest
import com.eight87.pageboy.TestApplication
import com.eight87.pageboy.format.mobi.internal.MobiImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Phase Q.7 — Robolectric tests for [MobiWebViewClient].
 *
 * Verifies the `pageboy://mobi/<recindex>` URL scheme resolves to the
 * in-memory image bytes the parser extracted.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = TestApplication::class)
class MobiWebViewClientTest {

  private val testImage = MobiImage(
    recindex = 1,
    mime = "image/png",
    bytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47),
  )

  private val client = MobiWebViewClient(mapOf("1" to testImage))

  @Test
  fun `pageboy mobi 1 resolves to the image bytes`() {
    val request = fakeRequest(Uri.parse("pageboy://mobi/1"))
    val response = client.shouldInterceptRequest(null, request)
    assertNotNull(response)
    assertEquals("image/png", response!!.mimeType)
    assertEquals("binary", response.encoding)
    val read = response.data.readBytes()
    assertEquals(4, read.size)
  }

  @Test
  fun `unknown recindex returns empty WebResourceResponse`() {
    val request = fakeRequest(Uri.parse("pageboy://mobi/99"))
    val response = client.shouldInterceptRequest(null, request)
    assertNotNull(response)
    assertEquals(0, response!!.data.readBytes().size)
  }

  @Test
  fun `non-pageboy scheme is not intercepted`() {
    val request = fakeRequest(Uri.parse("https://example.com/image.png"))
    val response = client.shouldInterceptRequest(null, request)
    assertNull(response)
  }

  private fun fakeRequest(uri: Uri): WebResourceRequest = object : WebResourceRequest {
    override fun getUrl(): Uri = uri
    override fun isForMainFrame(): Boolean = false
    override fun isRedirect(): Boolean = false
    override fun hasGesture(): Boolean = false
    override fun getMethod(): String = "GET"
    override fun getRequestHeaders(): MutableMap<String, String> = mutableMapOf()
  }
}
