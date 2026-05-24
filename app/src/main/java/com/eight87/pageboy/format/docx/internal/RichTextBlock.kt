package com.eight87.pageboy.format.docx.internal

/**
 * Phase I — block-level model for the DOCX renderer. The DOCX parser
 * walks POI's `XWPFDocument` body once and emits a flat `List<RichTextBlock>`;
 * the Compose layer renders that list inside a `LazyColumn`.
 *
 * One concern per variant (R.X.2 sealed-type dispatch). Adding a block
 * kind (e.g. a real image render in v1.1) is a new variant + one arm in
 * the `RichTextBlocks` dispatch, not a hunt-and-modify across the
 * package.
 *
 * Internal to `format/docx/` per the Wave A boundary: the ODT renderer
 * owns a sibling internal model. Cross-format model unification is
 * deferred to a later refactor.
 */
internal sealed interface RichTextBlock {

  /** Body paragraph — the default run-of-inlines block. */
  data class Paragraph(val runs: List<RichTextRun>) : RichTextBlock

  /**
   * Heading with a 1-6 level (matches Word's Heading1..Heading6 style
   * IDs). The Compose layer maps the level to typography tokens; level
   * 7+ falls back to level 6 since M3 doesn't ship distinct tokens
   * past `headlineSmall`.
   */
  data class Heading(val level: Int, val runs: List<RichTextRun>) : RichTextBlock

  /**
   * Block-quote (Word's `Quote` / `IntenseQuote` paragraph styles).
   * Holds runs at the leaf — nested quotes flatten in v1.
   */
  data class BlockQuote(val runs: List<RichTextRun>) : RichTextBlock

  /** Unordered list — each item is its own run list. */
  data class BulletList(val items: List<List<RichTextRun>>) : RichTextBlock

  /** Ordered list — each item is its own run list. */
  data class NumberedList(val items: List<List<RichTextRun>>) : RichTextBlock

  /**
   * Table — `rows[r][c]` is a cell carrying its own runs. Merged cells
   * are flattened in v1: a vertically-merged cell shows its content in
   * the top-left corner and the merged-over cells render empty. This
   * matches what every other reader app does on DOCX in our research.
   */
  data class Table(val rows: List<List<TableCell>>) : RichTextBlock

  /**
   * Image placeholder — POI exposes embedded pictures via
   * `XWPFPicture`; we render them as a marker block carrying the URI
   * for a later Coil-backed lookup (Phase F when PDF brings Coil in).
   * In v1 the renderer just shows "[image]" + the alt text.
   */
  data class ImagePlaceholder(val altText: String, val uri: String?) : RichTextBlock

  /**
   * Text box content — Word's `<w:txbxContent>`. We render the inner
   * runs inline as a paragraph; the box border is dropped in v1.
   */
  data class TextBox(val runs: List<RichTextRun>) : RichTextBlock

  /**
   * Catch-all placeholder for content that we recognise but cannot
   * fully render — embedded OLE objects, drawing-XML shapes that
   * aren't pictures, charts. Carries a [kind] label the renderer
   * surfaces as a small badge.
   */
  data class Placeholder(val kind: PlaceholderKind, val label: String) : RichTextBlock

  /** Word document section break — renders as a thin divider. */
  data object SectionBreak : RichTextBlock
}

/**
 * Kinds of "we recognised but didn't render" content. Surfaced to the
 * user via a small badge so they know SOMETHING was there. Each variant
 * maps to a different "[shape] / [chart] / [embedded …]" label.
 */
internal enum class PlaceholderKind {
  Drawing,
  Chart,
  OleObject,
  SmartArt,
  Unknown,
}

/**
 * One cell in a DOCX table. Holds its own runs; merge info is captured
 * here for v1.1's proper-merge rendering.
 */
internal data class TableCell(
  val runs: List<RichTextRun>,
  val colSpan: Int = 1,
  val rowSpan: Int = 1,
)
