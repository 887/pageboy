package com.eight87.pageboy.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.eight87.pageboy.AppGraph
import com.eight87.pageboy.PageboyApplication
import com.eight87.pageboy.R
import com.eight87.pageboy.data.library.DocumentSource
import com.eight87.pageboy.data.library.LibraryRescanCoordinator
import com.eight87.pageboy.data.library.LibraryTab
import com.eight87.pageboy.data.library.LibraryUiSettings
import com.eight87.pageboy.data.library.PersistedUriPermissionStore
import com.eight87.pageboy.data.library.SetupSettings
import com.eight87.pageboy.ui.setup.SetupWizardScreen
import kotlinx.coroutines.flow.flowOf
import com.eight87.pageboy.domain.render.RendererReadingPrefs
import com.eight87.pageboy.format.registry.FormatRegistry
import com.eight87.pageboy.ui.library.LibraryRail
import com.eight87.pageboy.ui.library.LibraryScreen
import com.eight87.pageboy.ui.reader.ReaderScreen
import com.eight87.pageboy.ui.reader.control.InMemoryFindInDocCommands
import com.eight87.pageboy.ui.reader.control.InMemorySigningCommands
import com.eight87.pageboy.ui.reader.control.ReaderStateProjector
import com.eight87.pageboy.ui.reader.control.ScrollPersistence
import com.eight87.pageboy.ui.reader.control.ShareExportCommands
import com.eight87.pageboy.ui.settings.AboutScreen
import com.eight87.pageboy.ui.settings.LicensesScreen
import com.eight87.pageboy.ui.settings.SettingsScreen
import com.eight87.pageboy.ui.settings.folders.LibraryFoldersScreen
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageboyApp(
  documentSource: DocumentSource? = null,
  libraryUiSettings: LibraryUiSettings? = null,
  libraryRescanCoordinator: LibraryRescanCoordinator? = null,
  persistedUriPermissionStore: PersistedUriPermissionStore? = null,
) {
  val backStack = rememberNavBackStack(LibraryRoute)
  val scope = rememberCoroutineScope()

  val context = LocalContext.current
  val appGraph: AppGraph? = remember(context) {
    when (val app = context.applicationContext) {
      is PageboyApplication -> app.appGraph
      else -> null
    }
  }
  val effectiveDocSource = documentSource ?: appGraph?.documentSource
  val effectiveUiSettings = libraryUiSettings ?: appGraph?.libraryUiSettings
  val effectiveCoordinator = libraryRescanCoordinator ?: appGraph?.libraryRescanCoordinator
  val effectiveRootStore = persistedUriPermissionStore ?: appGraph?.persistedUriPermissionStore
  val effectiveProjector: ReaderStateProjector? = appGraph?.readerStateProjector
  val effectiveFormatRegistry: FormatRegistry? = appGraph?.formatRegistry
  val effectiveShareCommands: ShareExportCommands? = appGraph?.shareExportCommands
  val findFactory: (() -> InMemoryFindInDocCommands)? = appGraph?.findInDocCommandsFactory
  val effectiveScrollPersistence: ScrollPersistence? = appGraph?.scrollPersistence
  val effectiveReadingPrefs: RendererReadingPrefs? = appGraph?.rendererReadingPrefs
  val signingFactory: (() -> InMemorySigningCommands)? = appGraph?.signingCommandsFactory
  val effectiveSetupSettings: SetupSettings? = appGraph?.setupSettings

  // ---- Setup wizard detection ----
  // Show the wizard if setup is not complete AND no library roots exist.
  // For retroactive support: existing installs where setup_complete was
  // never written will default to false, triggering the wizard on next
  // launch.
  val setupComplete by (effectiveSetupSettings?.setupComplete?.flow
    ?: flowOf(true)).collectAsStateWithLifecycle(initialValue = true)
  val hasRoots by (effectiveRootStore?.observeRoots()
    ?: flowOf(emptyList())).collectAsStateWithLifecycle(initialValue = null)

  val showWizard = !setupComplete && (hasRoots == null || hasRoots!!.isEmpty())

  LaunchedEffect(effectiveCoordinator) { /* trigger lazy init */ }

  val currentTab by (effectiveUiSettings?.tab
    ?: flowOf(LibraryTab.All))
    .collectAsStateWithLifecycle(initialValue = LibraryTab.All)

  val backStackSnapshot: List<NavKey> = backStack.toList()
  val onLibraryRoute = remember(backStackSnapshot) {
    backStackSnapshot.lastOrNull() is LibraryRoute
  }

  // Show setup wizard instead of the main library if first-launch
  // conditions are met.
  if (showWizard && effectiveSetupSettings != null && effectiveRootStore != null) {
    SetupWizardScreen(
      setupSettings = effectiveSetupSettings,
      persistedUriPermissionStore = effectiveRootStore,
      onSetupComplete = {
        // Trigger a rescan after setup completes.
        effectiveCoordinator?.requestRescan()
      },
    )
    return
  }

  Row(modifier = Modifier.fillMaxSize().semantics { testTag = "pageboy_root" }) {
    if (onLibraryRoute && effectiveUiSettings != null) {
      LibraryRail(
        tabs = LibraryTab.entries,
        selectedTab = currentTab,
        onSelectTab = { tab -> scope.launch { effectiveUiSettings.setTab(tab) } },
        onOpenSettings = {
          backStack.add(SettingsRootRoute)
        },
      )
    }

    NavDisplay(
      backStack = backStack,
      onBack = { backStack.removeLastOrNull() },
      modifier = Modifier
        .fillMaxSize()
        .semantics { testTag = "pageboy_nav_host" },
      entryProvider = entryProvider {
        entry<LibraryRoute> {
          if (effectiveDocSource != null && effectiveUiSettings != null &&
            effectiveCoordinator != null
          ) {
            LibraryScreen(
              documentSource = effectiveDocSource,
              libraryUiSettings = effectiveUiSettings,
              libraryRescanCoordinator = effectiveCoordinator,
              onDocumentTap = { doc ->
                scope.launch { effectiveDocSource.recordOpen(doc.documentId) }
                backStack.add(ReaderRoute(doc.documentId))
              },
              onOpenSettings = { backStack.add(SettingsRootRoute) },
            )
          } else {
            PlaceholderScreen(
              text = stringResource(R.string.library_placeholder),
              testTag = "library_placeholder",
            )
          }
        }
        entry<SettingsRootRoute> {
          SettingsScreen(
            onAbout = { backStack.add(SettingsAboutRoute) },
            onLibraryFolders = { backStack.add(SettingsLibraryFoldersRoute) },
            onRescanNow = { effectiveCoordinator?.requestRescan() },
            readerSettings = appGraph?.readerSettings,
            themeSettings = appGraph?.themeSettings,
            signingSettings = appGraph?.signingSettings,
            openWithSettings = appGraph?.openWithSettings,
            onManageSigningKeys = { /* TODO Phase H.6 sub-screen */ },
            onResetSigningKeys = { /* TODO Phase H.6 confirmation dialog */ },
          )
        }
        entry<SettingsAboutRoute> {
          AboutScreen(
            onBack = { backStack.removeLastOrNull() },
            onLicenses = { backStack.add(SettingsLicensesRoute) },
          )
        }
        entry<SettingsLicensesRoute> {
          LicensesScreen(onBack = { backStack.removeLastOrNull() })
        }
        entry<SettingsLibraryFoldersRoute> {
          if (effectiveRootStore != null) {
            LibraryFoldersScreen(
              persistedUriPermissionStore = effectiveRootStore,
              onBack = { backStack.removeLastOrNull() },
            )
          }
        }
        entry<ReaderRoute> { route ->
          if (effectiveProjector != null && effectiveFormatRegistry != null &&
            effectiveShareCommands != null && findFactory != null &&
            effectiveScrollPersistence != null && effectiveReadingPrefs != null
          ) {
            val find = remember(route.documentId) { findFactory() }
            val signing = remember(route.documentId) { signingFactory?.invoke() }
            ReaderScreen(
              documentId = route.documentId,
              readerStateProjector = effectiveProjector,
              formatRegistry = effectiveFormatRegistry,
              findInDocCommands = find,
              shareExportCommands = effectiveShareCommands,
              scrollPersistence = effectiveScrollPersistence,
              readingPrefs = effectiveReadingPrefs,
              onBack = { backStack.removeLastOrNull() },
              signingCommands = signing,
            )
          }
        }
      },
    )
  }
}

@Composable
private fun PlaceholderScreen(text: String, testTag: String) {
  Box(
    modifier = Modifier
      .fillMaxSize()
      .padding(24.dp)
      .semantics { this.testTag = testTag },
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = text,
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center,
    )
  }
}
