package com.eight87.pageboy.ui.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import com.eight87.pageboy.R

/**
 * Phase C.5 — find-in-doc panel. Shown when the chrome's `findActive`
 * state is on. Takes only the narrow fields it renders (R.X.7):
 * the query string, the match summary (current index + total), and
 * three callbacks.
 *
 * Phase C wires the chrome plumbing through to
 * [com.eight87.pageboy.ui.reader.control.FindInDocCommands]; per-format
 * renderers fill in real match emission in Phase D+. With no real
 * matches yet the count summary stays at "0 / 0" — visible affordance,
 * intentionally inert, which is what the placeholder body deserves.
 */
@Composable
internal fun ReaderFindPanel(
  query: String,
  currentMatchIndex: Int,
  matchCount: Int,
  onQueryChange: (String) -> Unit,
  onNext: () -> Unit,
  onPrevious: () -> Unit,
  onClose: () -> Unit,
) {
  Surface(
    color = MaterialTheme.colorScheme.surfaceContainerHigh,
    modifier = Modifier
      .fillMaxWidth()
      .semantics { testTag = "reader_find_panel" },
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(4.dp),
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
      OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text(stringResource(R.string.reader_find_placeholder)) },
        singleLine = true,
        modifier = Modifier
          .weight(1f)
          .semantics { testTag = "reader_find_field" },
      )
      Text(
        text = matchCountLabel(currentMatchIndex, matchCount),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
          .padding(horizontal = 4.dp)
          .semantics { testTag = "reader_find_count" },
      )
      IconButton(
        onClick = onPrevious,
        enabled = matchCount > 0,
        modifier = Modifier.semantics { testTag = "reader_find_prev" },
      ) {
        Icon(
          imageVector = Icons.Filled.KeyboardArrowUp,
          contentDescription = stringResource(R.string.reader_find_prev_cd),
        )
      }
      IconButton(
        onClick = onNext,
        enabled = matchCount > 0,
        modifier = Modifier.semantics { testTag = "reader_find_next" },
      ) {
        Icon(
          imageVector = Icons.Filled.KeyboardArrowDown,
          contentDescription = stringResource(R.string.reader_find_next_cd),
        )
      }
      IconButton(
        onClick = onClose,
        modifier = Modifier.semantics { testTag = "reader_find_close" },
      ) {
        Icon(
          imageVector = Icons.Filled.Close,
          contentDescription = stringResource(R.string.reader_find_close_cd),
        )
      }
    }
  }
}

private fun matchCountLabel(currentIndex: Int, total: Int): String {
  if (total <= 0) return "0 / 0"
  val displayIndex = (currentIndex + 1).coerceAtLeast(1)
  return "$displayIndex / $total"
}
