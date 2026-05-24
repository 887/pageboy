package com.eight87.pageboy.domain.render

/**
 * Phase E.1 — a single find-in-document match the renderer surfaced.
 *
 * Lives in `domain/render/` (not `ui/reader/control/`) so per-format
 * renderers can emit matches without crossing the `format/` → `ui/`
 * import barrier R.X.6 forbids. Closes O.D.1 from the Phase D audit:
 * previously `format/markdown/MarkdownFind` had to ship its own local
 * `MarkdownMatch` type because reusing the chrome's [FindMatch] would
 * have required `format/markdown/` to import `ui/reader/control/`.
 *
 * Generic across formats:
 *  - [pageIndex] is `null` for reflowable formats (Markdown / Txt /
 *    EPUB); paginated renderers (PDF, future paged DOCX) set it.
 *  - [contextSnippet] is the short surrounding-text hint a results-list
 *    UI can render; the Phase D-shipped find panel doesn't surface a
 *    list, but the contract leaves room for one.
 *  - [rangeStart] / [rangeEnd] are character offsets into the
 *    renderer-defined text space (raw markdown text for Markdown; line
 *    + within-line offset for the Phase E TXT renderer; PDF char
 *    coordinates for PDF when Phase F lands).
 */
data class FindMatch(
  val rangeStart: Int,
  val rangeEnd: Int,
  val pageIndex: Int? = null,
  val contextSnippet: String? = null,
)
