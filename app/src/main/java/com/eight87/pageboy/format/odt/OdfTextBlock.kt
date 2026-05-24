package com.eight87.pageboy.format.odt

/**
 * Phase K — internal block model for the ODT renderer. Each variant
 * carries the parsed-once shape `OdtBody` walks; we never expose this
 * outside the `format/odt/` package (per the agent prompt's "no shared
 * RichTextDocument cross-package" rule — ODT owns its own model; the
 * OOXML pair owns its own; cross-format unification is a 1.x concern).
 *
 * R.X.2 — sealed dispatch. Adding a block kind = one new variant + one
 * arm in [com.eight87.pageboy.format.odt.RenderOdtBlock]; no scattered
 * `when (node.tag)` chains across the package.
 *
 * Frames / OLE / charts / formulas land as [EmbeddedPlaceholder] with a
 * `kind` label the body renders inside a card (the spec gotcha #2 rule
 * — never crash on unknown `draw:*` children; emit a placeholder so the
 * user sees the structure was there but isn't rendered).
 */
sealed interface OdfTextBlock {

  /** Paragraph of styled inline runs. */
  data class Paragraph(
    val runs: List<OdfRun>,
    val style: OdfParagraphStyle,
  ) : OdfTextBlock

  /** Heading H1–H6 — `text:h` with `text:outline-level`. */
  data class Heading(
    val level: Int,
    val runs: List<OdfRun>,
  ) : OdfTextBlock

  /** Bulleted or numbered list. Items are flattened paragraphs per item. */
  data class ListBlock(
    val ordered: Boolean,
    val items: List<List<OdfTextBlock>>,
  ) : OdfTextBlock

  /** ODT table — list of rows; each row is a list of cells; each cell carries its own blocks. */
  data class Table(
    val rows: List<List<List<OdfTextBlock>>>,
  ) : OdfTextBlock

  /**
   * `<draw:object>` / `<draw:object-ole>` / `<draw:frame>` / `<chart:chart>` /
   * `<text:tracked-changes>` (skipped subtree). The body renders a labelled
   * card so the user understands an embedded thing was present.
   */
  data class EmbeddedPlaceholder(
    val kind: String,
  ) : OdfTextBlock
}

/**
 * Inline text run. `text:span` instances flatten into one per run; bare
 * `<text:p>` text between spans becomes runs with [OdfCharStyle.Default].
 * Hyperlinks (`<text:a xlink:href="…">`) carry [href].
 */
data class OdfRun(
  val text: String,
  val style: OdfCharStyle = OdfCharStyle.Default,
  val href: String? = null,
)

/**
 * Character-level style flags resolved against `styles.xml` per
 * [OdfStyleResolver]. Booleans are the read-only subset the v1 viewer
 * surfaces — colour / font-size are intentionally omitted in v1 to keep
 * the resolver small and the renderer fast; the format-odt.md plan
 * names them as v1.1 polish.
 */
data class OdfCharStyle(
  val bold: Boolean = false,
  val italic: Boolean = false,
  val underline: Boolean = false,
  val strike: Boolean = false,
) {
  companion object {
    val Default = OdfCharStyle()
  }
}

/**
 * Paragraph-level style — alignment + heading-level treated separately
 * via [OdfTextBlock.Heading]. Indent / spacing / line-height are v1.1.
 */
data class OdfParagraphStyle(
  val align: OdfAlign = OdfAlign.Start,
) {
  companion object {
    val Default = OdfParagraphStyle()
  }
}

enum class OdfAlign { Start, Center, End, Justify }
