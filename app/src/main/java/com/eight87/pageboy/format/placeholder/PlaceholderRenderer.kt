package com.eight87.pageboy.format.placeholder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.eight87.pageboy.R
import com.eight87.pageboy.data.library.DocumentFormat
import com.eight87.pageboy.domain.render.RendererContext
import com.eight87.pageboy.format.api.DocumentBytesSource
import com.eight87.pageboy.format.api.DocumentHandle
import com.eight87.pageboy.format.api.DocumentRenderer

/**
 * Phase C.3 — backstop renderer for every format whose real impl hasn't
 * shipped yet. Lives in its own package so per-format renderers
 * (`format/markdown/`, `format/pdf/`, etc.) can shadow it by registering
 * themselves in the [com.eight87.pageboy.format.registry.FormatRegistry].
 *
 * Implements the [DocumentRenderer] contract totally — no
 * `NotImplementedError`. [open] returns a trivial [PlaceholderHandle]
 * carrying the document's display name; [Body] renders a polite
 * "not yet implemented" message + the format label so the user
 * understands what they tapped; [extractTitle] returns null (falling
 * back to the scanner's filename-derived title).
 *
 * The "view file info" / "back to library" affordances mentioned in the
 * Phase C plan are not surfaced inside the body itself — the reader
 * chrome's top bar already provides Back, and surfacing a third "back"
 * affordance inside the body would be redundant; file-info lands when
 * there's something interesting to show beyond the filename (Phase D+).
 */
class PlaceholderRenderer(
  override val format: DocumentFormat,
) : DocumentRenderer {

  override suspend fun open(source: DocumentBytesSource): DocumentHandle {
    val name = source.displayName() ?: DEFAULT_TITLE
    return PlaceholderHandle(format = format, title = name)
  }

  @Composable
  override fun Body(handle: DocumentHandle, context: RendererContext, modifier: Modifier) {
    // Placeholder does not scroll and does not search; the [context]
    // handles are accepted (R.X.9 contract) and intentionally unread.
    @Suppress("UNUSED_PARAMETER") val ctx = context
    val placeholder = handle as? PlaceholderHandle
    val formatLabel = stringResource(formatLabelRes(handle.format))
    Column(
      modifier = modifier
        .fillMaxSize()
        .padding(PaddingValues(horizontal = 24.dp, vertical = 32.dp))
        .semantics { testTag = "reader_placeholder_body" },
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
    ) {
      Icon(
        imageVector = Icons.AutoMirrored.Filled.InsertDriveFile,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.height(64.dp),
      )
      Spacer(Modifier.height(16.dp))
      Text(
        text = stringResource(R.string.reader_placeholder_headline, formatLabel),
        style = MaterialTheme.typography.titleMedium,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurface,
      )
      Spacer(Modifier.height(8.dp))
      Text(
        text = placeholder?.title ?: handle.title,
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Spacer(Modifier.height(16.dp))
      Text(
        text = stringResource(R.string.reader_placeholder_subtext),
        style = MaterialTheme.typography.bodySmall,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }

  override suspend fun extractTitle(source: DocumentBytesSource): String? = null

  private companion object {
    const val DEFAULT_TITLE = "Document"
  }
}

/**
 * Phase C.3 — trivial [DocumentHandle] for the placeholder path. No
 * native resources, no parsed AST. [close] is a no-op.
 */
data class PlaceholderHandle(
  override val format: DocumentFormat,
  override val title: String,
  override val pageCount: Int? = null,
) : DocumentHandle

/**
 * String-resource lookup for the format label shown inside the
 * placeholder body. Kept here next to the body so the lookup lives in
 * one file and the renderer file size stays controllable.
 */
private fun formatLabelRes(format: DocumentFormat): Int = when (format) {
  DocumentFormat.Markdown -> R.string.format_label_markdown
  DocumentFormat.Txt -> R.string.format_label_txt
  DocumentFormat.Pdf -> R.string.format_label_pdf
  DocumentFormat.Epub -> R.string.format_label_epub
  DocumentFormat.Docx -> R.string.format_label_docx
  DocumentFormat.Xlsx -> R.string.format_label_xlsx
  DocumentFormat.Odt -> R.string.format_label_odt
  DocumentFormat.Ods -> R.string.format_label_ods
  DocumentFormat.Unknown -> R.string.format_label_unknown
}
