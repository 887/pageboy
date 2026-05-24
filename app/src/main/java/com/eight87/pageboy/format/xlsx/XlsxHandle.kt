package com.eight87.pageboy.format.xlsx

import com.eight87.pageboy.data.library.DocumentFormat
import com.eight87.pageboy.format.api.DocumentHandle
import com.eight87.pageboy.format.xlsx.internal.SpreadsheetSheet
import org.apache.poi.xssf.usermodel.XSSFWorkbook

/**
 * Phase J — opened-document state for XLSX.
 *
 * Carries the parsed [SpreadsheetSheet] list + the still-open POI
 * workbook handle (closed via [close] when the reader leaves the
 * screen).
 *
 * `pageCount` is null — workbooks paginate per-sheet differently from
 * documents; the chrome surfaces sheet tabs separately.
 */
internal data class XlsxHandle(
  val workbook: XSSFWorkbook,
  val sheets: List<SpreadsheetSheet>,
  override val title: String,
) : DocumentHandle {

  override val format: DocumentFormat = DocumentFormat.Xlsx
  override val pageCount: Int? = null

  override fun close() {
    runCatching { workbook.close() }
  }
}
