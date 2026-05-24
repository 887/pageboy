package com.eight87.pageboy.ui.library

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.ui.graphics.vector.ImageVector
import com.eight87.pageboy.data.library.DocumentFormat

/**
 * Phase B (audit split) — per-format display affordances. Single source of
 * truth for the filter chip row, the document card icon, and any future
 * surface that needs to render a [DocumentFormat] visually.
 *
 * The `when (format)` switches live here, NOT in every consumer (R.X.2
 * spirit applied to an enum we keep flat for Room round-trip). Adding a
 * new format means a new enum case in [DocumentFormat] and two new
 * branches in this file; the compiler flags both.
 */

internal fun formatLabel(format: DocumentFormat): String = when (format) {
  DocumentFormat.Markdown -> "Markdown"
  DocumentFormat.Txt -> "Text"
  DocumentFormat.Pdf -> "PDF"
  DocumentFormat.Epub -> "EPUB"
  DocumentFormat.Mobi -> "MOBI"
  DocumentFormat.Docx -> "DOCX"
  DocumentFormat.Xlsx -> "XLSX"
  DocumentFormat.Odt -> "ODT"
  DocumentFormat.Ods -> "ODS"
  DocumentFormat.Unknown -> "Unknown"
}

internal fun formatIcon(format: DocumentFormat): ImageVector = when (format) {
  DocumentFormat.Pdf -> Icons.Filled.PictureAsPdf
  DocumentFormat.Epub, DocumentFormat.Mobi -> Icons.Filled.Book
  DocumentFormat.Markdown, DocumentFormat.Txt -> Icons.AutoMirrored.Filled.Article
  DocumentFormat.Docx, DocumentFormat.Odt -> Icons.Filled.Description
  DocumentFormat.Xlsx, DocumentFormat.Ods -> Icons.Filled.GridOn
  DocumentFormat.Unknown -> Icons.Filled.Description
}
