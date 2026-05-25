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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.eight87.pageboy.R
import com.eight87.pageboy.data.settings.ReaderSettings
import com.eight87.pageboy.data.settings.ThemeMode
import com.eight87.pageboy.data.settings.ThemeSettings
import com.eight87.pageboy.data.signing.DefaultKeySource
import com.eight87.pageboy.data.signing.SigningSettings
import com.eight87.pageboy.data.openwith.OpenWithSettings
import com.eight87.pageboy.ui.settings.sections.AppearanceCatalogIds
import com.eight87.pageboy.ui.settings.sections.LibraryCatalogIds
import com.eight87.pageboy.ui.settings.sections.OpenWithCatalogIds
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
  themeSettings: ThemeSettings? = null,
  signingSettings: SigningSettings? = null,
  openWithSettings: OpenWithSettings? = null,
  onManageSigningKeys: () -> Unit = {},
  onResetSigningKeys: () -> Unit = {},
) {
  val scope = rememberCoroutineScope()
  // Reader settings
  val continuousScrolling = readerSettings?.continuousScrolling
  val continuousScrollingValue by (continuousScrolling?.flow
    ?: kotlinx.coroutines.flow.flowOf(true)).collectAsState(initial = true)
  // Theme settings
  val themeMode by (themeSettings?.themeMode?.flow
    ?: kotlinx.coroutines.flow.flowOf(ThemeMode.System)).collectAsState(initial = ThemeMode.System)
  val dynamicColorEnabled by (themeSettings?.dynamicColor?.flow
    ?: kotlinx.coroutines.flow.flowOf(true)).collectAsState(initial = true)
  val seedColor by (themeSettings?.seedColor?.flow
    ?: kotlinx.coroutines.flow.flowOf(0L)).collectAsState(initial = 0L)
  // OpenWith settings
  val openWithRetention by (openWithSettings?.ephemeralRetentionDays?.flow
    ?: kotlinx.coroutines.flow.flowOf(7)).collectAsState(initial = 7)
  val openWithSaveDefault by (openWithSettings?.saveAdHocToLibraryDefault?.flow
    ?: kotlinx.coroutines.flow.flowOf(false)).collectAsState(initial = false)
  val openWithAutoClassify by (openWithSettings?.autoClassifyUnknownMime?.flow
    ?: kotlinx.coroutines.flow.flowOf(true)).collectAsState(initial = true)
  // Dialog state for theme picker and color picker
  var showThemeModePicker by remember { mutableStateOf(false) }
  var showColorPicker by remember { mutableStateOf(false) }
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

    // Appearance section — theme mode + dynamic color + seed color.
    if (themeSettings != null) {
      SettingsCatalog.bySection(Section.Appearance)
        .groupBy { it.group }
        .forEach { (group, items) ->
          SettingsCard(
            title = groupTitleFor(group.labelRes),
            modifier = Modifier.padding(horizontal = SettingsDimens.PagePadding),
          ) {
            items.forEachIndexed { index, entry ->
              when (entry.id) {
                AppearanceCatalogIds.ID_THEME_MODE -> {
                  val subtitle = when (themeMode) {
                    ThemeMode.Light -> "Light"
                    ThemeMode.Dark -> "Dark"
                    ThemeMode.System -> "System"
                  }
                  NavigateRow(
                    entry = entry.copy(subtitle = subtitle),
                    onClick = { showThemeModePicker = true },
                  )
                }
                AppearanceCatalogIds.ID_DYNAMIC_COLOR -> {
                  ToggleRow(
                    entry = entry,
                    checked = dynamicColorEnabled,
                    onCheckedChange = { v ->
                      scope.launch { themeSettings.dynamicColor.set(v) }
                    },
                    switchTestTag = "settings_appearance_dynamic_switch",
                  )
                }
                AppearanceCatalogIds.ID_SEED_COLOR -> {
                  val subtitle = if (seedColor != 0L) "#%06X".format(seedColor) else "Brand default"
                  NavigateRow(
                    entry = entry.copy(subtitle = subtitle),
                    onClick = { showColorPicker = true },
                  )
                }
                else -> NavigateRow(entry = entry, onClick = {})
              }
              if (index < items.size - 1) SettingsRowDivider()
            }
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

    // Phase N — Open With section.
    if (openWithSettings != null) {
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
                  NavigateRow(
                    entry = entry.copy(subtitle = "$openWithRetention days"),
                    onClick = { /* Slider sub-page deferred; retention changes via picker in v1.x */ },
                  )
                }
                OpenWithCatalogIds.ID_SAVE_DEFAULT -> {
                  ToggleRow(
                    entry = entry,
                    checked = openWithSaveDefault,
                    onCheckedChange = { v ->
                      scope.launch { openWithSettings.saveAdHocToLibraryDefault.set(v) }
                    },
                    switchTestTag = "settings_openwith_save_switch",
                  )
                }
                OpenWithCatalogIds.ID_AUTO_CLASSIFY -> {
                  ToggleRow(
                    entry = entry,
                    checked = openWithAutoClassify,
                    onCheckedChange = { v ->
                      scope.launch { openWithSettings.autoClassifyUnknownMime.set(v) }
                    },
                    switchTestTag = "settings_openwith_classify_switch",
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

  // Theme mode picker dialog.
  if (showThemeModePicker && themeSettings != null) {
    androidx.compose.material3.AlertDialog(
      onDismissRequest = { showThemeModePicker = false },
      title = { Text(stringResource(R.string.settings_appearance_theme_mode_label)) },
      text = {
        Column {
          ThemeMode.entries.forEach { mode ->
            val label = when (mode) {
              ThemeMode.Light -> "Light"
              ThemeMode.Dark -> "Dark"
              ThemeMode.System -> "System"
            }
            androidx.compose.material3.TextButton(
              onClick = {
                scope.launch { themeSettings.themeMode.set(mode) }
                showThemeModePicker = false
              },
              modifier = Modifier.semantics { testTag = "theme_mode_${mode.name}" },
            ) {
              Text(
                text = if (mode == themeMode) "$label (selected)" else label,
                style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
              )
            }
          }
        }
      },
      confirmButton = {
        androidx.compose.material3.TextButton(onClick = { showThemeModePicker = false }) {
          Text(stringResource(R.string.dialog_cancel))
        }
      },
    )
  }

  // Color picker dialog.
  if (showColorPicker && themeSettings != null) {
    ColorPickerDialog(
      initialRgb = seedColor,
      onConfirm = { rgb ->
        scope.launch { themeSettings.seedColor.set(rgb) }
        showColorPicker = false
      },
      onDismiss = { showColorPicker = false },
      onReset = {
        scope.launch { themeSettings.seedColor.set(0L) }
        showColorPicker = false
      },
    )
  }
}
