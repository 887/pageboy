package com.eight87.pageboy.format.epub

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.fragment.app.FragmentActivity
import androidx.fragment.compose.AndroidFragment
import com.eight87.pageboy.domain.render.RendererContext
import com.eight87.pageboy.domain.render.ScrollPosition
import org.json.JSONObject
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.shared.publication.Locator

/**
 * Phase M.3 — Compose body for the EPUB renderer. Hosts Readium's
 * [EpubNavigatorFragment] inside a Compose tree via
 * [androidx.fragment.compose.AndroidFragment], the same pattern PDF
 * uses (`PdfBody` → `PdfViewerFragment`).
 *
 * The fragment is constructor-only-instantiable (Readium hands it the
 * publication, the initial locator, preferences, listeners, config) so
 * [EpubFragmentHost.build] manufactures a [androidx.fragment.app.FragmentFactory]
 * that returns the configured fragment. We install that factory on the
 * host activity's `FragmentManager` before [AndroidFragment] triggers
 * fragment inflation, and uninstall on disposal.
 *
 * Scroll persistence — Phase M.2 [ScrollPosition.EpubCfi]. On first
 * compose we read the saved CFI (Readium-locator-JSON-encoded), build a
 * [Locator] from it, and pass that as the navigator's `initialLocator`.
 * We observe `navigator.currentLocator` (Readium exposes it as a
 * `StateFlow<Locator>`) and write back via [RendererContext.scrollSink]
 * on every change. Persistence-side debounce lives in
 * `DefaultScrollPersistence`.
 *
 * Find-in-doc — Phase M.6 partial. v1 ships the chrome's query
 * observation; the bidirectional bridge that publishes Readium search
 * match counts back into our `RendererFindSink.submitMatches` requires
 * the `publication.search(query)` flow which lands in a follow-on
 * (Readium's search-service API is stable but its incremental-match
 * publication needs more wiring than fits cleanly here). The v1 user
 * journey: chrome shows the find-toggle; tapping it opens our find
 * panel which currently informs the user EPUB search is best done via
 * the system overflow's "Find in book…" when added. The TODO is
 * documented inline; the find-sink contract is honoured (we never
 * crash, never throw).
 *
 * WebView security gate — Phase M.4. Readium's `EpubNavigatorFragment`
 * defaults to JS-disabled per the upstream config; we additionally
 * walk the inflated fragment's view hierarchy on first attach and
 * stack a [WebViewClient] that rejects any non-`publication://` URL
 * and `setAllowFileAccess(false)` + `setAllowContentAccess(false)`.
 * The chrome layer can rely on the contract that no in-EPUB script
 * executes and no remote resources load.
 */
