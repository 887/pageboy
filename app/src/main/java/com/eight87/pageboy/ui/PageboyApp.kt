package com.eight87.pageboy.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.eight87.pageboy.R
import com.eight87.pageboy.ui.settings.AboutScreen
import com.eight87.pageboy.ui.settings.LicensesScreen
import com.eight87.pageboy.ui.settings.SettingsScreen

/**
 * Root composable. Lays out the family's locked chrome:
 *
 *   - vertical [NavigationRail] on the left, four top-level destinations
 *     (Library / Recents / Pinned / Settings — names tentative per
 *     [Navigation]),
 *   - [TopAppBar] across the top with title + Search + overflow,
 *   - content host on the right driven by a [NavDisplay] back stack.
 *
 * Sub-pages (About, Licenses) are pushed onto the back stack from inside
 * the Settings destination; the back arrow in their `TopAppBar` pops the
 * stack and the rail still highlights "Settings" since the underlying
 * top-level route is unchanged.
 *
 * The locked shape comes from `tonearmboy/ui/nav/TonearmboyApp.kt` with
 * the music-player-specific pieces (overlay sheet, mini-player, palette
 * locals) stripped — a document reader doesn't have a now-playing
 * surface to host. See `docs/plans/ui-shell.md`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageboyApp() {
  val backStack = rememberNavBackStack(LibraryRoute)

  // The rail-selected top-level destination is whichever top-level key is
  // closest to the bottom of the back stack — sub-pages pushed on top
  // don't change which rail entry highlights. Snapshot the list so
  // `remember` notices structural changes.
  val backStackSnapshot: List<NavKey> = backStack.toList()
  val selectedRoot: NavKey = remember(backStackSnapshot) {
    backStackSnapshot.findLast { entry ->
      entry is LibraryRoute || entry is RecentsRoute || entry is PinnedRoute || entry is SettingsRootRoute
    } ?: LibraryRoute
  }

  val onRailSelect: (NavKey) -> Unit = { key ->
    // Tapping a rail entry resets the stack to just that root — the
    // family pattern for top-level switches. If the user is already on
    // that root, it's a no-op (don't keep pushing duplicates).
    if (backStack.lastOrNull() != key) {
      backStack.clear()
      backStack.add(key)
    }
  }

  Row(modifier = Modifier.fillMaxSize().semantics { testTag = "pageboy_root" }) {
    NavigationRail(
      modifier = Modifier.semantics { testTag = "pageboy_nav_rail" },
    ) {
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

    Scaffold(
      modifier = Modifier.fillMaxSize(),
      topBar = {
        TopAppBar(
          title = {
            Text(
              stringResource(R.string.app_name),
              modifier = Modifier.semantics { testTag = "top_bar_title" },
            )
          },
          actions = {
            IconButton(
              onClick = { /* Phase B+: search */ },
              modifier = Modifier.semantics { testTag = "top_bar_search" },
            ) {
              Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = stringResource(R.string.top_bar_search_cd),
              )
            }
            IconButton(
              onClick = { /* Phase B+: overflow menu */ },
              modifier = Modifier.semantics { testTag = "top_bar_overflow" },
            ) {
              Icon(
                imageVector = Icons.Filled.MoreVert,
                contentDescription = stringResource(R.string.top_bar_overflow_cd),
              )
            }
          },
        )
      },
    ) { innerPadding ->
      NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        modifier = Modifier
          .fillMaxSize()
          .padding(innerPadding)
          .semantics { testTag = "pageboy_nav_host" },
        entryProvider = entryProvider {
          entry<LibraryRoute> {
            PlaceholderScreen(
              text = stringResource(R.string.library_placeholder),
              testTag = "library_placeholder",
            )
          }
          entry<RecentsRoute> {
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
        },
      )
    }
  }
}

/**
 * Small placeholder body for the Library / Recents / Pinned destinations
 * that won't get real content until their respective phases land.
 */
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

/**
 * One rail entry. Wrapper around [NavigationRailItem] so the call sites
 * stay readable and the testTag plumbing lives in one place.
 */
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

/**
 * Stacked-column helper kept inline so the rail can grow a footer
 * (account avatar / FAB) later without a refactor. Currently unused —
 * but the import structure is here, so adding a footer to the rail in
 * Phase B (e.g. an "Add folder" SAF entry point) is a one-call addition.
 */
@Suppress("unused")
@Composable
private fun RailFooterSlot(content: @Composable () -> Unit) {
  Column(content = { content() })
}
