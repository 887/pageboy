package com.eight87.pageboy.ui.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
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
import com.eight87.pageboy.data.library.ViewMode

/**
 * Document list/grid with section headers. Dispatches on [viewMode]:
 * - [ViewMode.List] — LazyColumn (the original list layout)
 * - [ViewMode.Tile] — LazyVerticalGrid with adaptive 160dp columns
 * - [ViewMode.TwoColumn] — LazyVerticalGrid with fixed 2 columns
 *
 * Section headers render across the full grid width when in grid mode.
 */
@Composable
internal fun LibraryDocumentList(
  documents: List<DocumentEntity>,
  sortKey: LibrarySortKey,
  viewMode: ViewMode,
  selectedIds: Set<String>,
  multiSelectActive: Boolean,
  onTap: (DocumentEntity) -> Unit,
  onLongPress: (DocumentEntity) -> Unit,
  onTogglePin: (DocumentEntity) -> Unit,
) {
  when (viewMode) {
    ViewMode.List -> LibraryListView(
      documents = documents,
      sortKey = sortKey,
      selectedIds = selectedIds,
      multiSelectActive = multiSelectActive,
      onTap = onTap,
      onLongPress = onLongPress,
      onTogglePin = onTogglePin,
    )
    ViewMode.Tile -> LibraryGridView(
      documents = documents,
      sortKey = sortKey,
      columns = GridCells.Adaptive(160.dp),
      selectedIds = selectedIds,
      multiSelectActive = multiSelectActive,
      onTap = onTap,
      onLongPress = onLongPress,
      onTogglePin = onTogglePin,
    )
    ViewMode.TwoColumn -> LibraryGridView(
      documents = documents,
      sortKey = sortKey,
      columns = GridCells.Fixed(2),
      selectedIds = selectedIds,
      multiSelectActive = multiSelectActive,
      onTap = onTap,
      onLongPress = onLongPress,
      onTogglePin = onTogglePin,
    )
  }
}

@Composable
private fun LibraryListView(
  documents: List<DocumentEntity>,
  sortKey: LibrarySortKey,
  selectedIds: Set<String>,
  multiSelectActive: Boolean,
  onTap: (DocumentEntity) -> Unit,
  onLongPress: (DocumentEntity) -> Unit,
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
          selected = doc.documentId in selectedIds,
          onTap = { onTap(doc) },
          onLongPress = { onLongPress(doc) },
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

@Composable
private fun LibraryGridView(
  documents: List<DocumentEntity>,
  sortKey: LibrarySortKey,
  columns: GridCells,
  selectedIds: Set<String>,
  multiSelectActive: Boolean,
  onTap: (DocumentEntity) -> Unit,
  onLongPress: (DocumentEntity) -> Unit,
  onTogglePin: (DocumentEntity) -> Unit,
) {
  LazyVerticalGrid(
    columns = columns,
    modifier = Modifier.fillMaxSize().semantics { testTag = "library_document_grid" },
  ) {
    var lastSection: String? = null
    documents.forEach { doc ->
      val section = sectionKey(doc, sortKey)
      if (section != null && section != lastSection) {
        lastSection = section
        item(
          key = "header_$section",
          span = { GridItemSpan(maxLineSpan) },
        ) {
          SectionHeader(text = section)
        }
      }
      item(key = doc.documentId) {
        DocumentTile(
          document = doc,
          selected = doc.documentId in selectedIds,
          onTap = { onTap(doc) },
          onLongPress = { onLongPress(doc) },
          onTogglePin = { onTogglePin(doc) },
        )
      }
    }
  }
}

/**
 * Single document row matching tonearmboy's list row pattern:
 * 16dp horizontal / 10dp vertical padding, 48dp cover icon with 4dp
 * corner radius, 12dp spacer, title (titleSmall, maxLines=1) +
 * subtitle (bodySmall, maxLines=1), MoreVert overflow icon.
 *
 * When [selected] is true (multi-select active), the row gets a
 * `secondaryContainer` background.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun DocumentCard(
  document: DocumentEntity,
  selected: Boolean,
  onTap: () -> Unit,
  onLongPress: () -> Unit,
  onTogglePin: () -> Unit,
) {
  val format = DocumentFormat.fromId(document.format)
  var menuOpen by remember { mutableStateOf(false) }

  val backgroundColor = if (selected) {
    MaterialTheme.colorScheme.secondaryContainer
  } else {
    MaterialTheme.colorScheme.surface
  }

  Row(
    modifier = Modifier
      .fillMaxWidth()
      .background(backgroundColor)
      .combinedClickable(
        onClick = onTap,
        onLongClick = onLongPress,
      )
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
