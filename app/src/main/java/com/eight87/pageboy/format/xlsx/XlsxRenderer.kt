package com.eight87.pageboy.format.xlsx

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.eight87.pageboy.data.library.DocumentFormat
import com.eight87.pageboy.domain.render.RendererContext
import com.eight87.pageboy.format.api.DocumentBytesSource
import com.eight87.pageboy.format.api.DocumentHandle
import com.eight87.pageboy.format.api.DocumentRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.poi.xssf.usermodel.XSSFWorkbook

/**
 * Phase J — XLSX [DocumentRenderer] impl.
 *
 * Thin façade: [open] hands the SAF byte stream to [XlsxParser],
 * gathers the result into an [XlsxHandle]; [Body] dispatches into
 * `XlsxBody.kt` (the Compose Composable lives there, in its own file
 * so this renderer stays under R.X.4).
 *
 * Liskov: every parse failure surfaces as an exception on [open]
 * (caught by `DefaultReaderStateProjector` and projected to
 * `ReaderState.Failed`), never a `NotImplementedError`. Encrypted
 * XLSX raises POI's `EncryptedDocumentException`; same propagation
 * path.
 *
 * SOLID notes:
 *  - **R.X.1** narrow — takes a [DocumentBytesSource] only.
 *  - **R.X.6** does not import `ui/` or `data/library/` (except the
 *    closed-enum `DocumentFormat`, per Phase C audit observation O.C.1).
 *  - **R.X.9** open/closed dispatch — one new line in
 *    [com.eight87.pageboy.AppGraph.formatRegistry] registers it.
 */
internal class XlsxRenderer(
  private val parser: XlsxParser = XlsxParser(),
) : DocumentRenderer {

  override val format: DocumentFormat = DocumentFormat.Xlsx

  override suspend fun open(source: DocumentBytesSource): DocumentHandle = withContext(Dispatchers.IO) {
    val result = source.openStream().use { stream -> parser.parse(stream) }
    val title = result.title
      ?: source.displayName()
      ?: DEFAULT_TITLE
    XlsxHandle(
      workbook = result.workbook,
      sheets = result.sheets,
      title = title,
    )
  }

  @Composable
  override fun Body(handle: DocumentHandle, context: RendererContext, modifier: Modifier) {
    val xlsx = handle as? XlsxHandle ?: return
    XlsxBody(handle = xlsx, context = context, modifier = modifier)
  }

  /**
   * Cheap title probe. Opens just long enough to read the core
   * properties and closes immediately.
   */
  override suspend fun extractTitle(source: DocumentBytesSource): String? = withContext(Dispatchers.IO) {
    runCatching {
      val title: String? = source.openStream().use { stream ->
        val wb = XSSFWorkbook(stream)
        try {
          wb.properties?.coreProperties?.title?.takeIf { it.isNotBlank() }
        } finally {
          runCatching { wb.close() }
        }
      }
      title
    }.getOrNull()
  }

  private companion object {
    const val DEFAULT_TITLE = "Spreadsheet"
  }
}
