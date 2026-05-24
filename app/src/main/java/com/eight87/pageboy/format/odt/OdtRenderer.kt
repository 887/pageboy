package com.eight87.pageboy.format.odt

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
 * Phase K — ODT [DocumentRenderer] impl.
 *
 * Thin façade: [open] inflates the ZIP, parses `content.xml` +
 * `styles.xml` + `meta.xml` via [OdtParser] → in-memory [OdtHandle];
 * [Body] delegates to [OdtBody]; [extractTitle] reads only `meta.xml`
 * for the scanner's cheap title probe.
 *
 * SOLID notes:
 *  - **R.X.1** narrow — takes a [DocumentBytesSource], emits a
 *    [DocumentHandle]; no `Context`, no `LibraryRepository`.
 *  - **R.X.5** no `NotImplementedError`. Embedded objects / charts /
 *    OLE / frames render as labelled placeholder cards rather than
 *    throwing (format-odt.md spec gotcha #2). Tracked changes are
 *    rendered final-without-markup (the subtree is skipped).
 *  - **R.X.6** does not import `data/library/` (except the closed-enum
 *    `DocumentFormat`, the documented narrow exception O.C.1).
 *  - **R.X.9** registry-dispatched — adding ODT is one line in
 *    [com.eight87.pageboy.AppGraph.formatRegistry], no `when (format)`
 *    in the reader.
 */
class OdtRenderer internal constructor(
  private val parser: OdtParser,
) : DocumentRenderer {

  constructor() : this(OdtParser())

  override val format: DocumentFormat = DocumentFormat.Odt

  override suspend fun open(source: DocumentBytesSource): DocumentHandle = withContext(Dispatchers.IO) {
    val parsed = parser.parse(source)
    val title = parsed.title?.takeIf { it.isNotBlank() }
      ?: source.displayName()
      ?: DEFAULT_TITLE
    OdtHandle(blocks = parsed.blocks, title = title)
  }

  @Composable
  override fun Body(handle: DocumentHandle, context: RendererContext, modifier: Modifier) {
    val odt = handle as? OdtHandle ?: return
    OdtBody(handle = odt, context = context, modifier = modifier)
  }

  override suspend fun extractTitle(source: DocumentBytesSource): String? = withContext(Dispatchers.IO) {
    runCatching { parser.extractTitle(source)?.takeIf { it.isNotBlank() } }.getOrNull()
  }

  private companion object {
    const val DEFAULT_TITLE = "OpenDocument text"
  }
}
