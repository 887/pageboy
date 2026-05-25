package com.eight87.pageboy.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
import com.eight87.pageboy.domain.render.RendererReadingPrefs
import com.eight87.pageboy.format.registry.FormatRegistry
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

/**
 * Root composable. Lays out the family's locked chrome:
 *
 *   - vertical [NavigationRail] on the left, four top-level destinations
 *     (Library / Recents / Pinned / Settings),
 *   - content host on the right driven by a [NavDisplay] back stack.
 *
 * Phase B drops the top app bar — the [LibraryScreen] now owns its own,
 * with a search field that swaps in place and a sort dropdown. The rail
 * stays.
 *
 * `documentSource` / `libraryUiSettings` / `libraryRescanCoordinator` /
 * `persistedUriPermissionStore` are injectable so tests can substitute
 * fakes; in production they come from [AppGraph] off the
 * [PageboyApplication]. A `null` default keeps the existing
 * `MainScreenSmokeTest` working (it tests the chrome + the empty rail,
 * which renders without the graph).
 */
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

  // Resolve the data layer from the running Application if no overrides
  // were passed. Wrap in remember so the lazy AppGraph build happens
  // exactly once per app process.
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

  // Touch the coordinator so the lazy block runs and start() fires.
  LaunchedEffect(effectiveCoordinator) { /* trigger lazy init */ }

  val backStackSnapshot: List<NavKey> = backStack.toList()
  val selectedRoot: NavKey = remember(backStackSnapshot) {
    backStackSnapshot.findLast { entry ->
      entry is LibraryRoute || entry is RecentsRoute || entry is PinnedRoute || entry is SettingsRootRoute
    } ?: LibraryRoute
  }

  val resetTo: (NavKey) -> Unit = { key ->
    if (backStack.lastOrNull() != key) {
      backStack.clear()
      backStack.add(key)
    }
  }
  val onRailSelect: (NavKey) -> Unit = { key ->
    // Recents / Pinned land on LibraryRoute with the appropriate tab
    // pre-selected — the rail acts as a one-tap shortcut to the
    // LibraryScreen tab. Settings stays its own destination.
    when (key) {
      RecentsRoute -> {
        effectiveUiSettings?.let { scope.launch { it.setTab(LibraryTab.Recents) } }
        resetTo(LibraryRoute)
      }
      PinnedRoute -> {
        effectiveUiSettings?.let { scope.launch { it.setTab(LibraryTab.Pinned) } }
        resetTo(LibraryRoute)
      }
      else -> resetTo(key)
    }
  }

  Row(modifier = Modifier.fillMaxSize().semantics { testTag = "pageboy_root" }) {
    NavigationRail(modifier = Modifier.semantics { testTag = "pageboy_nav_rail" }) {
      RailItem(
        selected = selectedRoot is LibraryRoute,
        onClick = { onRailSelect(LibraryRoute) },
        icon = Icons.Filled.MenuBook,
        label = stringResource(R.string.nav_library),
        testTag = "nav_rail_library",
      )
      RailItem(
        selected = selectedRoot is RecentsRoute,
        onClick = { onRailSelect(RecentsRoute) },
        icon = Icons.Outlined.History,
        label = stringResource(R.string.nav_recents),
        testTag = "nav_rail_recents",
      )
      RailItem(
        selected = selectedRoot is PinnedRoute,
        onClick = { onRailSelect(PinnedRoute) },
        icon = Icons.Filled.PushPin,
        label = stringResource(R.string.nav_pinned),
        testTag = "nav_rail_pinned",
      )
      RailItem(
        selected = selectedRoot is SettingsRootRoute,
        onClick = { onRailSelect(SettingsRootRoute) },
        icon = Icons.Filled.Settings,
        label = stringResource(R.string.nav_settings),
        testTag = "nav_rail_settings",
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
            )
          } else {
            // Defensive fallback when no graph is available (e.g. the
            // legacy MainScreenSmokeTest renders the chrome alone).
            PlaceholderScreen(
              text = stringResource(R.string.library_placeholder),
              testTag = "library_placeholder",
            )
          }
        }
        entry<RecentsRoute> {
          // Rail-shortcut destinations route to LibraryRoute with the
          // tab pre-selected; if the user lands here directly somehow,
          // render the same placeholder so nothing crashes.
          PlaceholderScreen(
            text = stringResource(R.string.recents_placeholder),
            testTag = "recents_placeholder",
          )
        }
        entry<PinnedRoute> {
          PlaceholderScreen(
            text = stringResource(R.string.pinned_placeholder),
            testTag = "pinned_placeholder",
          )
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
            // One FindInDocCommands per reader instance — find state is
            // per-document, see AppGraph.findInDocCommandsFactory.
            val find = remember(route.documentId) { findFactory() }
            // Phase H — per-reader signing commands. The sheet is
            // rendered above PdfBody by ReaderScreen; production
            // wiring of the adapter (Pkcs12 / Keystore / PadesSigner)
            // happens inside ReaderScreen via the AppGraph passed
            // through `signingCommands`.
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

@Composable
private fun RailItem(
  selected: Boolean,
  onClick: () -> Unit,
  icon: ImageVector,
  label: String,
  testTag: String,
) {
  NavigationRailItem(
    selected = selected,
    onClick = onClick,
    icon = { Icon(icon, contentDescription = label) },
    label = { Text(label) },
    modifier = Modifier.semantics { this.testTag = testTag },
  )
}

@Suppress("unused")
@Composable
private fun RailFooterSlot(content: @Composable () -> Unit) {
  Column(content = { content() })
}
