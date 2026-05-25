package com.eight87.pageboy.data.settings

import android.os.Build
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey

/**
 * Close-out — M3E color theming settings facet. Per R.B pattern:
 * narrow interface, `Setting<T>` handles, no god repository.
 *
 * Three knobs:
 *  - [themeMode] — Light / Dark / System (enum, persisted in DataStore).
 *  - [dynamicColor] — on/off. Android 12+ wallpaper-derived colors.
 *    Default on for API 31+, off below.
 *  - [seedColor] — a user-picked 24-bit RGB seed (0xRRGGBB) that
 *    generates a full M3 color scheme when dynamic color is off.
 *    0 = "use brand defaults" (no user pick).
 */
interface ThemeSettings {
  val themeMode: EnumSetting<ThemeMode>
  val dynamicColor: Setting<Boolean>
  val seedColor: Setting<Long>
}

/**
 * Three-state theme mode. Mirrors tonearmboy's BaseTheme simplified
 * for pageboy (no PureBlack in v1 — e-reader dark mode is already
 * near-black via the M3E surface ladder).
 */
enum class ThemeMode {
  Light,
  Dark,
  System,
}

/**
 * DataStore-backed [ThemeSettings]. Keys live under the
 * `theme_settings` Preferences instance the AppGraph wires.
 */
class AndroidThemeSettings(
  dataStore: DataStore<Preferences>,
) : ThemeSettings {

  override val themeMode: EnumSetting<ThemeMode> =
    dataStore.enumSetting("theme_mode", default = ThemeMode.System)

  override val dynamicColor: Setting<Boolean> =
    dataStore.setting(
      KEY_DYNAMIC_COLOR,
      default = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
    )

  override val seedColor: Setting<Long> =
    dataStore.setting(KEY_SEED_COLOR, default = 0L)

  private companion object {
    val KEY_DYNAMIC_COLOR = booleanPreferencesKey("theme_dynamic_color")
    val KEY_SEED_COLOR = longPreferencesKey("theme_seed_color")
  }
}
