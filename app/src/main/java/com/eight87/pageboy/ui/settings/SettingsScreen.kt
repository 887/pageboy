package com.eight87.pageboy.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import com.eight87.pageboy.R
import com.eight87.pageboy.ui.settings.sections.LibraryCatalogIds

/**
 * Settings root page. Renders the grouped-cards layout that every other
 * settings sub-page (About, Licenses, Library folders) inherits.
 *
 * Phase B adds the Library section with two entries (Source folders →
 * the per-root management screen, Re-scan now → coordinator action).
 *
 * The screen lives inside the [com.eight87.pageboy.ui.PageboyApp]
 * NavDisplay (no parent app bar) — this Composable owns its own title.
 */
@Composable
fun SettingsScreen(
  onAbout: () -> Unit,
  onLibraryFolders: () -> Unit = {},
  onRescanNow: () -> Unit = {},
) {
  Column(
    modifier = Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .semantics { testTag = "settings_screen" },
    verticalArrangement = Arrangement.spacedBy(SettingsDimens.CardSpacing),
  ) {
    Spacer(Modifier.height(12.dp))

    Text(
      text = stringResource(R.string.settings_title),
      style = androidx.compose.material3.MaterialTheme.typography.headlineSmall,
      color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
      modifier = Modifier
        .padding(horizontal = SettingsDimens.PagePadding)
        .semantics { testTag = "settings_title" },
    )

    // Root section.
    SettingsCatalog.bySection(Section.Root)
      .groupBy { it.group }
      .forEach { (group, items) ->
        SettingsCard(
          title = groupTitleFor(group.labelRes),
          modifier = Modifier.padding(horizontal = SettingsDimens.PagePadding),
        ) {
          items.forEachIndexed { index, entry ->
            val onClick: () -> Unit = when (entry.id) {
              SettingsCatalog.ID_ABOUT -> onAbout
              else -> ({})
            }
            NavigateRow(entry = entry, onClick = onClick)
            if (index < items.size - 1) SettingsRowDivider()
          }
        }
      }

    // Phase B — Library section.
    SettingsCatalog.bySection(Section.Library)
      .groupBy { it.group }
      .forEach { (group, items) ->
        SettingsCard(
          title = groupTitleFor(group.labelRes),
          modifier = Modifier.padding(horizontal = SettingsDimens.PagePadding),
        ) {
          items.forEachIndexed { index, entry ->
            val onClick: () -> Unit = when (entry.id) {
              LibraryCatalogIds.ID_LIBRARY_FOLDERS -> onLibraryFolders
              LibraryCatalogIds.ID_RESCAN_NOW -> onRescanNow
              else -> ({})
            }
            NavigateRow(entry = entry, onClick = onClick)
            if (index < items.size - 1) SettingsRowDivider()
          }
        }
      }

    Spacer(modifier = Modifier.height(24.dp))
  }
}
