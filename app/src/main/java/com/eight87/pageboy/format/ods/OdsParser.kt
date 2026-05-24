package com.eight87.pageboy.format.ods

import com.eight87.pageboy.format.api.DocumentBytesSource
import com.eight87.pageboy.format.odt.getAttributeValueLocal
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * Phase L — hand-rolled ODS parser over Android's bundled
 * [XmlPullParser] + the JDK's [ZipInputStream]. Zero third-party deps;
 * Apache ODF Toolkit rejected on APK budget (format-ods.md).
 *
 * Two-pass design per format-ods.md:
 *   1. **Fast pass (eager)** — inflate `content.xml` + `meta.xml`,
 *      walk `<office:spreadsheet>` to discover sheet bounds, merged
 *      regions, frozen panes, named ranges. Materialises a sparse
 *      [OdfSheet] per `<table:table>` element.
 *   2. **Lazy windowing** — the body's `LazyColumn × LazyRow` only
 *      composes visible cells, so the in-memory sparse model is the
 *      "windowing" surface. For 100k+ row sheets the empty-row repeat
 *      skip (gotcha #3) keeps the sparse map bounded by non-empty
 *      content.
 *
 * Spec gotchas handled:
 *   - `table:number-columns-repeated` — column index advances by the
 *     repeat count; for non-empty cells with repeat > 1 the value is
 *     duplicated across the run (real-world: header row "Q1 Q1 Q1
 *     Q1" compressed to one element with repeat=4).
 *   - `table:number-rows-repeated` — repeated empty rows are skipped
 *     entirely (no row objects materialised); repeated non-empty rows
 *     duplicate their cells across the row span.
 *   - `<table:covered-table-cell>` — merged-cell hidden anchors;
 *     emitted as empty cells beneath the visual overlay.
 *   - `office:value` precedence — trust cached values over formulas
 *     (cells with only a formula and no cached value emit
 *     [OdfCell.Formula] with `formatted = "#FORMULA"`).
 *   - 1M-row default empty sheets — the empty-row-skip rule keeps the
 *     parser's allocation bounded.
 */
internal class OdsParser {

  data class Parsed(
    val sheets: List<OdfSheet>,
    val namedRanges: List<NamedRange>,
    val title: String?,
  )

  suspend fun parse(source: DocumentBytesSource): Parsed {
    val entries = source.openStream().use { readZipEntries(it) }
    val content = entries[CONTENT_XML] ?: error("ODS missing content.xml")
    val meta = entries[META_XML]
    val (sheets, ranges) = parseSpreadsheet(ByteArrayInputStream(content))
    val title = meta?.let { extractMetaTitle(ByteArrayInputStream(it)) }
    return Parsed(sheets = sheets, namedRanges = ranges, title = title)
  }

  suspend fun extractTitle(source: DocumentBytesSource): String? {
    val entries = source.openStream().use { readZipEntries(it, onlyNames = setOf(META_XML)) }
    val meta = entries[META_XML] ?: return null
    return extractMetaTitle(ByteArrayInputStream(meta))
  }

  private fun readZipEntries(stream: InputStream, onlyNames: Set<String>? = null): Map<String, ByteArray> {
    val wants = onlyNames ?: WANTED_ENTRIES
    val out = HashMap<String, ByteArray>(wants.size)
    ZipInputStream(stream).use { zip ->
      var entry: ZipEntry? = zip.nextEntry
      while (entry != null) {
        if (entry.name in wants) {
          out[entry.name] = zip.readBytes()
          if (out.size == wants.size) return out
        }
        zip.closeEntry()
        entry = zip.nextEntry
      }
    }
    return out
  }

  private fun extractMetaTitle(stream: InputStream): String? {
    val parser = newPullParser()
    parser.setInput(stream, null)
    var event = parser.eventType
    while (event != XmlPullParser.END_DOCUMENT) {
      if (event == XmlPullParser.START_TAG && parser.name == "title") {
        val sb = StringBuilder()
        var depth = 1
        while (depth > 0) {
          when (parser.next()) {
            XmlPullParser.START_TAG -> depth++
            XmlPullParser.END_TAG -> depth--
            XmlPullParser.TEXT -> sb.append(parser.text)
          }
        }
        val trimmed = sb.toString().trim()
        return trimmed.ifEmpty { null }
      }
      event = parser.next()
    }
    return null
  }

  private fun parseSpreadsheet(stream: InputStream): Pair<List<OdfSheet>, List<NamedRange>> {
    val parser = newPullParser()
    parser.setInput(stream, null)
    val sheets = ArrayList<OdfSheet>()
    val ranges = ArrayList<NamedRange>()
    var event = parser.eventType
    while (event != XmlPullParser.END_DOCUMENT) {
      if (event == XmlPullParser.START_TAG) {
        when (parser.name) {
          "table" -> {
            val sheet = parseTable(parser)
            if (sheet != null) sheets.add(sheet)
          }
          "named-range" -> {
            val nr = parseNamedRange(parser)
            if (nr != null) ranges.add(nr)
          }
        }
      }
      event = parser.next()
    }
    return sheets to ranges
  }

  private fun parseNamedRange(parser: XmlPullParser): NamedRange? {
    val name = parser.getAttributeValueLocal("name") ?: return null
    val target = parser.getAttributeValueLocal("cell-range-address")
      ?: parser.getAttributeValueLocal("base-cell-address")
      ?: return null
    // Address looks like "$Sheet1.$A$1" or "Sheet1.A1:B2"; v1 splits at
    // the dot and parses the upper-left cell only.
    val dot = target.indexOf('.')
    if (dot < 0) return null
    val sheetPart = target.substring(0, dot).removePrefix("$").removeSurrounding("'", "'")
    val cellPart = target.substring(dot + 1).substringBefore(':').replace("$", "")
    val (row, col) = parseCellRef(cellPart) ?: return null
    return NamedRange(name = name, sheetName = sheetPart, row = row, col = col)
  }

  /** Parse "A1" / "BC42" → (row, col) zero-based. Returns null on malformed. */
  internal fun parseCellRef(ref: String): Pair<Int, Int>? {
    var col = 0
    var i = 0
    while (i < ref.length && ref[i].isLetter()) {
      val ch = ref[i].uppercaseChar()
      if (ch !in 'A'..'Z') return null
      col = col * 26 + (ch - 'A' + 1)
      i++
    }
    if (i == 0 || i == ref.length) return null
    val row = ref.substring(i).toIntOrNull() ?: return null
    if (row < 1 || col < 1) return null
    return (row - 1) to (col - 1)
  }

  private fun parseTable(parser: XmlPullParser): OdfSheet? {
    val sheetName = parser.getAttributeValueLocal("name") ?: "Sheet"
    val cells = HashMap<Long, OdfCell>()
    val merged = ArrayList<MergedRegion>()
    var maxRow = 0
    var maxCol = 0
    var rowIndex = 0
    var frozenRows = 0
    var frozenCols = 0
    var inHeaderRows = false
    var inHeaderCols = false
    var depth = 1
    val rowCap = OdfSheet.MAX_RENDER_ROWS
    val colCap = OdfSheet.MAX_RENDER_COLS

    while (depth > 0) {
      val event = parser.next()
      when (event) {
        XmlPullParser.START_TAG -> {
          when (parser.name) {
            "table-header-rows" -> {
              inHeaderRows = true
              depth++
            }
            "table-header-columns" -> {
              inHeaderCols = true
              depth++
            }
            "table-column" -> {
              val repeat = parser.getAttributeValueLocal("number-columns-repeated")?.toIntOrNull() ?: 1
              if (inHeaderCols) frozenCols += repeat
              skipSubtree(parser)
            }
            "table-row" -> {
              val rowsRepeated = parser.getAttributeValueLocal("number-rows-repeated")?.toIntOrNull() ?: 1
              // Pre-parse the row cells into a list keyed by column-index; if
              // every cell is empty AND no merge spans cross this row, we
              // skip materialising rows entirely (gotcha #3: 1M-empty-row
              // default sheets).
              val rowCells = parseRow(parser)
              if (rowCells.isEmpty()) {
                rowIndex += rowsRepeated
              } else {
                // Real content — materialise repeats, but bounded by rowCap.
                val effectiveRepeats = rowsRepeated.coerceAtMost((rowCap - rowIndex).coerceAtLeast(0))
                repeat(effectiveRepeats) {
                  if (rowIndex >= rowCap) return@repeat
                  rowCells.forEach { (colIdx, cell, span) ->
                    val cappedCol = colIdx.coerceAtMost(colCap - 1)
                    cells[OdfSheet.packKey(rowIndex, cappedCol)] = cell
                    if (cappedCol > maxCol) maxCol = cappedCol
                    if (span != null) {
                      merged += MergedRegion(
                        anchorRow = rowIndex,
                        anchorCol = cappedCol,
                        rowSpan = span.rowSpan,
                        colSpan = span.colSpan,
                      )
                    }
                  }
                  if (inHeaderRows) frozenRows += 1
                  if (rowIndex > maxRow) maxRow = rowIndex
                  rowIndex++
                }
              }
            }
            else -> skipSubtree(parser)
          }
        }
        XmlPullParser.END_TAG -> {
          depth--
          if (parser.name == "table-header-rows") inHeaderRows = false
          if (parser.name == "table-header-columns") inHeaderCols = false
        }
        XmlPullParser.END_DOCUMENT -> break
      }
    }

    val rowCount = (maxRow + 1).coerceAtLeast(1).coerceAtMost(rowCap)
    val colCount = (maxCol + 1).coerceAtLeast(1).coerceAtMost(colCap)
    val frozen = if (frozenRows > 0 || frozenCols > 0) FrozenPane(rows = frozenRows, cols = frozenCols) else null

    return OdfSheet(
      name = sheetName,
      rowCount = rowCount,
      colCount = colCount,
      cells = cells,
      mergedRegions = merged,
      frozenPane = frozen,
    )
  }

  /** Triple of column index, cell value, and optional merge span. */
  private data class RowCell(val col: Int, val cell: OdfCell, val span: Span?)
  private data class Span(val rowSpan: Int, val colSpan: Int)

  /**
   * Parse the children of a `<table:table-row>`. Returns the list of
   * non-empty cells with their column indices already adjusted for
   * column repetition. Empty cells are omitted.
   */
  private fun parseRow(parser: XmlPullParser): List<RowCell> {
    val out = ArrayList<RowCell>()
    var col = 0
    var depth = 1
    val colCap = OdfSheet.MAX_RENDER_COLS
    while (depth > 0) {
      val event = parser.next()
      when (event) {
        XmlPullParser.START_TAG -> {
          when (parser.name) {
            "table-cell" -> {
              val repeat = parser.getAttributeValueLocal("number-columns-repeated")?.toIntOrNull() ?: 1
              val rowSpan = parser.getAttributeValueLocal("number-rows-spanned")?.toIntOrNull() ?: 1
              val colSpan = parser.getAttributeValueLocal("number-columns-spanned")?.toIntOrNull() ?: 1
              val cell = parseCell(parser)
              if (cell !is OdfCell.Empty) {
                val effective = repeat.coerceAtMost((colCap - col).coerceAtLeast(0))
                val span = if (rowSpan > 1 || colSpan > 1) Span(rowSpan = rowSpan, colSpan = colSpan) else null
                repeat(effective) { i ->
                  out += RowCell(col = col + i, cell = cell, span = if (i == 0) span else null)
                }
              }
              col += repeat
            }
            "covered-table-cell" -> {
              val repeat = parser.getAttributeValueLocal("number-columns-repeated")?.toIntOrNull() ?: 1
              // Spec: covered cells are placeholders beneath a merge overlay.
              // We don't emit content for them; the merge overlay handles it.
              col += repeat
              skipSubtree(parser)
            }
            else -> skipSubtree(parser)
          }
        }
        XmlPullParser.END_TAG -> depth--
        XmlPullParser.END_DOCUMENT -> break
      }
    }
    return out
  }

  /**
   * Parse one cell. Reads value attributes and the inline `<text:p>`
   * children; resolves cached value precedence over formulas.
   */
  private fun parseCell(parser: XmlPullParser): OdfCell {
    val type = parser.getAttributeValueLocal("value-type")
    val numericValue = parser.getAttributeValueLocal("value")
    val stringValue = parser.getAttributeValueLocal("string-value")
    val booleanValue = parser.getAttributeValueLocal("boolean-value")
    val dateValue = parser.getAttributeValueLocal("date-value")
    val formula = parser.getAttributeValueLocal("formula")

    // Read inline text body (`<text:p>` children) into a single string.
    val displayText = readInlineText(parser)

    val cachedValue: OdfCell? = when (type) {
      "float", "percentage", "currency" -> numericValue?.toDoubleOrNull()?.let { v ->
        OdfCell.Number(value = v, formatted = displayText.ifBlank { formatNumber(v) })
      }
      "boolean" -> booleanValue?.let { OdfCell.Bool(value = it.equals("true", true), formatted = displayText.ifBlank { it }) }
      "date" -> dateValue?.let { OdfCell.Date(iso = it, formatted = displayText.ifBlank { it }) }
      "time" -> dateValue?.let { OdfCell.Date(iso = it, formatted = displayText.ifBlank { it }) }
      "string" -> {
        val text = stringValue ?: displayText
        if (text.isEmpty()) null else OdfCell.Text(formatted = text)
      }
      null -> if (displayText.isNotEmpty()) OdfCell.Text(formatted = displayText) else null
      else -> if (displayText.isNotEmpty()) OdfCell.Text(formatted = displayText) else null
    }

    if (formula != null) {
      val formattedFallback = cachedValue?.formatted ?: FORMULA_PLACEHOLDER
      return OdfCell.Formula(
        formula = formula,
        cachedValue = cachedValue,
        formatted = formattedFallback,
      )
    }
    return cachedValue ?: OdfCell.Empty
  }

  /**
   * Read the inline text content of the current `<table:table-cell>`,
   * concatenating `<text:p>` paragraphs with newlines. Stops at the
   * matching cell END_TAG. Consumes the END_TAG.
   */
  private fun readInlineText(parser: XmlPullParser): String {
    val sb = StringBuilder()
    var depth = 1
    var firstParagraph = true
    while (depth > 0) {
      val event = parser.next()
      when (event) {
        XmlPullParser.START_TAG -> {
          depth++
          if (parser.name == "p" && !firstParagraph) sb.append('\n')
          if (parser.name == "p") firstParagraph = false
        }
        XmlPullParser.TEXT -> sb.append(parser.text)
        XmlPullParser.END_TAG -> depth--
        XmlPullParser.END_DOCUMENT -> return sb.toString()
      }
    }
    return sb.toString()
  }

  private fun skipSubtree(parser: XmlPullParser) {
    var depth = 1
    while (depth > 0) {
      when (parser.next()) {
        XmlPullParser.START_TAG -> depth++
        XmlPullParser.END_TAG -> depth--
        XmlPullParser.END_DOCUMENT -> return
      }
    }
  }

  /** Reasonable number formatter for v1 — no locale-specific currency / percent yet. */
  private fun formatNumber(value: Double): String {
    if (value.isNaN() || value.isInfinite()) return value.toString()
    val rounded = value.toLong()
    return if (value == rounded.toDouble()) rounded.toString() else value.toString()
  }

  companion object {
    const val CONTENT_XML = "content.xml"
    const val META_XML = "meta.xml"
    const val FORMULA_PLACEHOLDER = "#FORMULA"
    private val WANTED_ENTRIES = setOf(CONTENT_XML, META_XML)

    private fun newPullParser(): XmlPullParser {
      val factory = XmlPullParserFactory.newInstance()
      factory.isNamespaceAware = true
      return factory.newPullParser()
    }
  }
}
