package com.eight87.pageboy.ui.settings.folders

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eight87.pageboy.R
import com.eight87.pageboy.data.library.FolderType
import com.eight87.pageboy.data.library.LibraryRoot
import com.eight87.pageboy.data.library.PersistedUriPermissionStore
import kotlinx.coroutines.launch

/**
 * Phase B.12 — multi-root management screen. Reachable from Settings →
 * Library → Source folders. Lists each root with its folder mode + path
 * + remove button; "Add folder…" launches the SAF tree-URI picker, then
 * prompts for the folder mode via a modal bottom sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryFoldersScreen(
  persistedUriPermissionStore: PersistedUriPermissionStore,
  onBack: () -> Unit,
) {
  val roots by persistedUriPermissionStore.observeRoots()
    .collectAsStateWithLifecycle(initialValue = emptyList())
  val scope = rememberCoroutineScope()
  var pendingUri by remember { mutableStateOf<Uri?>(null) }

  val pickFolder = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocumentTree(),
  ) { uri -> if (uri != null) pendingUri = uri }

  Column(
    modifier = Modifier.fillMaxSize().semantics { testTag = "library_folders_screen" },
  ) {
    TopAppBar(
      title = { Text(stringResource(R.string.library_folders_title)) },
      navigationIcon = {
        IconButton(onClick = onBack) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = stringResource(R.string.library_folders_back_cd),
          )
        }
      },
    )

    Box(modifier = Modifier.fillMaxSize()) {
      if (roots.isEmpty()) {
        Card(
          colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
          ),
          modifier = Modifier
            .padding(16.dp)
            .align(Alignment.TopCenter)
            .semantics { testTag = "library_folders_empty" },
        ) {
          Text(
            text = stringResource(R.string.library_folders_empty),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(16.dp),
          )
        }
      } else {
        LazyColumn(
          contentPadding = PaddingValues(16.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          items(roots, key = { it.treeUri.toString() }) { root ->
            LibraryRootRow(
              root = root,
              onRemove = {
                scope.launch { persistedUriPermissionStore.removeRoot(root.treeUri) }
              },
            )
          }
        }
      }

      Button(
        onClick = { pickFolder.launch(null) },
        modifier = Modifier
          .align(Alignment.BottomEnd)
          .padding(16.dp)
          .semantics { testTag = "library_folders_add_button" },
      ) {
        Icon(Icons.Filled.Add, contentDescription = null)
        Spacer(Modifier.size(8.dp))
        Text(stringResource(R.string.library_folders_add))
      }
    }
  }

  val pending = pendingUri
  if (pending != null) {
    FolderTypeSheet(
      onDismiss = { pendingUri = null },
      onSelect = { folderType ->
        scope.launch {
          persistedUriPermissionStore.addRoot(pending, folderType)
          pendingUri = null
        }
      },
    )
  }
}

@Composable
private fun LibraryRootRow(root: LibraryRoot, onRemove: () -> Unit) {
  Card(
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.padding(12.dp),
    ) {
      Icon(
        imageVector = Icons.Filled.Folder,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
      )
      Spacer(Modifier.size(12.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = root.displayName,
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Medium,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = stringResource(folderTypeTitle(root.folderType)),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
          text = root.treeUri.toString(),
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      IconButton(onClick = onRemove) {
        Icon(
          imageVector = Icons.Filled.Delete,
          contentDescription = stringResource(R.string.library_folders_remove_cd),
        )
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FolderTypeSheet(
  onDismiss: () -> Unit,
  onSelect: (FolderType) -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = MaterialTheme.colorScheme.surfaceContainer,
    dragHandle = { BottomSheetDefaults.DragHandle() },
  ) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
      Text(
        text = stringResource(R.string.folder_type_dialog_title),
        style = MaterialTheme.typography.titleLarge,
      )
      Spacer(Modifier.size(12.dp))
      FolderType.allOrdered.forEach { type ->
        TextButton(
          onClick = { onSelect(type) },
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(modifier = Modifier.fillMaxWidth()) {
            Text(
              text = stringResource(folderTypeTitle(type)),
              style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.size(2.dp))
            Text(
              text = stringResource(folderTypeSubtitle(type)),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
      Spacer(Modifier.size(8.dp))
      TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
        Text(stringResource(R.string.dialog_cancel))
      }
    }
  }
}

internal fun folderTypeTitle(type: FolderType): Int = when (type) {
  FolderType.SingleFile -> R.string.folder_type_singlefile_title
  FolderType.SingleFolder -> R.string.folder_type_singlefolder_title
  FolderType.Root -> R.string.folder_type_root_title
  FolderType.Category -> R.string.folder_type_category_title
}

private fun folderTypeSubtitle(type: FolderType): Int = when (type) {
  FolderType.SingleFile -> R.string.folder_type_singlefile_subtitle
  FolderType.SingleFolder -> R.string.folder_type_singlefolder_subtitle
  FolderType.Root -> R.string.folder_type_root_subtitle
  FolderType.Category -> R.string.folder_type_category_subtitle
}
