package com.eight87.pageboy.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
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
import com.eight87.pageboy.data.library.LibrarySortKey

/**
 * Sort bottom sheet matching tonearmboy's pattern: RadioButton per sort
 * key + Cancel/OK buttons. Replaces the inline DropdownMenu that was
 * previously in the TopAppBar sort button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LibrarySortSheet(
  currentSortKey: LibrarySortKey,
  onConfirm: (LibrarySortKey) -> Unit,
  onDismiss: () -> Unit,
) {
  var selected by remember(currentSortKey) { mutableStateOf(currentSortKey) }
  val sheetState = rememberModalBottomSheetState()

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 24.dp, vertical = 16.dp)
        .semantics { testTag = "library_sort_sheet" },
    ) {
      Text(
        text = stringResource(R.string.library_sort_sheet_title),
        style = MaterialTheme.typography.headlineSmall,
      )
      Spacer(Modifier.height(16.dp))
      LibrarySortKey.entries.forEach { key ->
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clickable { selected = key }
            .padding(vertical = 4.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          RadioButton(
            selected = selected == key,
            onClick = { selected = key },
          )
          Text(
            text = stringResource(sortKeyLabel(key)),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 8.dp),
          )
        }
      }
      Spacer(Modifier.height(24.dp))
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
      ) {
        TextButton(onClick = onDismiss) {
          Text(stringResource(R.string.dialog_cancel))
        }
        TextButton(onClick = { onConfirm(selected) }) {
          Text(stringResource(R.string.settings_dialog_confirm))
        }
      }
    }
  }
}

/** Resolve a [LibrarySortKey] to its user-visible string resource. */
internal fun sortKeyLabel(key: LibrarySortKey): Int = when (key) {
  LibrarySortKey.TitleAsc -> R.string.library_sort_title_asc
  LibrarySortKey.TitleDesc -> R.string.library_sort_title_desc
  LibrarySortKey.DateAdded -> R.string.library_sort_date_added
  LibrarySortKey.LastOpened -> R.string.library_sort_last_opened
  LibrarySortKey.Format -> R.string.library_sort_format
}
