package com.eight87.pageboy.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.eight87.pageboy.R
import com.eight87.pageboy.data.library.DocumentEntity
import com.eight87.pageboy.data.library.DocumentFormat
import com.eight87.pageboy.data.library.LibrarySortKey

/**
 * Document list with section headers. The list renders a flat sequence of
 * documents with optional sticky-ish section headers driven by the current
 * sort key (Format sorts get a header per format; Title sorts get a header
 * per first letter; date-based sorts skip headers).
 */
@Composable
internal fun LibraryDocumentList(
  documents: List<DocumentEntity>,
  sortKey: LibrarySortKey,
  onTap: (DocumentEntity) -> Unit,
  onTogglePin: (DocumentEntity) -> Unit,
) {
  LazyColumn(
    modifier = Modifier.fillMaxSize().semantics { testTag = "library_document_list" },
  ) {
    var lastSection: String? = null
    documents.forEachIndexed { index, doc ->
      val section = sectionKey(doc, sortKey)
      if (section != null && section != lastSection) {
        lastSection = section
        item(key = "header_$section") {
          SectionHeader(text = section)
        }
      }
      item(key = doc.documentId) {
        DocumentCard(
          document = doc,
          onTap = { onTap(doc) },
          onTogglePin = { onTogglePin(doc) },
        )
        if (index < documents.lastIndex) {
          HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp),
            color = MaterialTheme.colorScheme.outlineVariant,
          )
        }
      }
    }
  }
}

/**
 * Single document row matching tonearmboy's list row pattern:
 * 16dp horizontal / 10dp vertical padding, 48dp cover icon with 4dp
 * corner radius, 12dp spacer, title (titleSmall, maxLines=1) +
 * subtitle (bodySmall, maxLines=1), MoreVert overflow icon.
 */
@Composable
internal fun DocumentCard(
  document: DocumentEntity,
  onTap: () -> Unit,
  onTogglePin: () -> Unit,
) {
  val format = DocumentFormat.fromId(document.format)
  var menuOpen by remember { mutableStateOf(false) }

  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onTap)
      .padding(horizontal = 16.dp, vertical = 10.dp)
      .semantics { testTag = "document_card_${document.documentId.take(8)}" },
    verticalAlignment = Alignment.CenterVertically,
  ) {
    // Cover icon — 48dp with 4dp corner radius
    Box(
      modifier = Modifier
        .size(48.dp)
        .clip(RoundedCornerShape(4.dp))
        .background(MaterialTheme.colorScheme.primaryContainer),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        imageVector = formatIcon(format),
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onPrimaryContainer,
      )
    }
    Spacer(Modifier.width(12.dp))
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = document.title,
        style = MaterialTheme.typography.titleSmall,
        maxLines = 1,
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
}
