package com.eight87.pageboy.ui.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.eight87.pageboy.R
import com.eight87.pageboy.data.library.ScanState

/**
 * Scan progress banner matching tonearmboy's pattern: Surface with
 * tonalElevation=2.dp, AnimatedVisibility(fadeIn + expandVertically),
 * Row with count + LinearProgressIndicator.
 */
@Composable
internal fun LibraryScanProgressBanner(state: ScanState.Scanning) {
  AnimatedVisibility(
    visible = true,
    enter = fadeIn() + expandVertically(),
    exit = fadeOut() + shrinkVertically(),
  ) {
    Surface(
      tonalElevation = 2.dp,
      modifier = Modifier
        .fillMaxWidth()
        .semantics { testTag = "library_scan_banner" },
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = stringResource(R.string.library_scan_banner_searching, state.documentsFound),
          style = MaterialTheme.typography.bodySmall,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(12.dp))
        val total = state.total
        if (total != null && total > 0) {
          LinearProgressIndicator(
            progress = { (state.documentsFound.toFloat() / total).coerceIn(0f, 1f) },
            modifier = Modifier.weight(1f),
          )
        } else {
          LinearProgressIndicator(
            modifier = Modifier.weight(1f),
          )
        }
      }
    }
  }
}
