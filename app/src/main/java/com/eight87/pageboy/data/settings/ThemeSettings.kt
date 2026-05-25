package com.eight87.pageboy.data.settings

import android.os.Build
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey

/**
 * M3E color theming settings facet. Per R.B pattern: narrow interface,
 * `Setting<T>` handles, no god repository.
 *
 * Four knobs:
 *  - [themeMode] — Light / Dark / System (enum, persisted in DataStore).
 *  - [baseTheme] — which foundation color scheme to use. See
 *    [BaseThemeChoice] for the four variants.
 *  - [dynamicColor] — legacy toggle, retained for backward compat.
 *    Selecting [BaseThemeChoice.DefaultAndroid] implies dynamic color;
 *    selecting any other base theme overrides it.
 *  - [seedColor] — a user-picked 24-bit RGB seed (0xRRGGBB) that
 *    generates a full M3 color scheme when [baseTheme] is [Custom].
 *    0 = "use brand defaults" (no user pick).
 */
interface ThemeSettings {
  val themeMode: EnumSetting<ThemeMode>
  val baseTheme: EnumSetting<BaseThemeChoice>
  val dynamicColor: Setting<Boolean>
  val seedColor: Setting<Long>
}

/** Three-state light/dark mode selector. */
enum class ThemeMode {
  Light,
  Dark,
  System,
}

/**
 * Foundation color scheme selector. Mirrors tonearmboy's `BaseTheme`
 * sealed class as a flat enum for DataStore serialization.
 *
 *  - [DefaultAndroid] — dynamic color on API 31+, brand palette below.
 *  - [DefaultColors] — static brand palette regardless of API level.
 *  - [PureBlack] — AMOLED: surface + background = Color.Black.
 *  - [Custom] — user-picked seed color drives the palette via HSL derivation.
 */
enum class BaseThemeChoice {
  DefaultAndroid,
  DefaultColors,
  PureBlack,
  Custom,
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

  override val baseTheme: EnumSetting<BaseThemeChoice> =
    dataStore.enumSetting("theme_base_theme", default = BaseThemeChoice.DefaultAndroid)

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
