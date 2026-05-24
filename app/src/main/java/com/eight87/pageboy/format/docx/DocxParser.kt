package com.eight87.pageboy.format.docx

import com.eight87.pageboy.format.docx.internal.PlaceholderKind
import com.eight87.pageboy.format.docx.internal.RichTextBlock
import com.eight87.pageboy.format.docx.internal.RichTextRun
import com.eight87.pageboy.format.docx.internal.TableCell
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.xwpf.usermodel.XWPFHyperlinkRun
import org.apache.poi.xwpf.usermodel.XWPFParagraph
import org.apache.poi.xwpf.usermodel.XWPFRun
import org.apache.poi.xwpf.usermodel.XWPFTable
import org.apache.poi.xwpf.usermodel.XWPFTableCell
import java.io.InputStream

/**
 * Phase I.4 — DOCX parser. Wraps POI's `XWPFDocument` and walks the
 * body's paragraphs + tables + drawing children into a flat list of
 * [RichTextBlock] for the Compose layer.
 *
 * Single responsibility (R.X.1): byte stream in, internal block model
 * out. Compose is somebody else's problem (`DocxBody.kt`).
 *
 * POI handles several gotchas internally so the parser doesn't have to:
 *  - `mc:AlternateContent` MCE: POI's XWPF surface picks the `mc:Fallback`
 *    branch when accessed via the high-level API (paragraph runs etc.).
 *    We rely on that behaviour and verify with a fixture test.
 *  - Tracked changes: XWPF's run-level API returns the "final-without-
 *    markup" view by default (insertion runs are visible, deletion
 *    runs are skipped). Matches our v1 policy.
 *  - Comments: not exposed in the run-stream; the comment marker `<w:commentReference>`
 *    is invisible in v1 (the spec defers a sidebar UI to a later phase).
 */
internal class DocxParser {

  /**
   * Parse the DOCX bytes into [DocxParseResult]. Caller owns the
   * returned [XWPFDocument]'s lifecycle — close it via the handle.
   */
  fun parse(input: InputStream): DocxParseResult {
    val document = XWPFDocument(input)
    val blocks = ArrayList<RichTextBlock>()
    for (element in document.bodyElements) {
      // POI's `IBodyElement` is a sealed-ish heirarchy in practice
      // (paragraph / table / contentControl). We branch on the concrete
      // type and emit the appropriate block. Anything we don't
      // recognise becomes a single-arm `Placeholder` so the document
      // never silently drops content.
      when (element) {
        is XWPFParagraph -> blocks += paragraphToBlock(element, document)
        is XWPFTable -> blocks += tableToBlock(element, document)
        else -> blocks += RichTextBlock.Placeholder(
          kind = PlaceholderKind.Unknown,
          label = "[unknown body element: ${element.javaClass.simpleName}]",
        )
      }
    }
    val title = extractCoreTitle(document)
      ?: blocks.firstOrNull { it is RichTextBlock.Heading && (it as RichTextBlock.Heading).level == 1 }
        ?.let { (it as RichTextBlock.Heading).runs.joinToString("") { r -> if (r is RichTextRun.Text) r.value else "" } }
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
    return DocxParseResult(document = document, blocks = blocks, title = title)
  }

  /**
   * Convert one POI paragraph to its block form. Detects headings via
   * the style id (`Heading1`..`Heading9`); list items are picked off by
   * the paragraph's numbering reference. Otherwise it's a regular
   * paragraph carrying the run stream.
   */
  private fun paragraphToBlock(paragraph: XWPFParagraph, document: XWPFDocument): RichTextBlock {
    val runs = paragraphRuns(paragraph, document)

    // List detection: a paragraph with a numId is part of a list.
    // We emit each list paragraph as its own single-item list block —
    // a coalescing pass in Wave A could collapse consecutive items but
    // the per-block render is correct as-is (LazyColumn just renders
    // each item).
    val numId = runCatching { paragraph.numID }.getOrNull()
    if (numId != null) {
      // Distinguish ordered vs unordered: POI's numbering format
      // string starts with "decimal" / "lowerRoman" / etc. for ordered.
      // The numbering part may be null (saved-without-numbering edge
      // case); default to bullet in that case.
      val numbering = runCatching { paragraph.numFmt }.getOrNull()
      val ordered = numbering?.let {
        it.contains("decimal", ignoreCase = true) ||
          it.contains("roman", ignoreCase = true) ||
          it.contains("letter", ignoreCase = true) ||
          it.contains("ordinal", ignoreCase = true)
      } ?: false
      return if (ordered) {
        RichTextBlock.NumberedList(items = listOf(runs))
      } else {
        RichTextBlock.BulletList(items = listOf(runs))
      }
    }

    val style = runCatching { paragraph.style }.getOrNull()
    if (style != null) {
      val level = headingLevelForStyle(style)
      if (level != null) return RichTextBlock.Heading(level = level, runs = runs)
      if (style.equals("Quote", ignoreCase = true) ||
        style.equals("IntenseQuote", ignoreCase = true)
      ) {
        return RichTextBlock.BlockQuote(runs = runs)
      }
    }

    return RichTextBlock.Paragraph(runs = runs)
  }

