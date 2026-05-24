package com.eight87.pageboy.format.mobi

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.eight87.pageboy.format.mobi.internal.MobiImage
import java.io.ByteArrayInputStream

/**
 * Phase Q — custom WebViewClient that resolves `pageboy://mobi/<id>`
 * image URLs to the in-memory bytes the parser extracted.
 *
 * The HTML body the parser produces contains `<img src="kindle:...">`
 * or `<img recindex="N">` style references that the Mobipocket spec
 * mandates. `MobiHtmlRewriter` rewrites those to
 * `pageboy://mobi/<recindex>` before the body is loaded; this client
 * intercepts requests against that scheme and returns a
 * [WebResourceResponse] backed by the corresponding [MobiImage] bytes.
 *
 * Stays under 80 LOC per R.X.4 / Q.4 file budget.
 */
internal class MobiWebViewClient(
  private val images: Map<String, MobiImage>,
) : WebViewClient() {

  override fun shouldInterceptRequest(
    view: WebView?,
    request: WebResourceRequest?,
  ): WebResourceResponse? {
    val url = request?.url ?: return null
    if (url.scheme != SCHEME || url.host != HOST) return null
    val recindex = url.lastPathSegment ?: return null
    val image = images[recindex] ?: return EMPTY_RESPONSE
    return WebResourceResponse(
      image.mime,
      "binary",
      ByteArrayInputStream(image.bytes),
    )
  }

  override fun shouldOverrideUrlLoading(
    view: WebView?,
    request: WebResourceRequest?,
  ): Boolean {
    // Phase Q v1: in-document anchors only. External links are
    // swallowed rather than opened (the reader chrome handles share /
    // external-open via its own toolbar in a later phase).
    val url = request?.url?.toString() ?: return false
    return !url.startsWith("about:") && !url.startsWith("data:")
  }

  internal companion object {
    const val SCHEME = "pageboy"
    const val HOST = "mobi"
    const val URL_PREFIX = "$SCHEME://$HOST/"

    private val EMPTY_RESPONSE = WebResourceResponse(
      "application/octet-stream",
      "binary",
      ByteArrayInputStream(ByteArray(0)),
    )
  }
}
