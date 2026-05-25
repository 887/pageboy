package com.eight87.pageboy.ui.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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

/**
 * Tile card composable for the grid view modes (Tile / TwoColumn).
 * Matches tonearmboy's TileCell pattern: cover area with aspect ratio 1:1,
 * format icon centered, overflow menu top-right, title + subtitle below.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun DocumentTile(
  document: DocumentEntity,
  selected: Boolean,
  onTap: () -> Unit,
  onLongPress: () -> Unit,
  onTogglePin: () -> Unit,
) {
  val format = DocumentFormat.fromId(document.format)
  var menuOpen by remember { mutableStateOf(false) }

  val shape = RoundedCornerShape(12.dp)
  val borderModifier = if (selected) {
    Modifier.border(3.dp, MaterialTheme.colorScheme.primary, shape)
  } else {
    Modifier
  }

  Column(
    modifier = Modifier
      .padding(4.dp)
      .then(borderModifier)
      .clip(shape)
      .combinedClickable(
        onClick = onTap,
        onLongClick = onLongPress,
      )
      .semantics { testTag = "document_tile_${document.documentId.take(8)}" },
  ) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .aspectRatio(1f)
        .clip(RoundedCornerShape(12.dp))
        .background(MaterialTheme.colorScheme.surfaceVariant),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        imageVector = formatIcon(format),
        contentDescription = null,
        modifier = Modifier.size(48.dp),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      // Overflow icon top-right
      Box(modifier = Modifier.align(Alignment.TopEnd)) {
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
    Text(
      text = document.title,
      style = MaterialTheme.typography.titleSmall,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.padding(horizontal = 8.dp).padding(top = 4.dp),
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
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(horizontal = 8.dp).padding(bottom = 8.dp),
    )
  }
}
