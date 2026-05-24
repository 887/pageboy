package com.eight87.pageboy.format.docx.internal

/**
 * Phase I — inline run-level model for the DOCX renderer. Each
 * paragraph / heading / list-item flattens into a list of [RichTextRun]s
 * that the Compose layer folds into one `AnnotatedString`.
 *
 * Stays a `sealed interface` per R.X.2 — adding a run kind (e.g. an
 * inline image, an emoji glyph) is a new variant + one arm in the
 * Compose folder, not a hunt-and-modify across multiple sites.
 *
 * Lives in `format/docx/internal/` deliberately — the DOCX-side parsed
 * shape is NOT promoted to a shared `format/text/` package in Wave A.
 * The ODT renderer (running in parallel) owns its own internal model;
 * the cross-format unification is a Phase 1.x refactor, not Wave A
 * concern. Keeping each renderer's model private avoids accidental API
 * coupling between two unrelated parsers.
 */
internal sealed interface RichTextRun {

  /** Plain text with optional inline styling. */
  data class Text(
    val value: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val strikethrough: Boolean = false,
    val monospace: Boolean = false,
  ) : RichTextRun

  /** Tappable hyperlink. POI exposes both text + target separately. */
  data class Hyperlink(
    val text: String,
    val url: String,
  ) : RichTextRun

  /**
   * A field code's cached rendered text — `{ DATE }`, `{ PAGE }`,
   * `{ TOC }`, etc. POI gives us the cached result via `XWPFRun.text`
   * inside the `w:fldSimple`/`w:fldChar` boundaries; we render that
   * literally and do NOT re-evaluate the field. Carries the placeholder
   * label for diagnostics; the rendered text is what shows.
   */
  data class FieldCode(
    val placeholder: String,
    val cachedText: String,
  ) : RichTextRun

  /**
   * A tab inside a run. Word uses `<w:tab/>` for both tab stops and
   * indentation. We render as four spaces; precise tab-stop layout is a
   * later phase if anyone ever asks.
   */
  data object Tab : RichTextRun

  /**
   * A soft (in-paragraph) break — Word's `<w:br/>` without a type
   * attribute. Renders as a newline inside the same paragraph block.
   */
  data object SoftBreak : RichTextRun
}
