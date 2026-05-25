package com.eight87.pageboy.ui.setup

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.eight87.pageboy.R
import com.eight87.pageboy.data.library.DocumentSourceMode
import com.eight87.pageboy.data.library.FolderType
import com.eight87.pageboy.data.library.PersistedUriPermissionStore
import com.eight87.pageboy.data.library.SetupSettings
import kotlinx.coroutines.launch

/**
 * First-launch setup wizard. Presents two cards:
 *  1. "Scan all files" — requests `MANAGE_EXTERNAL_STORAGE` (Android 11+)
 *     or `READ_EXTERNAL_STORAGE` (older devices).
 *  2. "Pick folders" — launches the SAF tree picker.
 *
 * After either path completes, sets `setupComplete = true` and the
 * calling composable recomposes away from this screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupWizardScreen(
  setupSettings: SetupSettings,
  persistedUriPermissionStore: PersistedUriPermissionStore,
  onSetupComplete: () -> Unit,
) {
  val scope = rememberCoroutineScope()
  val context = LocalContext.current

  // Track whether we've launched the all-files settings and are waiting
  // for the user to come back.
  var awaitingAllFilesPermission by remember { mutableStateOf(false) }
  var pendingFolderUri by remember { mutableStateOf<Uri?>(null) }

  // SAF folder picker launcher (reuse the same pattern as LibraryFoldersScreen).
  val pickFolder = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocumentTree(),
  ) { uri ->
    if (uri != null) {
      scope.launch {
        persistedUriPermissionStore.addRoot(uri, FolderType.Root)
        setupSettings.documentSourceMode.set(DocumentSourceMode.FolderPicker)
        setupSettings.setupComplete.set(true)
        onSetupComplete()
      }
    }
  }

  // Legacy storage permission launcher for pre-Android 11.
  val requestLegacyStorage = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission(),
  ) { granted ->
    if (granted) {
      scope.launch {
        setupSettings.documentSourceMode.set(DocumentSourceMode.AllFiles)
        setupSettings.setupComplete.set(true)
        onSetupComplete()
      }
    }
  }

  // When returning from the all-files settings screen, check permission.
  val lifecycleOwner = LocalLifecycleOwner.current
  LaunchedEffect(awaitingAllFilesPermission) {
    if (!awaitingAllFilesPermission) return@LaunchedEffect
    lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
        Environment.isExternalStorageManager()
      ) {
        awaitingAllFilesPermission = false
        setupSettings.documentSourceMode.set(DocumentSourceMode.AllFiles)
        setupSettings.setupComplete.set(true)
        onSetupComplete()
      }
    }
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(24.dp)
      .semantics { testTag = "setup_wizard_screen" },
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    Icon(
      imageVector = Icons.Filled.Description,
      contentDescription = null,
      modifier = Modifier.size(72.dp),
      tint = MaterialTheme.colorScheme.primary,
    )

    Spacer(Modifier.height(16.dp))

    Text(
      text = stringResource(R.string.app_name),
      style = MaterialTheme.typography.headlineLarge,
      fontWeight = FontWeight.Bold,
      color = MaterialTheme.colorScheme.onSurface,
    )

    Spacer(Modifier.height(8.dp))

    Text(
      text = stringResource(R.string.setup_wizard_prompt),
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center,
    )

    Spacer(Modifier.height(32.dp))

    // Card 1: Scan all files
    Card(
      onClick = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
          // Android 11+ — launch the system settings for all-files access.
          val intent = Intent(
            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
            Uri.parse("package:${context.packageName}"),
          )
          awaitingAllFilesPermission = true
          context.startActivity(intent)
        } else {
          // Pre-Android 11 — request READ_EXTERNAL_STORAGE.
          requestLegacyStorage.launch(android.Manifest.permission.READ_EXTERNAL_STORAGE)
        }
      },
      colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
      ),
      modifier = Modifier
        .fillMaxWidth()
        .semantics { testTag = "setup_wizard_all_files_card" },
    ) {
      Row(
        modifier = Modifier.padding(16.dp),
        verticalAlignment = Alignment.Top,
      ) {
        Icon(
          imageVector = Icons.Filled.Storage,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(32.dp),
        )
        Spacer(Modifier.size(16.dp))
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = stringResource(R.string.setup_wizard_all_files_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
          )
          Spacer(Modifier.height(4.dp))
          Text(
            text = stringResource(R.string.setup_wizard_all_files_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }

    Spacer(Modifier.height(16.dp))

    // Card 2: Pick folders
    Card(
      onClick = { pickFolder.launch(null) },
      colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
      ),
      modifier = Modifier
        .fillMaxWidth()
        .semantics { testTag = "setup_wizard_pick_folders_card" },
    ) {
      Row(
        modifier = Modifier.padding(16.dp),
        verticalAlignment = Alignment.Top,
      ) {
        Icon(
          imageVector = Icons.Filled.Folder,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(32.dp),
        )
        Spacer(Modifier.size(16.dp))
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = stringResource(R.string.setup_wizard_pick_folders_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
          )
          Spacer(Modifier.height(4.dp))
          Text(
            text = stringResource(R.string.setup_wizard_pick_folders_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }
  }
}
