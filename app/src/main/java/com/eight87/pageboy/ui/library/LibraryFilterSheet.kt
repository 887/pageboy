package com.eight87.pageboy.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import com.eight87.pageboy.R
import com.eight87.pageboy.data.library.DocumentFormat

/**
 * Filter bottom sheet matching tonearmboy's pattern: format checkboxes +
 * collection checkboxes + Reset/Apply buttons. Replaces the inline
 * LibraryFilterChipRow that was displayed below the TopAppBar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LibraryFilterSheet(
  selectedFormats: Set<DocumentFormat>,
  selectedCollections: Set<String>,
  collections: List<String>,
  onApply: (formats: Set<DocumentFormat>, collections: Set<String>) -> Unit,
  onDismiss: () -> Unit,
) {
  var draftFormats by remember(selectedFormats) { mutableStateOf(selectedFormats) }
  var draftCollections by remember(selectedCollections) { mutableStateOf(selectedCollections) }
  val sheetState = rememberModalBottomSheetState()

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 24.dp, vertical = 16.dp)
        .semantics { testTag = "library_filter_sheet" },
    ) {
      Text(
        text = stringResource(R.string.library_filter_sheet_title),
        style = MaterialTheme.typography.headlineSmall,
      )

      // --- Format section ---
      Spacer(Modifier.height(16.dp))
      Text(
        text = stringResource(R.string.library_filter_section_format),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Spacer(Modifier.height(8.dp))
      DocumentFormat.entries.forEach { format ->
        val checked = format in draftFormats
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clickable {
              draftFormats = if (checked) draftFormats - format else draftFormats + format
            }
            .padding(vertical = 2.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Checkbox(
            checked = checked,
            onCheckedChange = {
              draftFormats = if (checked) draftFormats - format else draftFormats + format
            },
          )
          Text(
            text = formatLabel(format),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 8.dp),
          )
        }
      }

      // --- Collection section (only if collections exist) ---
      if (collections.isNotEmpty()) {
        Spacer(Modifier.height(16.dp))
        Text(
          text = stringResource(R.string.library_filter_section_collection),
          style = MaterialTheme.typography.titleSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        collections.forEach { collection ->
          val checked = collection in draftCollections
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clickable {
                draftCollections = if (checked) draftCollections - collection else draftCollections + collection
              }
              .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Checkbox(
              checked = checked,
              onCheckedChange = {
                draftCollections = if (checked) draftCollections - collection else draftCollections + collection
              },
            )
            Text(
              text = collection,
              style = MaterialTheme.typography.bodyLarge,
              modifier = Modifier.padding(start = 8.dp),
            )
          }
        }
      }

      // --- Bottom buttons ---
      Spacer(Modifier.height(24.dp))
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
      ) {
        TextButton(onClick = {
          draftFormats = emptySet()
          draftCollections = emptySet()
        }) {
          Text(stringResource(R.string.library_filter_reset))
        }
        TextButton(onClick = { onApply(draftFormats, draftCollections) }) {
          Text(stringResource(R.string.library_filter_apply))
        }
      }
    }
  }
}
