package com.eight87.pageboy.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey

/**
 * Phase C.8 — reader-side settings facet. Narrow interface that the reader
 * chrome + per-format renderers (Phase D+) take, not a god
 * `SettingsRepository`.
 *
 * Phase C ships one placeholder knob ([continuousScrolling]) so the
 * Reader section in the settings catalog has at least one rendering row
 * and so the [Setting] plumbing is exercised end-to-end. Real font-size
 * / theme-mode / scroll-mode knobs land in Phase D when the Markdown
 * renderer needs them (the first renderer that actually has a font
 * surface).
 */
interface ReaderSettings {

  /**
   * When `true` (default) the reader treats the document as one long
   * scrolling surface. When `false` the renderer paginates (per-format
   * semantics — paginated PDF, paged Markdown, etc.). The toggle is a
   * placeholder until the first per-format renderer earns the
   * distinction; no UI surface reads it yet.
   */
  val continuousScrolling: Setting<Boolean>
}

/**
 * DataStore-backed [ReaderSettings]. Keys live under the `reader_settings`
 * Preferences instance the AppGraph wires.
 */
class AndroidReaderSettings(
  private val dataStore: DataStore<Preferences>,
) : ReaderSettings {

  override val continuousScrolling: Setting<Boolean> =
    dataStore.setting(KEY_CONTINUOUS_SCROLL, default = true)

  private companion object {
    val KEY_CONTINUOUS_SCROLL = booleanPreferencesKey("reader_continuous_scrolling")
  }
}
