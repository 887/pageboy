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

/**
 * Settings root page. Renders the grouped-cards layout that every other
 * settings sub-page (About, Licenses, future Appearance / Library /
 * Reader / Annotations / Signing) inherits.
 *
 * Phase A.4 only ships the About entry under the "About" group — real
 * per-feature sections land alongside the surfaces they configure.
 *
 * The screen lives inside the [com.eight87.pageboy.ui.PageboyApp]
 * Scaffold (which owns the top app bar + nav rail), so this Composable
 * doesn't repeat that chrome.
 */
@Composable
fun SettingsScreen(
  onAbout: () -> Unit,
) {
  Column(
    modifier = Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .semantics { testTag = "settings_screen" },
    verticalArrangement = Arrangement.spacedBy(SettingsDimens.CardSpacing),
  ) {
    Spacer(Modifier.height(12.dp))

    // The screen-title is rendered as a small label here instead of
    // pushing a sub-Scaffold (the parent already owns the top app bar).
    // Once the catalog grows real per-feature sections this label can
    // be promoted to a proper M3 LargeTopAppBar inside the nav host.
    Text(
      text = stringResource(R.string.settings_title),
      style = androidx.compose.material3.MaterialTheme.typography.headlineSmall,
      color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
      modifier = Modifier
        .padding(horizontal = SettingsDimens.PagePadding)
        .semantics { testTag = "settings_title" },
    )

    // Grouped catalog rendering. Phase A.4 has one group (About) with
    // one entry; the rest of the family (Appearance / Library / Reader
    // / Annotations / Signing) lands as more entries in
    // `sections/*.kt` files alongside their owning features.
    val rootEntries = SettingsCatalog.bySection(Section.Root)
    val grouped = rootEntries.groupBy { it.group }
    grouped.forEach { (group, items) ->
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

    // Tail breather so the last card doesn't sit flush against the
    // system nav inset.
    Spacer(modifier = Modifier.height(24.dp))
  }
}
