package com.eight87.pageboy.ui.settings.sections

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import com.eight87.pageboy.R
import com.eight87.pageboy.ui.settings.GroupRef
import com.eight87.pageboy.ui.settings.RowKind
import com.eight87.pageboy.ui.settings.Section
import com.eight87.pageboy.ui.settings.SettingsCatalogEntry

/**
 * Phase C.8 — Reader section in the settings catalog. Mirrors the Library
 * section shape: one [GroupRef] per related cluster of rows; entries
 * surface through [com.eight87.pageboy.ui.settings.SettingsCatalog].
 *
 * Phase C ships one placeholder entry (the continuous-scrolling toggle)
 * so the section has a row to render + the `Setting<T>` plumbing is
 * exercised end-to-end. Per-format reader settings (font size, theme,
 * scroll mode) land in Phase D when the Markdown renderer needs them.
 */
internal object ReaderGroups {
  val Display = GroupRef(R.string.settings_group_reader_display)
}

internal val ReaderEntries: List<SettingsCatalogEntry> = listOf(
  SettingsCatalogEntry(
    id = ReaderCatalogIds.ID_CONTINUOUS_SCROLL,
    label = "Continuous scrolling",
    subtitle = "Treat the document as one long scroll instead of paginated.",
    labelRes = R.string.settings_reader_continuous_label,
    subtitleRes = R.string.settings_reader_continuous_subtitle,
    keywords = listOf("scroll", "page", "continuous", "reader"),
    icon = Icons.AutoMirrored.Filled.MenuBook,
    section = Section.Reader,
    group = ReaderGroups.Display,
    kind = RowKind.Toggle,
  ),
)

internal object ReaderCatalogIds {
  const val ID_CONTINUOUS_SCROLL = "reader_continuous_scrolling"
}
