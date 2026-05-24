package com.eight87.pageboy.ui.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.eight87.pageboy.R

/**
 * Phase C.5 — error state shown when [com.eight87.pageboy.ui.reader.control.ReaderState.Failed].
 * Takes only [reason] + the retry callback (R.X.7).
 */
@Composable
internal fun ReaderErrorState(
  reason: String,
  onRetry: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier
      .fillMaxSize()
      .padding(24.dp)
      .semantics { testTag = "reader_error_state" },
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    Icon(
      imageVector = Icons.Filled.ErrorOutline,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.error,
      modifier = Modifier.height(64.dp),
    )
    Spacer(Modifier.height(16.dp))
    Text(
      text = stringResource(R.string.reader_error_headline),
      style = MaterialTheme.typography.titleMedium,
      textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(8.dp))
    Text(
      text = reason,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(24.dp))
    Button(onClick = onRetry) {
      Text(stringResource(R.string.reader_error_retry))
    }
  }
}
