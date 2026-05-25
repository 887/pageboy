package com.eight87.pageboy.ui.settings.sections

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Palette
import com.eight87.pageboy.R
import com.eight87.pageboy.ui.settings.GroupRef
import com.eight87.pageboy.ui.settings.RowKind
import com.eight87.pageboy.ui.settings.Section
import com.eight87.pageboy.ui.settings.SettingsCatalogEntry

/**
 * Close-out — Appearance section entries in the settings catalog.
 * Mirror of tonearmboy's LookAndFeelEntries; adapted for pageboy's
 * three-knob ThemeSettings facet (mode / dynamic / seed color).
 */
internal object AppearanceGroups {
  val Theme = GroupRef(R.string.settings_group_appearance)
}

internal val AppearanceEntries: List<SettingsCatalogEntry> = listOf(
  SettingsCatalogEntry(
    id = AppearanceCatalogIds.ID_THEME_MODE,
    label = "Theme",
    subtitle = "Light, Dark, or follow the system.",
    labelRes = R.string.settings_appearance_theme_mode_label,
    subtitleRes = R.string.settings_appearance_theme_mode_subtitle,
    keywords = listOf("dark", "light", "system", "theme", "mode"),
    icon = Icons.Filled.DarkMode,
    section = Section.Appearance,
    group = AppearanceGroups.Theme,
    kind = RowKind.Picker,
  ),
  SettingsCatalogEntry(
    id = AppearanceCatalogIds.ID_DYNAMIC_COLOR,
    label = "Dynamic color",
    subtitle = "Use wallpaper-derived colors (Android 12+).",
    labelRes = R.string.settings_appearance_dynamic_color_label,
    subtitleRes = R.string.settings_appearance_dynamic_color_subtitle,
    keywords = listOf("dynamic", "material you", "wallpaper", "color"),
    icon = Icons.Filled.Palette,
    section = Section.Appearance,
    group = AppearanceGroups.Theme,
    kind = RowKind.Toggle,
  ),
  SettingsCatalogEntry(
    id = AppearanceCatalogIds.ID_SEED_COLOR,
    label = "Seed color",
    subtitle = "Pick a base color for the app palette (when dynamic color is off).",
    labelRes = R.string.settings_appearance_seed_color_label,
    subtitleRes = R.string.settings_appearance_seed_color_subtitle,
    keywords = listOf("seed", "color", "picker", "palette", "tint"),
    icon = Icons.Filled.ColorLens,
    section = Section.Appearance,
    group = AppearanceGroups.Theme,
    kind = RowKind.Picker,
  ),
)

internal object AppearanceCatalogIds {
  const val ID_THEME_MODE = "appearance_theme_mode"
  const val ID_DYNAMIC_COLOR = "appearance_dynamic_color"
  const val ID_SEED_COLOR = "appearance_seed_color"
}
