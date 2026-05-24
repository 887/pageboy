package com.eight87.pageboy.ui.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
 * Phase B placeholder reader. Real per-format rendering is Phase C–M.
 *
 * Mounted by `PageboyApp` when the user taps a document card. Records
 * the open (via `DocumentSource.recordOpen`) so the Recents tab populates
 * even without a real reader.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
  title: String,
  onBack: () -> Unit,
) {
  Column(modifier = Modifier.fillMaxSize().semantics { testTag = "reader_screen" }) {
    TopAppBar(
      title = { Text(stringResource(R.string.reader_placeholder_title)) },
      navigationIcon = {
        IconButton(onClick = onBack) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = stringResource(R.string.reader_back_cd),
          )
        }
      },
    )
    Box(
      modifier = Modifier.fillMaxSize().padding(24.dp),
      contentAlignment = Alignment.Center,
    ) {
      Text(
        text = stringResource(R.string.reader_placeholder_body, title),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
      )
    }
  }
}
