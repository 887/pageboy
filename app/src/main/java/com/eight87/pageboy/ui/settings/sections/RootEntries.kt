package com.eight87.pageboy.ui.settings.sections

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import com.eight87.pageboy.R
import com.eight87.pageboy.ui.settings.Groups
import com.eight87.pageboy.ui.settings.RowKind
import com.eight87.pageboy.ui.settings.Section
import com.eight87.pageboy.ui.settings.SettingsCatalog
import com.eight87.pageboy.ui.settings.SettingsCatalogEntry

/**
 * Entries that render on the Settings root page. Phase A.4 ships only
 * the About row as a placeholder so the navigation route lands somewhere
 * real. Real per-feature sections (Appearance / Library / Reader /
 * Annotations / Signing — see `docs/plans/ui-shell.md`) land alongside
 * the surfaces they configure in later phases.
 */
internal val RootEntries: List<SettingsCatalogEntry> = listOf(
  SettingsCatalogEntry(
    id = SettingsCatalog.ID_ABOUT,
    label = "About pageboy",
    subtitle = "Version, source code, open-source licenses",
    labelRes = R.string.settings_about_label,
    subtitleRes = R.string.settings_about_subtitle,
    keywords = listOf("about", "version", "licenses", "source"),
    icon = Icons.Filled.Info,
    section = Section.Root,
    group = Groups.About,
    kind = RowKind.Navigate,
  ),
)
