package com.eight87.pageboy.ui.library

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.eight87.pageboy.R

/**
 * Top bar shown during multi-select mode, replacing the standard TopAppBar.
 * Shows a close button, the count of selected items, and bulk actions
 * (pin/unpin, delete from library).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MultiSelectBar(
  count: Int,
  onClose: () -> Unit,
  onPinAll: () -> Unit,
  onDeleteAll: () -> Unit,
) {
  TopAppBar(
    navigationIcon = {
      IconButton(onClick = onClose) {
        Icon(
          imageVector = Icons.Filled.Close,
          contentDescription = stringResource(R.string.multi_select_close_cd),
        )
      }
    },
    title = {
      Text(
        text = stringResource(R.string.multi_select_count, count),
        style = MaterialTheme.typography.titleMedium,
      )
    },
    actions = {
      IconButton(onClick = onPinAll) {
        Icon(
          imageVector = Icons.Filled.PushPin,
          contentDescription = stringResource(R.string.multi_select_pin_cd),
        )
      }
      IconButton(onClick = onDeleteAll) {
        Icon(
          imageVector = Icons.Filled.Delete,
          contentDescription = stringResource(R.string.multi_select_delete_cd),
        )
      }
    },
    colors = TopAppBarDefaults.topAppBarColors(
      containerColor = MaterialTheme.colorScheme.secondaryContainer,
      titleContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
      navigationIconContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
      actionIconContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ),
  )
}
