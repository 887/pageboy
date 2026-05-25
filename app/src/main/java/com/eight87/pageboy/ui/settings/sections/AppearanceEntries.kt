package com.eight87.pageboy.ui.settings.sections

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Palette
import com.eight87.pageboy.R
import com.eight87.pageboy.ui.settings.GroupRef
import com.eight87.pageboy.ui.settings.RowKind
import com.eight87.pageboy.ui.settings.Section
import com.eight87.pageboy.ui.settings.SettingsCatalogEntry

/**
 * Appearance section entries in the settings catalog. Mirror of
 * tonearmboy's LookAndFeelEntries; adapted for pageboy's four-knob
 * ThemeSettings facet (mode / base theme / dynamic color / seed color).
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
    id = AppearanceCatalogIds.ID_BASE_THEME,
    label = "Base theme",
    subtitle = "Foundation colors for the app palette.",
    labelRes = R.string.settings_appearance_base_theme_label,
    subtitleRes = R.string.settings_appearance_base_theme_subtitle,
    keywords = listOf(
      "dynamic", "material you", "brand", "palette",
      "amoled", "oled", "pure black", "static", "custom",
    ),
    icon = Icons.Filled.Contrast,
    section = Section.Appearance,
    group = AppearanceGroups.Theme,
    kind = RowKind.Picker,
  ),
  SettingsCatalogEntry(
    id = AppearanceCatalogIds.ID_SEED_COLOR,
    label = "Seed color",
    subtitle = "Pick a base color for the app palette.",
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
  const val ID_BASE_THEME = "appearance_base_theme"
  const val ID_SEED_COLOR = "appearance_seed_color"
}
