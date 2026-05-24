package com.eight87.pageboy.ui.library

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import com.eight87.pageboy.R
import com.eight87.pageboy.data.library.DocumentFormat

/**
 * Phase B (audit split) — the filter chip row above the document list.
 * Eight format chips + per-collection chips + a clear-filters button.
 *
 * Takes only the selection sets + the toggle / clear callbacks (R.X.7
 * Compose ISP) — no god-state object.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LibraryFilterChipRow(
  selectedFormats: Set<DocumentFormat>,
  selectedCollections: Set<String>,
  collections: List<String>,
  onToggleFormat: (DocumentFormat) -> Unit,
  onToggleCollection: (String) -> Unit,
  onClear: () -> Unit,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .horizontalScroll(rememberScrollState())
      .padding(horizontal = 8.dp, vertical = 4.dp)
      .semantics { testTag = "library_filter_chip_row" },
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    // Format chips for the eight known formats + Unknown.
    DocumentFormat.entries.forEach { format ->
      val selected = format in selectedFormats
      FilterChip(
        selected = selected,
        onClick = { onToggleFormat(format) },
        label = { Text(formatLabel(format)) },
        leadingIcon = { Icon(formatIcon(format), contentDescription = null, modifier = Modifier.size(18.dp)) },
        modifier = Modifier.semantics { testTag = "library_filter_format_${format.name}" },
      )
    }
    collections.forEach { collection ->
      val selected = collection in selectedCollections
      FilterChip(
        selected = selected,
        onClick = { onToggleCollection(collection) },
        label = { Text(collection) },
      )
    }
    if (selectedFormats.isNotEmpty() || selectedCollections.isNotEmpty()) {
      AssistChip(
        onClick = onClear,
        label = { Text(stringResource(R.string.library_filters_clear)) },
        colors = AssistChipDefaults.assistChipColors(
          containerColor = MaterialTheme.colorScheme.errorContainer,
          labelColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
      )
    }
  }
}
