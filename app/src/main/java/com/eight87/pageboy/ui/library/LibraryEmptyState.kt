package com.eight87.pageboy.ui.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import com.eight87.pageboy.data.library.LibraryTab

/**
 * Phase B (audit split) — per-tab empty-state card. Takes only the [tab]
 * it renders for (no god-state); the message string id table is internal
 * to this file.
 */
@Composable
internal fun LibraryEmptyState(tab: LibraryTab) {
  val message = when (tab) {
    LibraryTab.Started -> R.string.library_empty_started
    LibraryTab.All -> R.string.library_empty_all
    LibraryTab.Recents -> R.string.library_empty_recents
    LibraryTab.Pinned -> R.string.library_empty_pinned
  }
  Box(
    modifier = Modifier
      .fillMaxSize()
      .padding(32.dp)
      .semantics { testTag = "library_empty_state_${tab.name.lowercase()}" },
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = stringResource(message),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}
