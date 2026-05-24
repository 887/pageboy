package com.eight87.pageboy.format.xlsx

import com.eight87.pageboy.format.xlsx.internal.MergedRegion
import com.eight87.pageboy.format.xlsx.internal.SpreadsheetCell
import com.eight87.pageboy.format.xlsx.internal.SpreadsheetRow
import com.eight87.pageboy.format.xlsx.internal.SpreadsheetSheet
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.DateUtil
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.xssf.usermodel.XSSFCell
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.InputStream
import java.util.TimeZone

/**
 * Phase J.3 — XLSX parser. Wraps POI's `XSSFWorkbook` and walks every
 * sheet → rows → cells, emitting an internal [SpreadsheetSheet] list.
 *
 * The plan's "switch to streaming reader on sheets > 5 MB" optimisation
 * is deferred to a follow-up — the `excel-streaming-reader` dep is on
 * the classpath ready for it, but v1 ships the plain-XSSF path which
 * handles the vast majority of personal-use spreadsheets without
 * memory pressure. Adding the streamed path is a parser-internal
 * branch; the `SpreadsheetSheet` shape stays unchanged. See
 * "Anything deferred" in the phase report.
 *
 * Single responsibility (R.X.1): byte stream in, internal model out.
 * Compose is somebody else's problem (`XlsxBody.kt`).
 */
internal class XlsxParser {

  private val formatter = DataFormatter()

  /**
   * Parse the XLSX bytes into [XlsxParseResult]. Caller closes the
   * returned `XSSFWorkbook` via [XlsxHandle.close].
   */
  fun parse(input: InputStream): XlsxParseResult {
    val workbook = XSSFWorkbook(input)
    val sheets = ArrayList<SpreadsheetSheet>(workbook.numberOfSheets)
    for (i in 0 until workbook.numberOfSheets) {
      sheets += sheetToSheet(workbook.getSheetAt(i))
    }
    val title = extractCoreTitle(workbook)
    return XlsxParseResult(workbook = workbook, sheets = sheets, title = title)
  }

  /**
   * Convert one POI sheet into our flat [SpreadsheetSheet] model.
   * Handles frozen panes via the `paneInformation` API, merged regions
   * via `getMergedRegion`, and per-cell type dispatch.
   */
  internal fun sheetToSheet(sheet: Sheet): SpreadsheetSheet {
    val rowsOut = ArrayList<SpreadsheetRow>()
    val lastRowNum = sheet.lastRowNum
    var maxColumns = 0
    for (rowIndex in 0..lastRowNum) {
      val poiRow = sheet.getRow(rowIndex)
      if (poiRow == null) {
        rowsOut += SpreadsheetRow(cells = emptyList())
        continue
      }
      val cellsOut = ArrayList<SpreadsheetCell>(poiRow.lastCellNum.coerceAtLeast(0).toInt())
      // POI's row.lastCellNum is the (0-based) last index + 1.
      val last = poiRow.lastCellNum.toInt().coerceAtLeast(0)
      for (col in 0 until last) {
        val cell = poiRow.getCell(col)
        cellsOut += if (cell == null) SpreadsheetCell.Empty else cellToCell(cell as XSSFCell)
      }
      if (cellsOut.size > maxColumns) maxColumns = cellsOut.size
      rowsOut += SpreadsheetRow(cells = cellsOut)
    }

    val merged = ArrayList<MergedRegion>()
    for (i in 0 until sheet.numMergedRegions) {
      val cra = sheet.getMergedRegion(i)
      merged += MergedRegion(
        firstRow = cra.firstRow,
        lastRow = cra.lastRow,
        firstCol = cra.firstColumn,
        lastCol = cra.lastColumn,
      )
    }

    val pane = runCatching { sheet.paneInformation }.getOrNull()
    val frozenRows = if (pane?.isFreezePane == true) pane.horizontalSplitTopRow.toInt() else 0
    val frozenCols = if (pane?.isFreezePane == true) pane.verticalSplitLeftColumn.toInt() else 0

    return SpreadsheetSheet(
      name = sheet.sheetName,
      rows = rowsOut,
      columnCount = maxColumns,
      frozenRows = frozenRows,
      frozenColumns = frozenCols,
      mergedRegions = merged,
    )
  }

