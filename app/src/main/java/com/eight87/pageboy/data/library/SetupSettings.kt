package com.eight87.pageboy.data.library

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import com.eight87.pageboy.data.settings.EnumSetting
import com.eight87.pageboy.data.settings.Setting
import com.eight87.pageboy.data.settings.enumSetting
import com.eight87.pageboy.data.settings.setting

/**
 * Setup wizard settings facet. Tracks whether the user has completed the
 * first-launch wizard and which document source mode they chose.
 *
 * Retroactive support: existing installs where [setupComplete] was never
 * written will read the default `false`, causing the wizard to show on the
 * next launch.
 */
interface SetupSettings {

  /** `true` once the user has completed the setup wizard. */
  val setupComplete: Setting<Boolean>

  /**
   * Which document scanning mode the user chose in the wizard.
   * [DocumentSourceMode.FolderPicker] is the default (SAF-only, existing
   * behaviour). [DocumentSourceMode.AllFiles] uses
   * `MANAGE_EXTERNAL_STORAGE` and walks common filesystem paths directly.
   */
  val documentSourceMode: EnumSetting<DocumentSourceMode>
}

/**
 * How pageboy discovers documents on the device.
 *
 * - [FolderPicker] — user picks SAF tree roots (default, existing behaviour).
 * - [AllFiles] — `MANAGE_EXTERNAL_STORAGE` granted; the scanner walks
 *   `/storage/emulated/0/Documents/`, `Download/`, `Books/` directly.
 */
enum class DocumentSourceMode {
  FolderPicker,
  AllFiles,
}

/**
 * DataStore-backed [SetupSettings]. Keys live under the `setup_settings`
 * Preferences instance the AppGraph wires.
 */
class AndroidSetupSettings(
  dataStore: DataStore<Preferences>,
) : SetupSettings {

  override val setupComplete: Setting<Boolean> =
    dataStore.setting(KEY_SETUP_COMPLETE, default = false)

  override val documentSourceMode: EnumSetting<DocumentSourceMode> =
    dataStore.enumSetting("document_source_mode", default = DocumentSourceMode.FolderPicker)

  private companion object {
    val KEY_SETUP_COMPLETE = booleanPreferencesKey("setup_complete")
  }
}
