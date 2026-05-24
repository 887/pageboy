package com.eight87.pageboy.ui.settings.folders

import android.net.Uri
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.eight87.pageboy.TestApplication
import com.eight87.pageboy.data.library.FolderType
import com.eight87.pageboy.data.library.LibraryRoot
import com.eight87.pageboy.data.library.PersistedUriPermissionStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Phase B.16 — Compose smoke test for the folders management screen.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = TestApplication::class)
class LibraryFoldersScreenSmokeTest {

  @get:Rule
  val composeRule = createComposeRule()

  private fun fakeStore(roots: List<LibraryRoot>): PersistedUriPermissionStore =
    object : PersistedUriPermissionStore {
      override fun observeRoots(): Flow<List<LibraryRoot>> = flowOf(roots)
      override suspend fun addRoot(treeUri: Uri, folderType: FolderType) {}
      override suspend fun removeRoot(treeUri: Uri) {}
    }

  @Test
  fun `empty roots renders the empty state card and Add folder button`() {
    composeRule.setContent {
      LibraryFoldersScreen(
        persistedUriPermissionStore = fakeStore(emptyList()),
        onBack = {},
      )
    }
    composeRule.onNodeWithTag("library_folders_screen").assertExists()
    composeRule.onNodeWithTag("library_folders_empty").assertExists()
    composeRule.onNodeWithTag("library_folders_add_button").assertIsDisplayed()
  }

  @Test
  fun `non-empty roots renders each entry`() {
    val roots = listOf(
      LibraryRoot(
        treeUri = Uri.parse("content://com.example.provider/tree/123"),
        folderType = FolderType.Root,
        displayName = "Calibre Library",
      ),
    )
    composeRule.setContent {
      LibraryFoldersScreen(
        persistedUriPermissionStore = fakeStore(roots),
        onBack = {},
      )
    }
    composeRule.onNodeWithTag("library_folders_add_button").assertIsDisplayed()
  }
}
