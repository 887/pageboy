package com.eight87.pageboy.ui.library

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.eight87.pageboy.data.library.DocumentEntity
import com.eight87.pageboy.data.library.DocumentFormat
import com.eight87.pageboy.data.library.LibrarySortKey

/**
 * Sticky section header matching tonearmboy's pattern: rounded surface
 * chip with `surfaceContainerHigh` background and `labelLarge` text.
 */
@Composable
internal fun SectionHeader(
  text: String,
  modifier: Modifier = Modifier,
) {
  Surface(
    shape = RoundedCornerShape(12.dp),
    color = MaterialTheme.colorScheme.surfaceContainerHigh,
    modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
  ) {
    Text(
      text = text,
      style = MaterialTheme.typography.labelLarge,
      color = MaterialTheme.colorScheme.onSurface,
      modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
  }
}

/**
 * Compute the section-header key for a document given the current sort.
 * Returns `null` when the sort key doesn't produce meaningful groups
 * (e.g. title ascending — every doc would get its own header).
 */
internal fun sectionKey(doc: DocumentEntity, sortKey: LibrarySortKey): String? = when (sortKey) {
  LibrarySortKey.Format -> {
    val format = DocumentFormat.fromId(doc.format)
    formatLabel(format)
  }
  LibrarySortKey.TitleAsc, LibrarySortKey.TitleDesc -> {
    val first = doc.title.firstOrNull()?.uppercaseChar()
    if (first != null && first.isLetter()) first.toString() else "#"
  }
  LibrarySortKey.DateAdded, LibrarySortKey.LastOpened -> null
}