  /**
   * Convert one POI cell into our sealed [SpreadsheetCell]. Sniffs:
   *  - Numeric cell with date format → [SpreadsheetCell.DateValue]
   *  - Numeric cell otherwise → [SpreadsheetCell.Number]
   *  - Formula cells trust the cached value (no recompute) per
   *    `format-xlsx.md` gotcha #2.
   *  - Cells we can't recognise become [SpreadsheetCell.Empty] (never
   *    crash).
   */
  internal fun cellToCell(cell: XSSFCell): SpreadsheetCell {
    return try {
      when (cell.cellType) {
        CellType.STRING -> SpreadsheetCell.Text(cell.stringCellValue.orEmpty())
        CellType.BOOLEAN -> SpreadsheetCell.Bool(cell.booleanCellValue)
        CellType.NUMERIC -> numericCell(cell)
        CellType.FORMULA -> formulaCell(cell)
        CellType.BLANK -> SpreadsheetCell.Empty
        CellType.ERROR -> SpreadsheetCell.Text("#${cell.errorCellString}")
        else -> SpreadsheetCell.Empty
      }
    } catch (e: Exception) {
      // Any per-cell parse problem (corrupted shared-string ref, etc.)
      // becomes an empty cell — never crash the whole sheet.
      SpreadsheetCell.Empty
    }
  }

  private fun numericCell(cell: XSSFCell): SpreadsheetCell {
    val raw = cell.numericCellValue
    return if (DateUtil.isCellDateFormatted(cell)) {
      val date = cell.dateCellValue ?: return SpreadsheetCell.Number(raw, raw.formatPlain())
      SpreadsheetCell.DateValue(
        epochMillis = date.time,
        display = formatter.formatCellValue(cell) ?: date.toString(),
      )
    } else {
      SpreadsheetCell.Number(raw = raw, display = formatter.formatCellValue(cell) ?: raw.formatPlain())
    }
  }

  /**
   * Formula cells — read the cached value (the `<v>` element Excel
   * writes alongside the `<f>` element). Never evaluate. Per the spec
   * gotcha: if `<v>` is missing the cell shows empty; we surface as
   * an empty cell rather than triggering POI's evaluator.
   */
  private fun formulaCell(cell: XSSFCell): SpreadsheetCell {
    val formula = runCatching { cell.cellFormula.orEmpty() }.getOrDefault("")
    val cachedType = runCatching { cell.cachedFormulaResultType }.getOrNull() ?: CellType.BLANK
    val display = when (cachedType) {
      CellType.NUMERIC -> {
        if (DateUtil.isCellDateFormatted(cell)) {
          formatter.formatCellValue(cell) ?: ""
        } else {
          val n = cell.numericCellValue
          formatter.formatCellValue(cell) ?: n.formatPlain()
        }
      }
      CellType.STRING -> cell.stringCellValue.orEmpty()
      CellType.BOOLEAN -> if (cell.booleanCellValue) "TRUE" else "FALSE"
      CellType.ERROR -> "#${cell.errorCellString}"
      else -> ""
    }
    return SpreadsheetCell.Formula(formula = formula, display = display)
  }

  private fun extractCoreTitle(workbook: XSSFWorkbook): String? = runCatching {
    workbook.properties?.coreProperties?.title?.takeIf { it.isNotBlank() }
  }.getOrNull()

  /**
   * Render a double without scientific notation when small, with a
   * trailing zero stripped for integer-valued doubles. Used when POI's
   * DataFormatter is unavailable.
   */
  private fun Double.formatPlain(): String {
    if (this == this.toLong().toDouble()) return this.toLong().toString()
    return this.toString()
  }
}

internal data class XlsxParseResult(
  val workbook: XSSFWorkbook,
  val sheets: List<SpreadsheetSheet>,
  val title: String?,
)
