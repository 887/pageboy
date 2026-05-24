package com.eight87.pageboy.ui.settings

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import com.eight87.pageboy.R
import com.eight87.pageboy.ui.settings.sections.LibraryEntries
import com.eight87.pageboy.ui.settings.sections.OpenWithEntries
import com.eight87.pageboy.ui.settings.sections.ReaderEntries
import com.eight87.pageboy.ui.settings.sections.RootEntries

/**
 * Where this entry sits in the settings hierarchy. The root settings
 * screen is rendered from entries whose `section` is [Section.Root];
 * each sub-page is rendered from entries with the matching section
 * value. (Phase A.4 ships only the Root section with a single About
 * placeholder; real per-feature sections — Appearance, Library, Reader,
 * Annotations, Signing — land alongside the surfaces they configure.)
 */
enum class Section { Root, Appearance, Library, Reader, OpenWith, Annotations, Signing }

/**
 * Grouping bucket inside a section. All entries with the same
 * (section, group) pair render inside one `SettingsCard`. Order of
 * entries inside the card follows the order they appear in the catalog.
 */
data class GroupRef(@StringRes val labelRes: Int)

/** Pre-built group refs reused by the catalog entries. */
internal object Groups {
  val About = GroupRef(R.string.settings_group_about)
}

/**
 * What kind of UI affordance the row exposes. The page renderer
 * switches on this to decide whether the row needs a Switch, a picker
 * dialog, or a plain navigation tap. Phase A.4 only needs `Navigate`;
 * the rest are placeholders for future surfaces.
 */
enum class RowKind {
  /** Tapping navigates to another destination. */
  Navigate,
  /** Boolean toggle wired to a settings repository (future). */
  Toggle,
  /** Picker (radio dialog) wired to a settings repository (future). */
  Picker,
  /** Leaf action with a confirmation or immediate effect (future). */
  Action,
}

/**
 * One settings entry. The catalog is the single source of truth: every
 * row visible in any settings sub-page comes from this list, and a
 * future global search filters this list. There is no parallel "screen
 * definition" — the screens render by filtering the catalog.
 */
data class SettingsCatalogEntry(
  val id: String,
  /** Canonical English label, kept inline so the catalog can be searched
   *  without a Context handle (tonearmboy's pattern). */
  val label: String,
  /** Canonical English subtitle, for the same reason as [label]. */
  val subtitle: String? = null,
  /** Translated string resource for the row label. */
  @StringRes val labelRes: Int,
  /** Translated string resource for the row subtitle, when present. */
  @StringRes val subtitleRes: Int? = null,
  val keywords: List<String> = emptyList(),
  val icon: ImageVector,
  val section: Section,
  val group: GroupRef,
  val kind: RowKind,
)

/**
 * Single source of truth for every settings row. Adding a new setting
 * is one entry in the appropriate `sections/<Section>Entries.kt` file
 * plus a binding in the corresponding sub-page renderer.
 *
 * Phase A.4 ships only the Root section's About entry. Real per-feature
 * sections (Appearance, Library, Reader, Annotations, Signing) land
 * alongside the surfaces they configure in later phases.
 */
object SettingsCatalog {

  /** Stable ID for the root → About row. Tests reference this; do not
   *  rename without updating both the binding in `SettingsScreen.kt`
   *  and any test references. */
  const val ID_ABOUT = "about"

  /** Flat aggregation across the per-section files. */
  val entries: List<SettingsCatalogEntry> =
    RootEntries + LibraryEntries + ReaderEntries + OpenWithEntries

  /** Look up an entry by id. Throws if missing — IDs are compile-time
   *  stable. */
  fun byId(id: String): SettingsCatalogEntry = entries.first { it.id == id }

  /** Entries belonging to one section, grouped and rendered in one
   *  card per group. */
  fun bySection(section: Section): List<SettingsCatalogEntry> =
    entries.filter { it.section == section }
}
