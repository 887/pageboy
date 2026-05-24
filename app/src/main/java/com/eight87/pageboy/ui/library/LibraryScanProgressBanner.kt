package com.eight87.pageboy.ui.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.eight87.pageboy.R
import com.eight87.pageboy.data.library.ScanState

/**
 * Phase B (audit split) — banner shown while a [ScanState.Scanning] is
 * active. Takes only the sealed-state variant it actually renders (the
 * scaffold filters out `Idle` / `Failed` before constructing this).
 */
@Composable
internal fun LibraryScanProgressBanner(state: ScanState.Scanning) {
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 16.dp, vertical = 8.dp)
      .semantics { testTag = "library_scan_banner" },
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ),
  ) {
    Column(modifier = Modifier.padding(12.dp)) {
      Text(
        text = stringResource(R.string.library_scan_banner_searching, state.documentsFound),
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Medium,
      )
      val folder = state.currentFolder
      if (!folder.isNullOrBlank()) {
        Spacer(Modifier.height(2.dp))
        Text(
          text = stringResource(R.string.library_scan_banner_in, folder),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      Spacer(Modifier.height(8.dp))
      LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    }
  }
}