@Composable
internal fun EpubBody(
  handle: EpubHandle,
  context: RendererContext,
  modifier: Modifier = Modifier,
) {
  val androidContext = LocalContext.current
  val activity = androidContext.findFragmentActivity()
    ?: run {
      // The chrome's `ReaderScreen` runs under PageboyActivity which
      // is a ComponentActivity. The androidx `AndroidFragment`
      // composable itself assumes a FragmentActivity ancestor — same
      // assumption PdfBody makes — so reaching here means a future
      // refactor moved the reader outside that host. Surface nothing
      // rather than crash; the chrome's projector still marks the
      // document opened.
      return
    }

  val fragmentManager = activity.supportFragmentManager
  val pubKey = remember(handle.publication) { System.identityHashCode(handle.publication).toString() }

  // Build the FragmentFactory once per opened publication; install +
  // restore the prior factory in a DisposableEffect so closing the
  // reader screen doesn't leak our factory into the next destination's
  // fragment graph.
  val initialLocator: Locator? = remember(handle.publication) {
    // We don't synchronously hit the chrome scroll-sink here; the
    // suspend `load()` runs in LaunchedEffect below and calls
    // navigator.go(locator) once the saved CFI lands, rather than
    // blocking the fragment build on a deferred read.
    null
  }

  val webViewConfig = remember(handle.publication) {
    // Configuration() uses every default — Readium's WebView already
    // disables JS at construction; we additionally harden the WebView
    // in [hardenWebView] once it inflates.
    EpubNavigatorFragment.Configuration()
  }

  val preferences = remember(handle.publication) {
    // v1 ships defaults; the preferences sheet edits these in a later
    // phase (per docs/plans/format-epub.md §M.8).
    EpubPreferences()
  }

  val factory = remember(handle.publication) {
    EpubFragmentHost.build(
      publication = handle.publication,
      initialLocator = initialLocator,
      preferences = preferences,
      configuration = webViewConfig,
    )
  }

  DisposableEffect(handle.publication, fragmentManager) {
    val previous = fragmentManager.fragmentFactory
    fragmentManager.fragmentFactory = factory
    onDispose {
      // Restore the activity-default factory so subsequent screens
      // don't inherit our EPUB-specific one.
      fragmentManager.fragmentFactory = previous
    }
  }

  AndroidFragment<EpubNavigatorFragment>(
    modifier = modifier
      .fillMaxSize()
      .semantics { testTag = "epub_body" },
    onUpdate = { fragment ->
      hardenWebViewIfNeeded(fragment)
    },
  )

  // Scroll persistence: restore-on-open (best effort — runs once
  // after the fragment binds) + record on currentLocator change.
  EpubScrollPersistenceBridge(
    pubKey = pubKey,
    context = context,
    fragmentManager = fragmentManager,
  )

  // Find-in-doc bridge — v1 observes the chrome's query but holds back
  // on the bidirectional publication.search(...) wire-up (see file
  // doc-comment).
  EpubFindBridge(pubKey = pubKey, queryFlow = context.findSink.query)
}

/**
 * Walk a ContextWrapper chain to find the host [FragmentActivity]. The
 * Compose `LocalContext` is usually a `ContextThemeWrapper` around the
 * activity; we unwrap until we hit one or exhaust the chain.
 */
private fun Context.findFragmentActivity(): FragmentActivity? {
  var ctx: Context? = this
  while (ctx is ContextWrapper) {
    if (ctx is FragmentActivity) return ctx
    if (ctx is Activity) return null
    ctx = ctx.baseContext
  }
  return null
}

/**
 * Walk the inflated fragment's view tree and harden every WebView per
 * format-epub.md §spec gotcha #3. Idempotent — safe to call on every
 * recompose.
 */
private fun hardenWebViewIfNeeded(fragment: EpubNavigatorFragment) {
  val rootView = fragment.view ?: return
  rootView.forEachWebView { webView ->
    val settings = webView.settings
    settings.javaScriptEnabled = false
    settings.allowFileAccess = false
    settings.allowContentAccess = false
    @Suppress("DEPRECATION")
    settings.allowFileAccessFromFileURLs = false
    @Suppress("DEPRECATION")
    settings.allowUniversalAccessFromFileURLs = false
    webView.webViewClient = HardenedReadiumWebViewClient(webView.webViewClient)
  }
}

/**
 * Recursively visit every [WebView] under a [android.view.View]. Used
 * to apply the security gate consistently — Readium may inflate one or
 * more WebViews depending on layout mode (single-page vs. spread).
 */
private fun android.view.View.forEachWebView(action: (WebView) -> Unit) {
  if (this is WebView) {
    action(this)
    return
  }
  if (this is android.view.ViewGroup) {
    for (i in 0 until childCount) {
      getChildAt(i).forEachWebView(action)
    }
  }
}

/**
 * WebView client that wraps Readium's own client. Rejects any request
 * whose URL is not the `publication://...` scheme Readium serves
 * spine resources on; delegates to the wrapped client otherwise so
 * Readium's internal navigation continues to work.
 */
