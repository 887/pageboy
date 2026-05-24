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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import com.eight87.pageboy.R
import com.eight87.pageboy.data.settings.ReaderSettings
import com.eight87.pageboy.data.signing.DefaultKeySource
import com.eight87.pageboy.data.signing.SigningSettings
import com.eight87.pageboy.ui.settings.sections.LibraryCatalogIds
import com.eight87.pageboy.ui.settings.sections.ReaderCatalogIds
import com.eight87.pageboy.ui.settings.sections.SigningCatalogIds
import kotlinx.coroutines.launch

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
  readerSettings: ReaderSettings? = null,
  signingSettings: SigningSettings? = null,
  onManageSigningKeys: () -> Unit = {},
  onResetSigningKeys: () -> Unit = {},
) {
  val scope = rememberCoroutineScope()
  // Reader settings — null when the SettingsScreen is rendered without
  // the AppGraph (e.g. the legacy MainScreenSmokeTest). The Reader
  // section renders only when readerSettings is supplied.
  val continuousScrolling = readerSettings?.continuousScrolling
  val continuousScrollingValue by (continuousScrolling?.flow
    ?: kotlinx.coroutines.flow.flowOf(true)).collectAsState(initial = true)
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

    // Phase C.8 — Reader section.
    if (readerSettings != null) {
      SettingsCatalog.bySection(Section.Reader)
        .groupBy { it.group }
        .forEach { (group, items) ->
          SettingsCard(
            title = groupTitleFor(group.labelRes),
            modifier = Modifier.padding(horizontal = SettingsDimens.PagePadding),
          ) {
            items.forEachIndexed { index, entry ->
              when (entry.id) {
                ReaderCatalogIds.ID_CONTINUOUS_SCROLL -> {
                  ToggleRow(
                    entry = entry,
                    checked = continuousScrollingValue,
                    onCheckedChange = { v ->
                      continuousScrolling?.let { setting ->
                        scope.launch { setting.set(v) }
                      }
                    },
                    switchTestTag = "settings_reader_continuous_switch",
                  )
                }
                else -> NavigateRow(entry = entry, onClick = {})
              }
              if (index < items.size - 1) SettingsRowDivider()
            }
          }
        }
    }

    // Phase H.6 — Signing section. Renders only when signingSettings
    // is wired (production: AppGraph.signingSettings; tests: omitted to
    // keep the catalog smoke tests stable).
    if (signingSettings != null) {
      val defaultKeySource by signingSettings.defaultKeySource.flow
        .collectAsState(initial = DefaultKeySource.KEYSTORE)
      SettingsCatalog.bySection(Section.Signing)
        .groupBy { it.group }
        .forEach { (group, items) ->
          SettingsCard(
            title = groupTitleFor(group.labelRes),
            modifier = Modifier.padding(horizontal = SettingsDimens.PagePadding),
          ) {
            items.forEachIndexed { index, entry ->
              when (entry.id) {
                SigningCatalogIds.ID_DEFAULT_KEY_SOURCE -> {
                  // Two-state picker — toggle behaves like a binary
                  // picker until a future multi-state picker DSL lands.
                  ToggleRow(
                    entry = entry,
                    checked = defaultKeySource == DefaultKeySource.KEYSTORE,
                    onCheckedChange = { v ->
                      scope.launch {
                        signingSettings.defaultKeySource.set(
                          if (v) DefaultKeySource.KEYSTORE
                          else DefaultKeySource.ASK_EACH_TIME,
                        )
                      }
                    },
                    switchTestTag = "settings_signing_default_source_switch",
                  )
                }
                SigningCatalogIds.ID_MANAGE_KEYS -> NavigateRow(entry = entry, onClick = onManageSigningKeys)
                SigningCatalogIds.ID_RESET_KEYS -> NavigateRow(entry = entry, onClick = onResetSigningKeys)
                SigningCatalogIds.ID_TIMESTAMP_RESERVED,
                SigningCatalogIds.ID_LTV_RESERVED -> {
                  // Reserved for v2 — render as a disabled toggle that
                  // surfaces the roadmap without firing an action.
                  ToggleRow(
                    entry = entry,
                    checked = false,
                    onCheckedChange = { /* reserved — no-op */ },
                    switchTestTag = "settings_signing_${entry.id}_switch",
                  )
                }
                else -> NavigateRow(entry = entry, onClick = {})
              }
              if (index < items.size - 1) SettingsRowDivider()
            }
          }
        }
    }

    Spacer(modifier = Modifier.height(24.dp))
  }
}
