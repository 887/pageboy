package com.eight87.pageboy.format.ods

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.eight87.pageboy.data.library.DocumentFormat
import com.eight87.pageboy.domain.render.RendererContext
import com.eight87.pageboy.format.api.DocumentBytesSource
import com.eight87.pageboy.format.api.DocumentHandle
import com.eight87.pageboy.format.api.DocumentRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Phase L — ODS [DocumentRenderer] impl.
 *
 * Thin façade: [open] inflates the ZIP, parses `content.xml` +
 * `meta.xml` via [OdsParser] → in-memory [OdsHandle]; [Body] delegates
 * to [OdsBody]; [extractTitle] reads only `meta.xml`.
 *
 * SOLID notes:
 *  - **R.X.1** narrow — takes a [DocumentBytesSource], emits a
 *    [DocumentHandle].
 *  - **R.X.5** no `NotImplementedError`. Formulas without cached values
 *    render `#FORMULA`; embedded charts skipped (would be a
 *    placeholder cell in a later phase); empty sheets render an empty
 *    grid; macros / data validation / conditional formatting are
 *    silently ignored per format-ods.md scope.
 *  - **R.X.6** does not import `data/library/` (except the closed-enum
 *    `DocumentFormat`).
 *  - **R.X.9** registry-dispatched.
 */
class OdsRenderer internal constructor(
  private val parser: OdsParser,
) : DocumentRenderer {

  constructor() : this(OdsParser())

  override val format: DocumentFormat = DocumentFormat.Ods

  override suspend fun open(source: DocumentBytesSource): DocumentHandle = withContext(Dispatchers.IO) {
    val parsed = parser.parse(source)
    val title = parsed.title?.takeIf { it.isNotBlank() }
      ?: source.displayName()
      ?: DEFAULT_TITLE
    OdsHandle(sheets = parsed.sheets, namedRanges = parsed.namedRanges, title = title)
  }

  @Composable
  override fun Body(handle: DocumentHandle, context: RendererContext, modifier: Modifier) {
    val ods = handle as? OdsHandle ?: return
    OdsBody(handle = ods, context = context, modifier = modifier)
  }

  override suspend fun extractTitle(source: DocumentBytesSource): String? = withContext(Dispatchers.IO) {
    runCatching { parser.extractTitle(source)?.takeIf { it.isNotBlank() } }.getOrNull()
  }

  private companion object {
    const val DEFAULT_TITLE = "OpenDocument spreadsheet"
  }
}
