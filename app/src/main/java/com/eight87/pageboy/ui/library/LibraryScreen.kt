package com.eight87.pageboy.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ViewColumn
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import com.eight87.pageboy.data.library.ViewMode
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
  documentSource: DocumentSource,
  libraryUiSettings: LibraryUiSettings,
  libraryRescanCoordinator: LibraryRescanCoordinator,
  onDocumentTap: (com.eight87.pageboy.data.library.DocumentEntity) -> Unit,
  onOpenSettings: () -> Unit = {},
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
  val viewMode by libraryUiSettings.viewMode
    .collectAsStateWithLifecycle(initialValue = ViewMode.List)
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
  var showSortSheet by remember { mutableStateOf(false) }
  var showFilterSheet by remember { mutableStateOf(false) }

  // --- Multi-select state ---
  var multiSelectActive by remember { mutableStateOf(false) }
  var selectedIds by remember { mutableStateOf(emptySet<String>()) }

  // Back gesture closes multi-select
  BackHandler(enabled = multiSelectActive) {
    multiSelectActive = false
    selectedIds = emptySet()
  }

  val filtersActive = selectedFormats.isNotEmpty() || selectedCollections.isNotEmpty()

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

  val visibleDocs = remember(
    docs, recents, tab, selectedFormats, selectedCollections, searchQuery, sortKey,
  ) {
    if (tab == LibraryTab.Recents) {
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

  // --- Sort bottom sheet ---
  if (showSortSheet) {
    LibrarySortSheet(
      currentSortKey = sortKey,
      onConfirm = { newKey ->
        scope.launch { libraryUiSettings.setSortKey(newKey) }
        showSortSheet = false
      },
      onDismiss = { showSortSheet = false },
    )
  }

  // --- Filter bottom sheet ---
  if (showFilterSheet) {
    LibraryFilterSheet(
      selectedFormats = selectedFormats,
      selectedCollections = selectedCollections,
      collections = collections,
      onApply = { formats, colls ->
        scope.launch {
          libraryUiSettings.setSelectedFormats(formats)
          libraryUiSettings.setSelectedCollections(colls)
        }
        showFilterSheet = false
      },
      onDismiss = { showFilterSheet = false },
    )
  }

  // Resolve the view mode icon for the current mode (shows the current mode).
  val viewModeIcon = when (viewMode) {
    ViewMode.List -> Icons.AutoMirrored.Filled.ViewList
    ViewMode.Tile -> Icons.Filled.GridView
    ViewMode.TwoColumn -> Icons.Filled.ViewColumn
  }

  Box(modifier = modifier.fillMaxSize().semantics { testTag = "library_screen" }) {
    Column(modifier = Modifier.fillMaxSize()) {
      if (multiSelectActive) {
        MultiSelectBar(
          count = selectedIds.size,
          onClose = {
            multiSelectActive = false
            selectedIds = emptySet()
          },
          onPinAll = {
            scope.launch {
              for (id in selectedIds) {
                documentSource.setPinned(id, true)
              }
              multiSelectActive = false
              selectedIds = emptySet()
            }
          },
          onDeleteAll = {
            scope.launch {
              documentSource.deleteDocuments(selectedIds)
              multiSelectActive = false
              selectedIds = emptySet()
            }
          },
        )
      } else if (searchMode) {
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
            IconButton(
              onClick = { showSortSheet = true },
              modifier = Modifier.semantics { testTag = "library_sort_button" },
            ) {
              Icon(
                imageVector = Icons.AutoMirrored.Filled.Sort,
                contentDescription = stringResource(R.string.library_sort_cd),
              )
            }
            // View Mode icon — cycles List → Tile → TwoColumn → List.
            IconButton(
              onClick = {
                scope.launch { libraryUiSettings.setViewMode(viewMode.next()) }
              },
              modifier = Modifier.semantics { testTag = "library_view_mode_button" },
            ) {
              Icon(
                imageVector = viewModeIcon,
                contentDescription = stringResource(R.string.library_view_mode_cd),
              )
            }
            // Filter icon with badge when filters are active.
            IconButton(
              onClick = { showFilterSheet = true },
              modifier = Modifier.semantics { testTag = "library_filter_button" },
            ) {
              BadgedBox(
                badge = {
                  if (filtersActive) {
                    Badge(modifier = Modifier.size(6.dp))
                  }
                },
              ) {
                Icon(
                  imageVector = Icons.Filled.FilterList,
                  contentDescription = stringResource(R.string.library_filter_cd),
                )
              }
            }
            IconButton(
              onClick = onOpenSettings,
              modifier = Modifier.semantics { testTag = "library_settings_button" },
            ) {
              Icon(
                Icons.Filled.Settings,
                contentDescription = stringResource(R.string.nav_settings),
              )
            }
          },
        )
      }

      val scanning = scanState as? ScanState.Scanning
      if (scanning != null) {
        LibraryScanProgressBanner(state = scanning)
      }

      Box(modifier = Modifier.fillMaxSize()) {
        if (visibleDocs.isEmpty()) {
          LibraryEmptyState(tab = tab)
        } else {
          LibraryDocumentList(
            documents = visibleDocs,
            sortKey = sortKey,
            viewMode = viewMode,
            selectedIds = selectedIds,
            multiSelectActive = multiSelectActive,
            onTap = { doc ->
              if (multiSelectActive) {
                // Toggle selection
                selectedIds = if (doc.documentId in selectedIds) {
                  val updated = selectedIds - doc.documentId
                  if (updated.isEmpty()) {
                    multiSelectActive = false
                  }
                  updated
                } else {
                  selectedIds + doc.documentId
                }
              } else {
                onDocumentTap(doc)
              }
            },
            onLongPress = { doc ->
              if (!multiSelectActive) {
                multiSelectActive = true
                selectedIds = setOf(doc.documentId)
              } else {
                // Toggle selection on long-press too
                selectedIds = if (doc.documentId in selectedIds) {
                  val updated = selectedIds - doc.documentId
                  if (updated.isEmpty()) {
                    multiSelectActive = false
                  }
                  updated
                } else {
                  selectedIds + doc.documentId
                }
              }
            },
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
