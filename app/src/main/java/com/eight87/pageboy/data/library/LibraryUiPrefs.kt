package com.eight87.pageboy.data.library

import kotlinx.coroutines.flow.Flow

/**
 * Phase B.10 — sort orders the library can render. Persisted in DataStore
 * via [LibraryUiSettings] as the enum's name.
 */
enum class LibrarySortKey {
  TitleAsc,
  TitleDesc,
  DateAdded,
  LastOpened,
  Format,
  ;

  companion object {
    fun fromId(id: String): LibrarySortKey =
      entries.firstOrNull { it.name == id } ?: TitleAsc
  }
}

/**
 * View mode for the library document list. Persisted per-session via
 * [LibraryUiSettings].
 */
enum class ViewMode {
  List,
  Tile,
  TwoColumn,
  ;

  /** Cycle to the next mode: List → Tile → TwoColumn → List. */
  fun next(): ViewMode = when (this) {
    List -> Tile
    Tile -> TwoColumn
    TwoColumn -> List
  }

  companion object {
    fun fromId(id: String): ViewMode =
      entries.firstOrNull { it.name == id } ?: List
  }
}

/** Tab the user is currently on in the library screen. Persisted across sessions. */
enum class LibraryTab {
  Started,
  All,
  Recents,
  Pinned,
  ;

  companion object {
    fun fromId(id: String): LibraryTab =
      entries.firstOrNull { it.name == id } ?: All
  }
}

/**
 * Phase B.10 — narrow data interface for the library-UI preferences
 * surface. Backed by DataStore Preferences in the Android impl.
 */
interface LibraryUiSettings {

  val sortKey: Flow<LibrarySortKey>
  val tab: Flow<LibraryTab>
  val viewMode: Flow<ViewMode>
  val selectedFormats: Flow<Set<DocumentFormat>>
  val selectedCollections: Flow<Set<String>>
  val showHiddenFiles: Flow<Boolean>

  suspend fun setSortKey(value: LibrarySortKey)
  suspend fun setTab(value: LibraryTab)
  suspend fun setViewMode(value: ViewMode)
  suspend fun setSelectedFormats(value: Set<DocumentFormat>)
  suspend fun setSelectedCollections(value: Set<String>)
  suspend fun setShowHiddenFiles(value: Boolean)
}
