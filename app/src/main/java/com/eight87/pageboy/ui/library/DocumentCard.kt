package com.eight87.pageboy.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.eight87.pageboy.R
import com.eight87.pageboy.data.library.DocumentEntity
import com.eight87.pageboy.data.library.DocumentFormat

/**
 * Phase B (audit split) — the per-document card + the LazyColumn that
 * lists them.
 *
 * The card takes the entity + two callbacks (`onTap`, `onTogglePin`) —
 * the fields it actually reads, not a god-state (R.X.7). The list takes
 * the resolved list of documents + the per-row callbacks.
 */

@Composable
internal fun LibraryDocumentList(
  documents: List<DocumentEntity>,
  onTap: (DocumentEntity) -> Unit,
  onTogglePin: (DocumentEntity) -> Unit,
) {
  LazyColumn(
    contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 16.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
    modifier = Modifier.fillMaxSize().semantics { testTag = "library_document_list" },
  ) {
    items(documents, key = { it.documentId }) { doc ->
      DocumentCard(
        document = doc,
        onTap = { onTap(doc) },
        onTogglePin = { onTogglePin(doc) },
      )
    }
  }
}

@Composable
internal fun DocumentCard(
  document: DocumentEntity,
  onTap: () -> Unit,
  onTogglePin: () -> Unit,
) {
  val format = DocumentFormat.fromId(document.format)
  var menuOpen by remember { mutableStateOf(false) }
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onTap)
      .semantics { testTag = "document_card_${document.documentId.take(8)}" },
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ),
    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
  ) {
    Column(modifier = Modifier.padding(12.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
          modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.primaryContainer),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = formatIcon(format),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
          )
        }
        Spacer(Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = document.title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
          val subtitle = buildString {
            append(formatLabel(format))
            document.collection?.let {
              append(" · ")
              append(it)
            }
          }
          Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
        Box {
          IconButton(onClick = { menuOpen = true }) {
            Icon(
              imageVector = Icons.Filled.MoreVert,
              contentDescription = stringResource(R.string.library_card_overflow_cd),
            )
          }
          DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false },
          ) {
            DropdownMenuItem(
              text = {
                Text(
                  stringResource(
                    if (document.pinned) R.string.library_card_action_unpin
                    else R.string.library_card_action_pin,
                  ),
                )
              },
              leadingIcon = {
                Icon(
                  imageVector = if (document.pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                  contentDescription = null,
                )
              },
              onClick = {
                onTogglePin()
                menuOpen = false
              },
            )
          }
        }
      }
      // Read progress indicator — visible whenever the user has started
      // the document. The line stays out of the way for unstarted docs
      // so the All tab feels light.
      if (document.readFraction > 0f) {
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
          progress = { document.readFraction.coerceIn(0f, 1f) },
          modifier = Modifier.fillMaxWidth().height(4.dp),
        )
      }
    }
  }
}
