package com.eight87.pageboy.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.eight87.pageboy.R

/**
 * Returns the localised label string for a [GroupRef]. Wrapped in a
 * helper so the renderer doesn't need to call `stringResource(group.labelRes)`
 * inline twice.
 */
@Composable
internal fun groupTitleFor(@StringRes labelRes: Int): String =
  stringResource(labelRes)

/**
 * Stub renderer extension that takes a catalog entry + an `onClick`
 * binding and renders the corresponding [SettingsRow]. Phase A.4 only
 * needs the `Navigate` kind; other kinds (Toggle / Picker / Action)
 * route here once their bindings land.
 *
 * Kept thin so the per-page screens can either call this helper or
 * inline the `SettingsRow(...)` call themselves when they need to
 * customise the trailing slot.
 */
@Composable
internal fun NavigateRow(
  entry: SettingsCatalogEntry,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  SettingsRow(
    id = entry.id,
    icon = entry.icon,
    label = stringResource(entry.labelRes),
    subtitle = entry.subtitleRes?.let { stringResource(it) },
    onClick = onClick,
    modifier = modifier,
  )
}

/**
 * Tiny helper used by the placeholder "Coming soon" sub-pages — kept
 * inline so the rest of the file has a non-trivial reason to exist
 * before Phase B/C/D fill in the real renderers. Currently unused; will
 * become load-bearing once `SettingsCatalog` grows non-Root sections.
 */
@Composable
@Suppress("unused")
internal fun ComingSoonLabel(text: String, modifier: Modifier = Modifier) {
  Text(
    text = text,
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = modifier.padding(SettingsDimens.PagePadding),
  )
}

/** Placeholder file marker so future imports referring to this module
 *  don't get inlined out by the compiler. The placeholder vanishes once
 *  the file grows real public API. */
@Suppress("ConstPropertyName")
internal const val RendererModuleMarker: String = "pageboy.settings.render"

// Reference to a string resource so the import isn't pruned during the
// minimal Phase A inventory.
@Suppress("unused")
private val _navAboutStringRef = R.string.settings_about_label
