package com.eight87.pageboy.data.library

import android.net.Uri

/**
 * Phase B.2 — one folder the user has opted into via the SAF picker,
 * paired with the [FolderType] they declared for it.
 *
 * The set of [LibraryRoot]s is the canonical input to the scanner. Stored
 * via [PersistedUriPermissionStore] (DataStore-backed) so the entries
 * survive process death and reboot.
 */
data class LibraryRoot(
  val treeUri: Uri,
  val folderType: FolderType,
  val displayName: String,
)