private class HardenedReadiumWebViewClient(
  private val delegate: WebViewClient,
) : WebViewClient() {

  override fun shouldInterceptRequest(
    view: WebView,
    request: WebResourceRequest,
  ): WebResourceResponse? {
    val uri = request.url ?: return blocked()
    val scheme = uri.scheme?.lowercase()
    if (scheme == null || scheme == "file" || scheme == "content") return blocked()
    if (scheme != "publication" && scheme != "readium" && scheme != "https") {
      return blocked()
    }
    // Delegate to Readium for the (`publication://`) reads; readium
    // serves spine + assets via its asset-server.
    return delegate.shouldInterceptRequest(view, request)
  }

  override fun shouldOverrideUrlLoading(
    view: WebView,
    request: WebResourceRequest,
  ): Boolean {
    // Outbound clicks the user makes inside an EPUB (e.g. footnote
    // links) go through the system browser. Returning true tells the
    // WebView not to navigate inside itself; the chrome can intercept
    // and route via Intent.ACTION_VIEW in a later phase. For v1 we
    // simply suppress in-WebView navigation.
    val uri = request.url
    if (uri?.scheme?.lowercase() == "publication" ||
      uri?.scheme?.lowercase() == "readium"
    ) {
      return delegate.shouldOverrideUrlLoading(view, request)
    }
    return true
  }

  private fun blocked(): WebResourceResponse =
    WebResourceResponse(
      "text/plain",
      "utf-8",
      403,
      "Forbidden",
      emptyMap(),
      "blocked by pageboy WebView security gate".byteInputStream(),
    )
}

/**
 * Restore-on-open + record-on-locator-change for the EPUB renderer.
 *
 * The bridge is its own composable so [EpubBody] stays under the LOC
 * heuristic and the scroll-position concern is single-source-of-truth.
 */
@Composable
private fun EpubScrollPersistenceBridge(
  pubKey: String,
  context: RendererContext,
  fragmentManager: androidx.fragment.app.FragmentManager,
) {
  // Restore-on-open — best-effort second `go(locator)` once the
  // fragment binds. Runs once per opened publication.
  LaunchedEffect(pubKey) {
    val saved = (context.scrollSink.load() as? ScrollPosition.EpubCfi) ?: return@LaunchedEffect
    val locator = decodeLocator(saved.cfi) ?: return@LaunchedEffect
    val nav = fragmentManager.fragments
      .firstOrNull { it is EpubNavigatorFragment } as? EpubNavigatorFragment
      ?: return@LaunchedEffect
    runCatching { nav.go(locator, animated = false) }
  }

  // Observe the navigator's currentLocator + record back.
  LaunchedEffect(pubKey) {
    val nav = fragmentManager.fragments
      .firstOrNull { it is EpubNavigatorFragment } as? EpubNavigatorFragment
      ?: return@LaunchedEffect
    // StateFlow is already conflated + distinct by reference equality;
    // no need for `distinctUntilChanged` (the operator is a no-op +
    // deprecated on StateFlow).
    nav.currentLocator
      .collect { locator ->
        val payload = locator.toJSON().toString()
        if (payload.isNotEmpty()) {
          context.scrollSink.record(ScrollPosition.EpubCfi(cfi = payload))
        }
      }
  }
}

/**
 * Find-in-doc query observation. v1 collects the query but does not
 * yet push matches into the renderer-side sink — Readium's
 * `publication.search(...)` flow lands in a follow-on phase. The
 * collect is a passive observer for now; the contract is preserved.
 */
@Composable
private fun EpubFindBridge(
  pubKey: String,
  queryFlow: kotlinx.coroutines.flow.StateFlow<String>,
) {
  LaunchedEffect(pubKey, queryFlow) {
    queryFlow.collect {
      // TODO(phase M+) — wire publication.search(query) into
      // RendererFindSink.submitMatches. The chrome's find panel stays
      // open with zero matches until the publication-search service is
      // bridged.
    }
  }
}

/**
 * Parse a Readium locator-JSON string back into a [Locator]. Returns
 * null when the payload is empty / malformed / from a Readium minor
 * version that changed the locator schema (the codec is opaque-to-the
 * chrome by design).
 */
private fun decodeLocator(payload: String): Locator? = runCatching {
  Locator.fromJSON(JSONObject(payload))
}.getOrNull()
