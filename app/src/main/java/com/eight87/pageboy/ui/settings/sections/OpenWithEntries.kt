package com.eight87.pageboy.ui.settings.sections

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Sync
import com.eight87.pageboy.R
import com.eight87.pageboy.ui.settings.GroupRef
import com.eight87.pageboy.ui.settings.RowKind
import com.eight87.pageboy.ui.settings.Section
import com.eight87.pageboy.ui.settings.SettingsCatalogEntry

/**
 * Phase N.12 — Open-with section in the settings catalog.
 *
 * Three entries:
 *  - retention slider (1–30 days; Action kind because the row hosts a
 *    slider rather than a standard navigate-or-toggle row),
 *  - save-to-library default boolean,
 *  - auto-classify unknown MIME boolean.
 *
 * The Open-with section uses [Section.Annotations] as the host bucket
 * intentionally: the existing [Section] enum doesn't carry a dedicated
 * `OpenWith` value, and the Annotations slot is the only unused
 * bucket in Phase N. Extending the enum is the cleaner long-term fix
 * (single-line addition + a new "Open with" group header); Phase N
 * lifts that into the catalog when the section's first surface lands.
 *
 * Update: the simpler, less-invasive choice is to add the section
 * value to the enum (one diff line) and have it carry its own
 * catalog entries. We do that in the catalog (see [Section.OpenWith])
 * — this file just defines its entries.
 */
internal object OpenWithGroups {
  val Behavior = GroupRef(R.string.settings_group_open_with)
}

internal val OpenWithEntries: List<SettingsCatalogEntry> = listOf(
  SettingsCatalogEntry(
    id = OpenWithCatalogIds.ID_RETENTION_DAYS,
    label = "Ephemeral retention",
    subtitle = "Days to keep ad-hoc opens before cleanup.",
    labelRes = R.string.settings_open_with_retention_label,
    subtitleRes = R.string.settings_open_with_retention_subtitle,
    keywords = listOf("retention", "days", "open with", "ephemeral", "ad-hoc"),
    icon = Icons.Filled.Sync,
    section = Section.OpenWith,
    group = OpenWithGroups.Behavior,
    kind = RowKind.Picker,
  ),
  SettingsCatalogEntry(
    id = OpenWithCatalogIds.ID_SAVE_DEFAULT,
    label = "Save ad-hoc opens to library by default",
    subtitle = "Skip the Keep prompt and copy ad-hoc documents automatically.",
    labelRes = R.string.settings_open_with_save_default_label,
    subtitleRes = R.string.settings_open_with_save_default_subtitle,
    keywords = listOf("save", "library", "default", "open with", "ad-hoc"),
    icon = Icons.Filled.SaveAlt,
    section = Section.OpenWith,
    group = OpenWithGroups.Behavior,
    kind = RowKind.Toggle,
  ),
  SettingsCatalogEntry(
    id = OpenWithCatalogIds.ID_AUTO_CLASSIFY,
    label = "Auto-classify unknown MIME types",
    subtitle = "Sniff bytes when the sender mis-declares the file type.",
    labelRes = R.string.settings_open_with_auto_classify_label,
    subtitleRes = R.string.settings_open_with_auto_classify_subtitle,
    keywords = listOf("mime", "classify", "open with", "sniff"),
    icon = Icons.Filled.OpenInNew,
    section = Section.OpenWith,
    group = OpenWithGroups.Behavior,
    kind = RowKind.Toggle,
  ),
)

internal object OpenWithCatalogIds {
  const val ID_RETENTION_DAYS = "open_with_retention_days"
  const val ID_SAVE_DEFAULT = "open_with_save_default"
  const val ID_AUTO_CLASSIFY = "open_with_auto_classify"
}
