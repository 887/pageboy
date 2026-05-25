package com.eight87.pageboy.data.openwith

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import com.eight87.pageboy.data.settings.Setting
import com.eight87.pageboy.data.settings.setting

/**
 * Phase N.12 — narrow settings facet for the "Open with" subsystem.
 * Same `Setting<T>`-shaped facet pattern Phase C introduced; not a god
 * `SettingsRepository`.
 *
 * Three knobs in v1:
 *  - [ephemeralRetentionDays] — how long ad-hoc rows hang around before
 *    `OpenWithEphemeralCleanupWorker` deletes them.
 *  - [saveAdHocToLibraryDefault] — when true, the "Keep this document"
 *    confirmation is skipped and ad-hoc opens immediately try to
 *    upgrade their grant + offer the save-to-library-root fallback.
 *  - [autoClassifyUnknownMime] — when true, MIME types pageboy doesn't
 *    explicitly handle fall through to the magic-byte classifier (Phase
 *    B's [com.eight87.pageboy.data.library.DocumentClassifier]). When
 *    false, intent resolution short-circuits to `UnknownFormat` on any
 *    non-matched MIME.
 */
interface OpenWithSettings {
  val ephemeralRetentionDays: Setting<Int>
  val saveAdHocToLibraryDefault: Setting<Boolean>
  val autoClassifyUnknownMime: Setting<Boolean>

  companion object {
    /** Phase N.12 — slider default; locked in the plan. */
    const val DEFAULT_RETENTION_DAYS: Int = 7

    /** Inclusive lower bound on the slider; below 1 day risks losing
     *  the doc before the user notices it. */
    const val MIN_RETENTION_DAYS: Int = 1

    /** Inclusive upper bound on the slider; over 30 days defeats the
     *  point of an ephemeral grant. */
    const val MAX_RETENTION_DAYS: Int = 30
  }
}

/**
 * DataStore-backed [OpenWithSettings]. Keys live under the
 * `open_with_settings` Preferences instance the AppGraph wires.
 */
class AndroidOpenWithSettings(
  dataStore: DataStore<Preferences>,
) : OpenWithSettings {

  override val ephemeralRetentionDays: Setting<Int> =
    dataStore.setting(KEY_RETENTION_DAYS, default = OpenWithSettings.DEFAULT_RETENTION_DAYS)

  override val saveAdHocToLibraryDefault: Setting<Boolean> =
    dataStore.setting(KEY_SAVE_DEFAULT, default = false)

  override val autoClassifyUnknownMime: Setting<Boolean> =
    dataStore.setting(KEY_AUTO_CLASSIFY, default = true)

  private companion object {
    val KEY_RETENTION_DAYS = intPreferencesKey("open_with_retention_days")
    val KEY_SAVE_DEFAULT = booleanPreferencesKey("open_with_save_default")
    val KEY_AUTO_CLASSIFY = booleanPreferencesKey("open_with_auto_classify")
  }
}
