package com.eight87.pageboy.ui.settings.sections

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import com.eight87.pageboy.R
import com.eight87.pageboy.ui.settings.GroupRef
import com.eight87.pageboy.ui.settings.RowKind
import com.eight87.pageboy.ui.settings.Section
import com.eight87.pageboy.ui.settings.SettingsCatalogEntry

/**
 * Phase B.15 — entries on the Settings → Library section.
 *
 * Two surfaces today: navigation into the source-folders management
 * screen (B.12), and a one-shot "Re-scan now" action that fires the
 * coordinator's `requestRescan()`. The "show hidden files" toggle is
 * promised in the plan but not yet wired through the UI — landing it
 * later is a one-row addition.
 */
internal object LibraryGroups {
  val SourceFolders = GroupRef(R.string.settings_group_library)
}

internal val LibraryEntries: List<SettingsCatalogEntry> = listOf(
  SettingsCatalogEntry(
    id = LibraryCatalogIds.ID_LIBRARY_FOLDERS,
    label = "Source folders",
    subtitle = "Pick the folders pageboy should index.",
    labelRes = R.string.settings_library_folders_label,
    subtitleRes = R.string.settings_library_folders_subtitle,
    keywords = listOf("folder", "library", "saf", "source", "tree"),
    icon = Icons.Filled.Folder,
    section = Section.Library,
    group = LibraryGroups.SourceFolders,
    kind = RowKind.Navigate,
  ),
  SettingsCatalogEntry(
    id = LibraryCatalogIds.ID_RESCAN_NOW,
    label = "Re-scan now",
    subtitle = "Re-walk every source folder.",
    labelRes = R.string.settings_library_rescan_label,
    subtitleRes = R.string.settings_library_rescan_subtitle,
    keywords = listOf("rescan", "scan", "refresh"),
    icon = Icons.Filled.Refresh,
    section = Section.Library,
    group = LibraryGroups.SourceFolders,
    kind = RowKind.Action,
  ),
)

internal object LibraryCatalogIds {
  const val ID_LIBRARY_FOLDERS = "library_folders"
  const val ID_RESCAN_NOW = "library_rescan_now"
}
