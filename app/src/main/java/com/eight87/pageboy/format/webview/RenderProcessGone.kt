package com.eight87.pageboy.format.webview

import android.view.ViewGroup
import android.webkit.WebView

/**
 * Shared `WebViewClient.onRenderProcessGone` handling for the WebView-based
 * renderers (EPUB via Readium, MOBI). When the WebView renderer process
 * crashes or is killed to reclaim memory, the default (returning `false`)
 * kills the whole app. Instead, detach and destroy the dead WebView — it
 * can never be used again — so only the reader surface goes blank and the
 * user can back out and reopen the document.
 *
 * @return always `true`: the crash has been handled.
 */
internal fun discardDeadWebView(view: WebView): Boolean {
  (view.parent as? ViewGroup)?.removeView(view)
  view.destroy()
  return true
}
