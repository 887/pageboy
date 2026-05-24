package com.eight87.pageboy.domain.render

/**
 * Phase E.1 — persisted scroll position for an opened document.
 *
 * Lives in `domain/render/` (not `ui/reader/control/`) so both the chrome
 * (`ui/reader/`) and the per-format renderers (`format/...`) can import
 * it without crossing the `format/` → `ui/` import barrier R.X.6
 * forbids. Closes O.D.3 from the Phase D audit: previously the renderer
 * could not consume `ScrollPersistence` because doing so required
 * `format/` to depend on `ui/`.
 *
 * Renderer-specific semantics — PDF reads `pageIndex` + within-page
 * `offsetFraction`; reflowable formats (Markdown / Txt / EPUB) ignore
 * `pageIndex` and read only `offsetFraction`. The reader chrome doesn't
 * interpret the value; it's passed through to the renderer's `Body()`
 * via [RendererContext] to apply on first compose.
 */
data class ScrollPosition(
  val pageIndex: Int = 0,
  val offsetFraction: Float = 0f,
)
