package com.eight87.pageboy.data.library

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Phase B.10 — DataStore-backed [LibraryUiSettings]. Keys live under the
 * `library_ui` Preferences instance the AppGraph wires.
 */
class AndroidLibraryUiSettings(
  private val dataStore: DataStore<Preferences>,
) : LibraryUiSettings {

  override val sortKey: Flow<LibrarySortKey> =
    dataStore.data.map { prefs ->
      val raw = prefs[KEY_SORT] ?: LibrarySortKey.TitleAsc.name
      LibrarySortKey.fromId(raw)
    }

  override val tab: Flow<LibraryTab> =
    dataStore.data.map { prefs ->
      val raw = prefs[KEY_TAB] ?: LibraryTab.All.name
      LibraryTab.fromId(raw)
    }

  override val viewMode: Flow<ViewMode> =
    dataStore.data.map { prefs ->
      val raw = prefs[KEY_VIEW_MODE] ?: ViewMode.List.name
      ViewMode.fromId(raw)
    }

  override val selectedFormats: Flow<Set<DocumentFormat>> =
    dataStore.data.map { prefs ->
      val raw = prefs[KEY_FORMATS] ?: emptySet()
      raw.map { DocumentFormat.fromId(it) }.toSet()
    }

  override val selectedCollections: Flow<Set<String>> =
    dataStore.data.map { prefs -> prefs[KEY_COLLECTIONS] ?: emptySet() }

  override val showHiddenFiles: Flow<Boolean> =
    dataStore.data.map { prefs -> prefs[KEY_SHOW_HIDDEN] ?: false }

  override suspend fun setSortKey(value: LibrarySortKey) {
    dataStore.edit { it[KEY_SORT] = value.name }
  }

  override suspend fun setTab(value: LibraryTab) {
    dataStore.edit { it[KEY_TAB] = value.name }
  }

  override suspend fun setViewMode(value: ViewMode) {
    dataStore.edit { it[KEY_VIEW_MODE] = value.name }
  }

  override suspend fun setSelectedFormats(value: Set<DocumentFormat>) {
    dataStore.edit { it[KEY_FORMATS] = value.map { f -> DocumentFormat.id(f) }.toSet() }
  }

  override suspend fun setSelectedCollections(value: Set<String>) {
    dataStore.edit { it[KEY_COLLECTIONS] = value }
  }

  override suspend fun setShowHiddenFiles(value: Boolean) {
    dataStore.edit { it[KEY_SHOW_HIDDEN] = value }
  }

  private companion object {
    val KEY_SORT = stringPreferencesKey("library_sort_key")
    val KEY_TAB = stringPreferencesKey("library_tab")
    val KEY_VIEW_MODE = stringPreferencesKey("library_view_mode")
    val KEY_FORMATS = stringSetPreferencesKey("library_selected_formats")
    val KEY_COLLECTIONS = stringSetPreferencesKey("library_selected_collections")
    val KEY_SHOW_HIDDEN = booleanPreferencesKey("library_show_hidden_files")
  }
}
