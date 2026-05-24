package com.eight87.pageboy.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import com.eight87.pageboy.R

/**
 * Phase B (audit split) — the in-library search field that swaps in for
 * the title bar when the user taps the magnifier icon. Pure leaf — takes
 * the query + the change/close callbacks, no state of its own.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LibrarySearchBar(
  query: String,
  onQueryChange: (String) -> Unit,
  onClose: () -> Unit,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .background(MaterialTheme.colorScheme.surface)
      .padding(horizontal = 4.dp, vertical = 8.dp)
      .semantics { testTag = "library_search_bar" },
    verticalAlignment = Alignment.CenterVertically,
  ) {
    IconButton(onClick = onClose) {
      Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.library_search_close_cd))
    }
    TextField(
      value = query,
      onValueChange = onQueryChange,
      placeholder = { Text(stringResource(R.string.library_search_hint)) },
      singleLine = true,
      colors = TextFieldDefaults.colors(
        focusedContainerColor = MaterialTheme.colorScheme.surface,
        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
      ),
      trailingIcon = {
        if (query.isNotEmpty()) {
          IconButton(onClick = { onQueryChange("") }) {
            Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.library_search_clear_cd))
          }
        }
      },
      modifier = Modifier.fillMaxWidth(),
    )
  }
}
