package com.eight87.pageboy.data.library

import android.net.Uri
import kotlinx.coroutines.flow.Flow

/**
 * Phase B.2 — narrow data interface (per the family's SOLID-I pattern)
 * for the set of document-library tree URIs the user has opted into.
 *
 * The store is responsible for:
 *  - Holding `FLAG_GRANT_READ_URI_PERMISSION` persistably across app restarts
 *    via `ContentResolver.takePersistableUriPermission` / `releasePersistableUriPermission`.
 *  - Persisting the user's [FolderType] choice + display label per picked tree.
 *  - Exposing the current set as a [Flow] so the UI / coordinator can
 *    react to add / remove without polling.
 *
 * Composables depend on this interface, not on the concrete Android
 * implementation. The Android-backed implementation lives in
 * [AndroidPersistedUriPermissionStore].
 */
interface PersistedUriPermissionStore {

  fun observeRoots(): Flow<List<LibraryRoot>>

  suspend fun addRoot(treeUri: Uri, folderType: FolderType)

  suspend fun removeRoot(treeUri: Uri)
}