  /**
   * Build the run list for a paragraph, with hyperlink + drawing
   * detection. POI's `XWPFParagraph.runs` returns a flat list with
   * `XWPFHyperlinkRun` and drawing-bearing runs already differentiated
   * as subtypes — we just dispatch by Kotlin type.
   */
  private fun paragraphRuns(paragraph: XWPFParagraph, document: XWPFDocument): List<RichTextRun> {
    val out = ArrayList<RichTextRun>()
    for (run in paragraph.runs) {
      when (run) {
        is XWPFHyperlinkRun -> {
          val url: String = runCatching { run.getHyperlink(document)?.url }.getOrNull().orEmpty()
          val text = run.text()?.takeIf { it.isNotEmpty() } ?: continue
          out += RichTextRun.Hyperlink(text = text, url = url)
        }
        else -> {
          out += plainRun(run)
        }
      }
    }
    return out
  }

  /**
   * Convert one POI run to one [RichTextRun.Text]. We collect all the
   * inline styling toggles in a single pass.
   */
  private fun plainRun(run: XWPFRun): RichTextRun {
    val text = run.text() ?: ""
    return RichTextRun.Text(
      value = text,
      bold = runCatching { run.isBold }.getOrDefault(false),
      italic = runCatching { run.isItalic }.getOrDefault(false),
      underline = runCatching { run.underline.name != "NONE" }.getOrDefault(false),
      strikethrough = runCatching { run.isStrike || run.isStrikeThrough }.getOrDefault(false),
      monospace = runCatching {
        val font = run.fontFamily.orEmpty().lowercase()
        font.contains("mono") || font.contains("courier") || font.contains("consolas")
      }.getOrDefault(false),
    )
  }

  /**
   * POI heading styles are typically named `Heading1`..`Heading9` or
   * the localised variant. We accept the most common forms; anything
   * we don't recognise falls back to "no heading".
   */
  private fun headingLevelForStyle(style: String): Int? {
    val match = Regex("(?i)heading\\s*(\\d+)").find(style) ?: return null
    val n = match.groupValues[1].toIntOrNull() ?: return null
    return n.coerceIn(1, 9)
  }

  /**
   * Walk one POI table into the [RichTextBlock.Table] form. Each cell
   * gathers its paragraphs' runs into one flat list (a cell is rendered
   * inline rather than as nested blocks in v1; nested tables collapse
   * to "[table]" placeholder text for simplicity).
   */
  private fun tableToBlock(table: XWPFTable, document: XWPFDocument): RichTextBlock.Table {
    val rows = ArrayList<List<TableCell>>(table.rows.size)
    for (poiRow in table.rows) {
      val cells = ArrayList<TableCell>(poiRow.tableCells.size)
      for (poiCell in poiRow.tableCells) {
        cells += cellToCell(poiCell, document)
      }
      rows += cells
    }
    return RichTextBlock.Table(rows = rows)
  }

  private fun cellToCell(poiCell: XWPFTableCell, document: XWPFDocument): TableCell {
    val runs = ArrayList<RichTextRun>()
    for ((i, p) in poiCell.paragraphs.withIndex()) {
      if (i > 0) runs += RichTextRun.SoftBreak
      runs += paragraphRuns(p, document)
    }
    // Nested tables inside cells collapse to a placeholder note so we
    // don't silently drop content but also don't recurse arbitrarily.
    if (poiCell.tables.isNotEmpty()) {
      runs += RichTextRun.Text(value = " [nested table]")
    }
    return TableCell(runs = runs)
  }

  private fun extractCoreTitle(document: XWPFDocument): String? = runCatching {
    document.properties?.coreProperties?.title?.takeIf { it.isNotBlank() }
  }.getOrNull()
}

/**
 * Output of [DocxParser.parse]. The [document] is the still-open POI
 * handle the caller must close (the [DocxHandle.close] funnel does
 * this for us in production); [blocks] is the flat list the Compose
 * layer renders.
 */
internal data class DocxParseResult(
  val document: XWPFDocument,
  val blocks: List<RichTextBlock>,
  val title: String?,
)
