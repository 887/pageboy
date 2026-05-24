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
import com.eight87.pageboy.data.openwith.OpenWithSettings
import com.eight87.pageboy.data.settings.ReaderSettings
import com.eight87.pageboy.ui.settings.sections.LibraryCatalogIds
import com.eight87.pageboy.ui.settings.sections.OpenWithCatalogIds
import com.eight87.pageboy.ui.settings.sections.ReaderCatalogIds
import kotlinx.coroutines.launch
import androidx.compose.material3.Slider

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
  openWithSettings: OpenWithSettings? = null,
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

    // Phase N.12 — Open with section. Only rendered when the
    // OpenWithSettings handle is available (production wiring via
    // AppGraph). Three rows: retention slider + save-default toggle +
    // auto-classify toggle.
    if (openWithSettings != null) {
      val retentionDays by openWithSettings.ephemeralRetentionDays.flow.collectAsState(
        initial = OpenWithSettings.DEFAULT_RETENTION_DAYS,
      )
      val saveDefault by openWithSettings.saveAdHocToLibraryDefault.flow.collectAsState(
        initial = false,
      )
      val autoClassify by openWithSettings.autoClassifyUnknownMime.flow.collectAsState(
        initial = true,
      )
      SettingsCatalog.bySection(Section.OpenWith)
        .groupBy { it.group }
        .forEach { (group, items) ->
          SettingsCard(
            title = groupTitleFor(group.labelRes),
            modifier = Modifier.padding(horizontal = SettingsDimens.PagePadding),
          ) {
            items.forEachIndexed { index, entry ->
              when (entry.id) {
                OpenWithCatalogIds.ID_RETENTION_DAYS -> {
                  SettingsRow(
                    id = entry.id,
                    icon = entry.icon,
                    label = stringResource(entry.labelRes),
                    subtitle = stringResource(R.string.settings_open_with_retention_subtitle, retentionDays),
                    onClick = null,
                    trailing = {
                      Slider(
                        value = retentionDays.toFloat(),
                        onValueChange = { v ->
                          scope.launch {
                            openWithSettings.ephemeralRetentionDays.set(
                              v.toInt().coerceIn(
                                OpenWithSettings.MIN_RETENTION_DAYS,
                                OpenWithSettings.MAX_RETENTION_DAYS,
                              ),
                            )
                          }
                        },
                        valueRange = OpenWithSettings.MIN_RETENTION_DAYS.toFloat()..
                          OpenWithSettings.MAX_RETENTION_DAYS.toFloat(),
                        steps = OpenWithSettings.MAX_RETENTION_DAYS - OpenWithSettings.MIN_RETENTION_DAYS - 1,
                        modifier = Modifier.semantics { testTag = "settings_open_with_retention_slider" },
                      )
                    },
                  )
                }
                OpenWithCatalogIds.ID_SAVE_DEFAULT -> {
                  ToggleRow(
                    entry = entry,
                    checked = saveDefault,
                    onCheckedChange = { v ->
                      scope.launch { openWithSettings.saveAdHocToLibraryDefault.set(v) }
                    },
                    switchTestTag = "settings_open_with_save_default_switch",
                  )
                }
                OpenWithCatalogIds.ID_AUTO_CLASSIFY -> {
                  ToggleRow(
                    entry = entry,
                    checked = autoClassify,
                    onCheckedChange = { v ->
                      scope.launch { openWithSettings.autoClassifyUnknownMime.set(v) }
                    },
                    switchTestTag = "settings_open_with_auto_classify_switch",
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
