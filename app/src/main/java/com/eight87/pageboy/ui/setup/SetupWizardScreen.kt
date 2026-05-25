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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.eight87.pageboy.R
import com.eight87.pageboy.data.library.DocumentSourceMode
import com.eight87.pageboy.data.library.FolderType
import com.eight87.pageboy.data.library.PersistedUriPermissionStore
import com.eight87.pageboy.data.library.SetupSettings
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupWizardScreen(
  setupSettings: SetupSettings,
  persistedUriPermissionStore: PersistedUriPermissionStore,
  onSetupComplete: () -> Unit,
) {
  val scope = rememberCoroutineScope()
  val context = LocalContext.current
  var awaitingPermission by remember { mutableStateOf(false) }

  val completeAllFiles: () -> Unit = {
    scope.launch {
      setupSettings.documentSourceMode.set(DocumentSourceMode.AllFiles)
      setupSettings.setupComplete.set(true)
      onSetupComplete()
    }
  }

  // Pre-Android 11: simple runtime permission dialog.
  val requestLegacyStorage = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission(),
  ) { granted ->
    if (granted) completeAllFiles()
  }

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

  // When returning from the Settings page, check if permission was granted.
  val lifecycleOwner = LocalLifecycleOwner.current
  LaunchedEffect(awaitingPermission) {
    if (!awaitingPermission) return@LaunchedEffect
    lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
        Environment.isExternalStorageManager()
      ) {
        awaitingPermission = false
        completeAllFiles()
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

    Card(
      onClick = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
          if (Environment.isExternalStorageManager()) {
            completeAllFiles()
          } else {
            val intent = try {
              Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:${context.packageName}"),
              )
            } catch (_: Exception) {
              Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
            }
            awaitingPermission = true
            context.startActivity(intent)
          }
        } else {
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
