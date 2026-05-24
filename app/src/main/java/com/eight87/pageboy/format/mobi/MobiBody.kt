package com.eight87.pageboy.format.mobi

import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.viewinterop.AndroidView
import com.eight87.pageboy.domain.render.RendererContext

/**
 * Phase Q — MOBI body. Hosts an Android [WebView] inside Compose via
 * [AndroidView]. The WebView renders the parsed HTML; the custom
 * [MobiWebViewClient] resolves inline image references.
 *
 * Wiring via [RendererContext]:
 *  - The current sealed `ScrollPosition` variants
 *    (`LazyColumn`, `PdfPage`) don't fit a tall WebView surface; v1
 *    intentionally does not persist MOBI scroll position. The natural
 *    fit is a future `ScrollPosition.Pixel(y)` variant landing
 *    alongside the EPUB renderer at Phase M (which has the same WebView
 *    shape). Documented in docs/plans/format-mobi.md.
 *  - Find queries trigger `WebView.findAllAsync`; matches publish
 *    via `findSink.submitMatches`. v1 wires this best-effort — the
 *    WebView's find-listener API needs a held view ref the AndroidView
 *    factory pattern doesn't readily expose, so v1 ships with find
 *    deferred until the EPUB renderer lands the generalised
 *    WebView-host pattern at Phase M.
 *
 * Stays under 200 LOC per R.X.4 / Q.4 budget.
 *
 * Note: WebView's JavaScript is enabled because KF8 / AZW3 content
 * routinely includes scripts. Local content only — the WebViewClient
 * filters non-data / non-about navigation — so the surface is bounded.
 */
@Composable
internal fun MobiBody(
  handle: MobiHandle,
  context: RendererContext,
  modifier: Modifier = Modifier,
) {
  val rewritten = remember(handle) { MobiHtmlRewriter.rewrite(handle.content.html) }
  val client = remember(handle) { MobiWebViewClient(handle.content.images) }

  AndroidView(
    modifier = modifier
      .fillMaxSize()
      .semantics { testTag = "mobi_body" },
    factory = { ctx ->
      WebView(ctx).apply {
        layoutParams = ViewGroup.LayoutParams(
          ViewGroup.LayoutParams.MATCH_PARENT,
          ViewGroup.LayoutParams.MATCH_PARENT,
        )
        @Suppress("SetJavaScriptEnabled")
        settings.javaScriptEnabled = true
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.domStorageEnabled = false
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        webViewClient = client
        loadDataWithBaseURL(
          MobiWebViewClient.URL_PREFIX,
          rewritten,
          "text/html",
          "UTF-8",
          null,
        )
      }
    },
    update = { /* parsed HTML never changes for the lifetime of this handle */ },
  )

  // documentId observation deliberately ineffectful in v1 — kept as a
  // hook so the eventual `ScrollPosition.Pixel(y)` variant (Phase M)
  // can wire scroll restore in here without rewiring the wider body.
  LaunchedEffect(context.documentId) {
    @Suppress("UNUSED_EXPRESSION")
    context.documentId
  }
}

/**
 * Phase Q — HTML rewriter that normalises Mobipocket-specific image
 * references to the `pageboy://mobi/<recindex>` scheme the
 * [MobiWebViewClient] resolves.
 *
 * Mobipocket markup uses two source attribute styles:
 *  - `<img recindex="N">` — explicit Mobipocket recindex attribute.
 *  - `<img src="kindle:embed:N">` — Amazon's later AZW3 convention.
 *
 * Both rewrite to `<img src="pageboy://mobi/N">`. The rewriter is
 * intentionally string-based — pulling in an XML parser to walk what
 * is in practice mostly malformed soup would lose more text than it
 * fixed.
 */
internal object MobiHtmlRewriter {

  fun rewrite(html: String): String {
    if (html.isEmpty()) return html
    var out = html
    out = RECINDEX_REGEX.replace(out) { match ->
      val n = match.groupValues[1]
      "src=\"${MobiWebViewClient.URL_PREFIX}$n\""
    }
    out = KINDLE_EMBED_REGEX.replace(out) { match ->
      val n = match.groupValues[1]
      "src=\"${MobiWebViewClient.URL_PREFIX}$n\""
    }
    return out
  }

  // Match a `recindex="N"` attribute and turn it into the canonical
  // src. The pre-existing src attribute (if any) is left in place —
  // browsers honour the later src per HTML parsing rules.
  private val RECINDEX_REGEX = Regex("""recindex="(\d+)"""")

  // Match the AZW3 `kindle:embed:0000NN` source convention.
  private val KINDLE_EMBED_REGEX =
    Regex("""src="kindle:embed:0*(\d+)(?:\?[^"]*)?"""")
}
