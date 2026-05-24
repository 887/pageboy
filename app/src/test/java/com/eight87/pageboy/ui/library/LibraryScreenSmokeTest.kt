package com.eight87.pageboy.ui.library

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.eight87.pageboy.TestApplication
import com.eight87.pageboy.data.library.DocumentEntity
import com.eight87.pageboy.data.library.DocumentFormat
import com.eight87.pageboy.data.library.DocumentSource
import com.eight87.pageboy.data.library.LibraryRescanCoordinator
import com.eight87.pageboy.data.library.LibrarySortKey
import com.eight87.pageboy.data.library.LibraryTab
import com.eight87.pageboy.data.library.LibraryUiSettings
import com.eight87.pageboy.data.library.ScanState
import com.eight87.pageboy.data.library.ScanSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Phase B.16 — Compose smoke test. Renders [LibraryScreen] with a
 * single-document fake; asserts the four tab labels render and at least
 * one document card lands.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = TestApplication::class)
class LibraryScreenSmokeTest {

  @get:Rule
  val composeRule = createComposeRule()

  private fun fakeDocSource(docs: List<DocumentEntity>) = object : DocumentSource {
    override fun observeDocuments(): Flow<List<DocumentEntity>> = flowOf(docs)
    override fun observeRecents(limit: Int): Flow<List<DocumentEntity>> = flowOf(emptyList())
    override fun observeCollections(): Flow<List<String>> =
      flowOf(docs.mapNotNull { it.collection }.distinct())
    override suspend fun findById(id: String) = docs.firstOrNull { it.documentId == id }
    override suspend fun setPinned(id: String, pinned: Boolean) {}
    override suspend fun recordOpen(id: String) {}
    override suspend fun setReadProgress(id: String, positionMs: Long, fraction: Float) {}
    override suspend fun setScrollPosition(id: String, positionJson: String?, fraction: Float) {}
  }

  private fun fakeUiSettings(): LibraryUiSettings = object : LibraryUiSettings {
    override val sortKey = flowOf(LibrarySortKey.TitleAsc)
    override val tab = flowOf(LibraryTab.All)
    override val selectedFormats = flowOf<Set<DocumentFormat>>(emptySet())
    override val selectedCollections = flowOf<Set<String>>(emptySet())
    override val showHiddenFiles = flowOf(false)
    override suspend fun setSortKey(value: LibrarySortKey) {}
    override suspend fun setTab(value: LibraryTab) {}
    override suspend fun setSelectedFormats(value: Set<DocumentFormat>) {}
    override suspend fun setSelectedCollections(value: Set<String>) {}
    override suspend fun setShowHiddenFiles(value: Boolean) {}
  }

  private fun fakeCoordinator(): LibraryRescanCoordinator = object : LibraryRescanCoordinator {
    override val state = MutableStateFlow<ScanState>(ScanState.Idle).asStateFlow()
    override val scanSummaries = MutableSharedFlow<ScanSummary>().asSharedFlow()
    override fun requestRescan() {}
  }

  private fun sampleDoc() = DocumentEntity(
    documentId = "doc-1",
    treeUriString = "content://tree/root",
    relativePath = "guide.pdf",
    documentUriString = "content://doc/guide",
    title = "Sample guide",
    fileName = "guide.pdf",
    format = DocumentFormat.id(DocumentFormat.Pdf),
    sizeBytes = 1024L,
    mtimeMs = 0L,
    collection = "Manuals",
    addedAt = 0L,
  )

  @Test
  fun `LibraryScreen renders four tabs and a document card when catalog is non-empty`() {
    composeRule.setContent {
      LibraryScreen(
        documentSource = fakeDocSource(listOf(sampleDoc())),
        libraryUiSettings = fakeUiSettings(),
        libraryRescanCoordinator = fakeCoordinator(),
        onDocumentTap = {},
      )
    }
    composeRule.onNodeWithTag("library_screen").assertExists()
    composeRule.onNodeWithTag("library_tab_row").assertExists()
    composeRule.onNodeWithTag("library_tab_started").assertIsDisplayed()
    composeRule.onNodeWithTag("library_tab_all").assertIsDisplayed()
    composeRule.onNodeWithTag("library_tab_recents").assertIsDisplayed()
    composeRule.onNodeWithTag("library_tab_pinned").assertIsDisplayed()
    composeRule.onNodeWithTag("document_card_${sampleDoc().documentId.take(8)}").assertExists()
  }

  @Test
  fun `LibraryScreen renders empty-state card when catalog is empty`() {
    composeRule.setContent {
      LibraryScreen(
        documentSource = fakeDocSource(emptyList()),
        libraryUiSettings = fakeUiSettings(),
        libraryRescanCoordinator = fakeCoordinator(),
        onDocumentTap = {},
      )
    }
    // Default tab is All; empty state for that tab should render.
    composeRule.onNodeWithTag("library_empty_state_all").assertExists()
  }
}
