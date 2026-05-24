package com.eight87.pageboy.ui.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eight87.pageboy.R
import com.eight87.pageboy.data.library.DocumentSource
import com.eight87.pageboy.data.library.LibraryFilters
import com.eight87.pageboy.data.library.LibraryRescanCoordinator
import com.eight87.pageboy.data.library.LibrarySortKey
import com.eight87.pageboy.data.library.LibraryTab
import com.eight87.pageboy.data.library.LibraryUiSettings
import com.eight87.pageboy.data.library.ScanState
import kotlinx.coroutines.launch

/**
 * Phase B.9 — the library screen scaffold.
 *
 * Whisperboy-shape: four tabs (Started / All / Recents / Pinned), filter
 * chip row (format + collection), search overlay, sort menu, document
 * cards with format icon + collection chip + progress bar + Pin overflow.
 *
 * Adapted, not copied — the whisperboy LibraryScreen is ~1200 LOC of
 * audiobook-grid plumbing (cover art, now-playing bar, multi-select, fast
 * scrollbar, author rail). Pageboy doesn't have covers (yet), has no
 * playback service, and the user's brief was specifically tabs + filters,
 * so this implementation is deliberately smaller — same gesture grammar,
 * lighter chrome.
 *
 * Split per the R.D pattern (audit fixup): this file is the scaffold
 * (data-flow wiring, tab dispatch, scan banner gate, snackbar host).
 * Each visual surface — search bar, filter chips, scan banner, empty
 * state, document card + list, format visuals — lives in its own sibling
 * file. Each leaf takes only the narrow params it actually reads
 * (R.X.7).
 *
 * Takes only narrow data interfaces (family SOLID-I pattern): a
 * [DocumentSource] for the catalog and per-document actions, a
 * [LibraryUiSettings] for the persisted UI state, a
 * [LibraryRescanCoordinator] for the scan-progress banner + snackbar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
  documentSource: DocumentSource,
  libraryUiSettings: LibraryUiSettings,
  libraryRescanCoordinator: LibraryRescanCoordinator,
  onDocumentTap: (com.eight87.pageboy.data.library.DocumentEntity) -> Unit,
  modifier: Modifier = Modifier,
  snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
  val docs by documentSource.observeDocuments()
    .collectAsStateWithLifecycle(initialValue = emptyList())
  val recents by documentSource.observeRecents()
    .collectAsStateWithLifecycle(initialValue = emptyList())
  val collections by documentSource.observeCollections()
    .collectAsStateWithLifecycle(initialValue = emptyList())

  val tab by libraryUiSettings.tab.collectAsStateWithLifecycle(initialValue = LibraryTab.All)
  val sortKey by libraryUiSettings.sortKey
    .collectAsStateWithLifecycle(initialValue = LibrarySortKey.TitleAsc)
  val selectedFormats by libraryUiSettings.selectedFormats
    .collectAsStateWithLifecycle(initialValue = emptySet())
  val selectedCollections by libraryUiSettings.selectedCollections
    .collectAsStateWithLifecycle(initialValue = emptySet())
  val scanState by libraryRescanCoordinator.state
    .collectAsStateWithLifecycle(initialValue = ScanState.Idle)

  val scope = rememberCoroutineScope()
  val context = LocalContext.current
  var searchMode by remember { mutableStateOf(false) }
  var searchQuery by remember { mutableStateOf("") }
  var sortMenuOpen by remember { mutableStateOf(false) }

  // "Found N new" snackbar — driven by the coordinator's one-shot flow.
  LaunchedEffect(libraryRescanCoordinator) {
    libraryRescanCoordinator.scanSummaries.collect { summary ->
      if (summary.newDocuments > 0) {
        snackbarHostState.showSnackbar(
          message = context.getString(
            R.string.library_scan_complete_snackbar,
            summary.newDocuments,
          ),
        )
      }
    }
  }

  // Tab-and-filter-and-sort pipeline. Started / All / Pinned go through
  // the standard filter pipeline; Recents uses the dedicated DAO query
  // (already capped + ordered) and just runs through format/collection
  // chips + search.
  //
  // The tab projection itself goes through [LibraryFilters.byTab] — the
  // single source of truth for the started/all/recents/pinned switch
  // (audit fixup: the inline `when (tab)` here used to duplicate the one
  // in `LibraryFilters`, which split the maintenance burden across two
  // files).
  val visibleDocs = remember(
    docs, recents, tab, selectedFormats, selectedCollections, searchQuery, sortKey,
  ) {
    if (tab == LibraryTab.Recents) {
      // Recents is already ordered by `lastOpenedAt DESC` in the DAO.
      // Run only format + collection + search; preserve the order.
      val a = LibraryFilters.byFormat(recents, selectedFormats)
      val b = LibraryFilters.byCollection(a, selectedCollections)
      LibraryFilters.bySearch(b, searchQuery)
    } else {
      LibraryFilters.apply(
        docs = LibraryFilters.byTab(docs, tab),
        formats = selectedFormats,
        collections = selectedCollections,
        search = searchQuery,
        sortKey = sortKey,
      )
    }
  }

  Box(modifier = modifier.fillMaxSize().semantics { testTag = "library_screen" }) {
    Column(modifier = Modifier.fillMaxSize()) {
      // Top app bar: title + search + sort. Sits inside the library
      // surface so it can flip to the search field without affecting the
      // parent shell's app bar.
      if (searchMode) {
        LibrarySearchBar(
          query = searchQuery,
          onQueryChange = { searchQuery = it },
          onClose = {
            searchMode = false
            searchQuery = ""
          },
        )
      } else {
        TopAppBar(
          title = { Text(stringResource(R.string.nav_library)) },
          actions = {
            IconButton(
              onClick = { searchMode = true },
              modifier = Modifier.semantics { testTag = "library_search_button" },
            ) {
              Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.library_search_cd))
            }
            Box {
              IconButton(
                onClick = { sortMenuOpen = true },
                modifier = Modifier.semantics { testTag = "library_sort_button" },
              ) {
                Icon(
                  imageVector = Icons.AutoMirrored.Filled.Sort,
                  contentDescription = stringResource(R.string.library_sort_cd),
                )
              }
              DropdownMenu(
                expanded = sortMenuOpen,
                onDismissRequest = { sortMenuOpen = false },
              ) {
                LibrarySortKey.entries.forEach { option ->
                  DropdownMenuItem(
                    text = { Text(stringResource(sortKeyLabel(option))) },
                    leadingIcon = if (option == sortKey) {
                      { Icon(Icons.Filled.Check, contentDescription = null) }
                    } else null,
                    onClick = {
                      scope.launch { libraryUiSettings.setSortKey(option) }
                      sortMenuOpen = false
                    },
                  )
                }
              }
            }
          },
        )
      }

      // Tab row.
      val currentTabIndex = LibraryTab.entries.indexOf(tab).coerceAtLeast(0)
      TabRow(
        selectedTabIndex = currentTabIndex,
        modifier = Modifier
          .fillMaxWidth()
          .semantics { testTag = "library_tab_row" },
      ) {
        LibraryTab.entries.forEachIndexed { index, t ->
          Tab(
            selected = index == currentTabIndex,
            onClick = { scope.launch { libraryUiSettings.setTab(t) } },
            text = { Text(stringResource(tabLabel(t))) },
            modifier = Modifier.semantics { testTag = "library_tab_${t.name.lowercase()}" },
          )
        }
      }

      // Filter chip row — format + collection (collection only when the
      // catalog actually surfaces collections, which depends on folder
      // mode). Visible whenever the catalog has any documents.
      if (docs.isNotEmpty() || collections.isNotEmpty()) {
        LibraryFilterChipRow(
          selectedFormats = selectedFormats,
          selectedCollections = selectedCollections,
          collections = collections,
          onToggleFormat = { format ->
            val next = selectedFormats.toMutableSet().apply {
              if (contains(format)) remove(format) else add(format)
            }
            scope.launch { libraryUiSettings.setSelectedFormats(next) }
          },
          onToggleCollection = { collection ->
            val next = selectedCollections.toMutableSet().apply {
              if (contains(collection)) remove(collection) else add(collection)
            }
            scope.launch { libraryUiSettings.setSelectedCollections(next) }
          },
          onClear = {
            scope.launch {
              libraryUiSettings.setSelectedFormats(emptySet())
              libraryUiSettings.setSelectedCollections(emptySet())
            }
          },
        )
      }

      // Scan progress banner.
      val scanning = scanState as? ScanState.Scanning
      if (scanning != null) {
        LibraryScanProgressBanner(state = scanning)
      }

      // Body — either empty state per tab or the document list.
      Box(modifier = Modifier.fillMaxSize()) {
        if (visibleDocs.isEmpty()) {
          LibraryEmptyState(tab = tab)
        } else {
          LibraryDocumentList(
            documents = visibleDocs,
            onTap = onDocumentTap,
            onTogglePin = { doc ->
              scope.launch { documentSource.setPinned(doc.documentId, !doc.pinned) }
            },
          )
        }
      }
    }
    SnackbarHost(
      hostState = snackbarHostState,
      modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
    )
  }
}

private fun tabLabel(tab: LibraryTab): Int = when (tab) {
  LibraryTab.Started -> R.string.library_tab_started
  LibraryTab.All -> R.string.library_tab_all
  LibraryTab.Recents -> R.string.library_tab_recents
  LibraryTab.Pinned -> R.string.library_tab_pinned
}

private fun sortKeyLabel(key: LibrarySortKey): Int = when (key) {
  LibrarySortKey.TitleAsc -> R.string.library_sort_title_asc
  LibrarySortKey.TitleDesc -> R.string.library_sort_title_desc
  LibrarySortKey.DateAdded -> R.string.library_sort_date_added
  LibrarySortKey.LastOpened -> R.string.library_sort_last_opened
  LibrarySortKey.Format -> R.string.library_sort_format
}
