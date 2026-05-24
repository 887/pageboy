package com.eight87.pageboy.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eight87.pageboy.R
import com.eight87.pageboy.data.library.DocumentEntity
import com.eight87.pageboy.data.library.DocumentFormat
import com.eight87.pageboy.data.library.DocumentSource
import com.eight87.pageboy.data.library.LibraryFilters
import com.eight87.pageboy.data.library.LibraryRescanCoordinator
import com.eight87.pageboy.data.library.LibrarySortKey
import com.eight87.pageboy.data.library.LibraryTab
import com.eight87.pageboy.data.library.LibraryUiSettings
import com.eight87.pageboy.data.library.ScanState
import kotlinx.coroutines.launch

/**
 * Phase B.9 — the library screen.
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
  onDocumentTap: (DocumentEntity) -> Unit,
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
  val visibleDocs = remember(
    docs, recents, tab, selectedFormats, selectedCollections, searchQuery, sortKey,
  ) {
    val tabbed = when (tab) {
      LibraryTab.Recents -> recents
      LibraryTab.Started -> docs.filter { it.lastReadPositionMs > 0L }
      LibraryTab.All -> docs
      LibraryTab.Pinned -> docs.filter { it.pinned }
    }
    if (tab == LibraryTab.Recents) {
      // Recents is already ordered by `lastOpenedAt DESC` in the DAO.
      // Run only format + collection + search; preserve the order.
      val a = LibraryFilters.byFormat(tabbed, selectedFormats)
      val b = LibraryFilters.byCollection(a, selectedCollections)
      LibraryFilters.bySearch(b, searchQuery)
    } else {
      LibraryFilters.apply(
        docs = tabbed,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibrarySearchBar(
  query: String,
  onQueryChange: (String) -> Unit,
  onClose: () -> Unit,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .background(MaterialTheme.colorScheme.surface)
      .padding(horizontal = 4.dp, vertical = 8.dp)
      .semantics { testTag = "library_search_bar" },
    verticalAlignment = Alignment.CenterVertically,
  ) {
    IconButton(onClick = onClose) {
      Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.library_search_close_cd))
    }
    TextField(
      value = query,
      onValueChange = onQueryChange,
      placeholder = { Text(stringResource(R.string.library_search_hint)) },
      singleLine = true,
      colors = TextFieldDefaults.colors(
        focusedContainerColor = MaterialTheme.colorScheme.surface,
        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
      ),
      trailingIcon = {
        if (query.isNotEmpty()) {
          IconButton(onClick = { onQueryChange("") }) {
            Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.library_search_clear_cd))
          }
        }
      },
      modifier = Modifier.fillMaxWidth(),
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryFilterChipRow(
  selectedFormats: Set<DocumentFormat>,
  selectedCollections: Set<String>,
  collections: List<String>,
  onToggleFormat: (DocumentFormat) -> Unit,
  onToggleCollection: (String) -> Unit,
  onClear: () -> Unit,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .horizontalScroll(rememberScrollState())
      .padding(horizontal = 8.dp, vertical = 4.dp)
      .semantics { testTag = "library_filter_chip_row" },
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    // Format chips for the eight known formats + Unknown.
    DocumentFormat.entries.forEach { format ->
      val selected = format in selectedFormats
      FilterChip(
        selected = selected,
        onClick = { onToggleFormat(format) },
        label = { Text(formatLabel(format)) },
        leadingIcon = { Icon(formatIcon(format), contentDescription = null, modifier = Modifier.size(18.dp)) },
        modifier = Modifier.semantics { testTag = "library_filter_format_${format.name}" },
      )
    }
    collections.forEach { collection ->
      val selected = collection in selectedCollections
      FilterChip(
        selected = selected,
        onClick = { onToggleCollection(collection) },
        label = { Text(collection) },
      )
    }
    if (selectedFormats.isNotEmpty() || selectedCollections.isNotEmpty()) {
      AssistChip(
        onClick = onClear,
        label = { Text(stringResource(R.string.library_filters_clear)) },
        colors = AssistChipDefaults.assistChipColors(
          containerColor = MaterialTheme.colorScheme.errorContainer,
          labelColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
      )
    }
  }
}

@Composable
internal fun LibraryScanProgressBanner(state: ScanState.Scanning) {
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 16.dp, vertical = 8.dp)
      .semantics { testTag = "library_scan_banner" },
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ),
  ) {
    Column(modifier = Modifier.padding(12.dp)) {
      Text(
        text = stringResource(R.string.library_scan_banner_searching, state.documentsFound),
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Medium,
      )
      val folder = state.currentFolder
      if (!folder.isNullOrBlank()) {
        Spacer(Modifier.height(2.dp))
        Text(
          text = stringResource(R.string.library_scan_banner_in, folder),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      Spacer(Modifier.height(8.dp))
      LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    }
  }
}

@Composable
private fun LibraryEmptyState(tab: LibraryTab) {
  val message = when (tab) {
    LibraryTab.Started -> R.string.library_empty_started
    LibraryTab.All -> R.string.library_empty_all
    LibraryTab.Recents -> R.string.library_empty_recents
    LibraryTab.Pinned -> R.string.library_empty_pinned
  }
  Box(
    modifier = Modifier
      .fillMaxSize()
      .padding(24.dp)
      .semantics { testTag = "library_empty_state_${tab.name.lowercase()}" },
    contentAlignment = Alignment.Center,
  ) {
    Card(
      colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
      ),
      elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
      Text(
        text = stringResource(message),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(24.dp),
      )
    }
  }
}

@Composable
private fun LibraryDocumentList(
  documents: List<DocumentEntity>,
  onTap: (DocumentEntity) -> Unit,
  onTogglePin: (DocumentEntity) -> Unit,
) {
  LazyColumn(
    contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 16.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
    modifier = Modifier.fillMaxSize().semantics { testTag = "library_document_list" },
  ) {
    items(documents, key = { it.documentId }) { doc ->
      DocumentCard(
        document = doc,
        onTap = { onTap(doc) },
        onTogglePin = { onTogglePin(doc) },
      )
    }
  }
}

@Composable
private fun DocumentCard(
  document: DocumentEntity,
  onTap: () -> Unit,
  onTogglePin: () -> Unit,
) {
  val format = DocumentFormat.fromId(document.format)
  var menuOpen by remember { mutableStateOf(false) }
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onTap)
      .semantics { testTag = "document_card_${document.documentId.take(8)}" },
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ),
    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
  ) {
    Column(modifier = Modifier.padding(12.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
          modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.primaryContainer),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = formatIcon(format),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
          )
        }
        Spacer(Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = document.title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
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
      // Read progress indicator — visible whenever the user has started
      // the document. The line stays out of the way for unstarted docs
      // so the All tab feels light.
      if (document.readFraction > 0f) {
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
          progress = { document.readFraction.coerceIn(0f, 1f) },
          modifier = Modifier.fillMaxWidth().height(4.dp),
        )
      }
    }
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

internal fun formatLabel(format: DocumentFormat): String = when (format) {
  DocumentFormat.Markdown -> "Markdown"
  DocumentFormat.Txt -> "Text"
  DocumentFormat.Pdf -> "PDF"
  DocumentFormat.Epub -> "EPUB"
  DocumentFormat.Docx -> "DOCX"
  DocumentFormat.Xlsx -> "XLSX"
  DocumentFormat.Odt -> "ODT"
  DocumentFormat.Ods -> "ODS"
  DocumentFormat.Unknown -> "Unknown"
}

internal fun formatIcon(format: DocumentFormat): ImageVector = when (format) {
  DocumentFormat.Pdf -> Icons.Filled.PictureAsPdf
  DocumentFormat.Epub -> Icons.Filled.Book
  DocumentFormat.Markdown, DocumentFormat.Txt -> Icons.AutoMirrored.Filled.Article
  DocumentFormat.Docx, DocumentFormat.Odt -> Icons.Filled.Description
  DocumentFormat.Xlsx, DocumentFormat.Ods -> Icons.Filled.GridOn
  DocumentFormat.Unknown -> Icons.Filled.Description
}
