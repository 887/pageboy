package com.eight87.pageboy.ui.settings.sections

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.VerifiedUser
import com.eight87.pageboy.R
import com.eight87.pageboy.ui.settings.GroupRef
import com.eight87.pageboy.ui.settings.RowKind
import com.eight87.pageboy.ui.settings.Section
import com.eight87.pageboy.ui.settings.SettingsCatalogEntry

/**
 * Phase H.6 — Signing section in the settings catalog. Three live
 * entries (default key source picker, manage signing keys, reset
 * signing keys) plus two reserved entries that ship greyed-out as
 * v2 visibility hooks per format-pdf.md §3.2:
 *
 *  - PAdES-B-T (timestamp) — RFC 3161 TSA wired into the signing path.
 *  - PAdES-B-LT (long-term validation) — OCSP/CRL fetch + embed.
 *
 * Both reserved entries render with `enabled = false` in the settings
 * page so the user sees the roadmap without being able to toggle them.
 * Documented inline so a v2 agent can flip the enable flag without
 * re-introducing the row.
 */
internal object SigningGroups {
  val Keys = GroupRef(R.string.settings_group_signing_keys)
}

internal val SigningEntries: List<SettingsCatalogEntry> = listOf(
  SettingsCatalogEntry(
    id = SigningCatalogIds.ID_DEFAULT_KEY_SOURCE,
    label = "Default key source",
    subtitle = "Pick the key the sign sheet defaults to.",
    labelRes = R.string.settings_signing_default_source_label,
    subtitleRes = R.string.settings_signing_default_source_subtitle,
    keywords = listOf("sign", "key", "default", "keystore", "p12"),
    icon = Icons.Filled.Key,
    section = Section.Signing,
    group = SigningGroups.Keys,
    kind = RowKind.Picker,
  ),
  SettingsCatalogEntry(
    id = SigningCatalogIds.ID_MANAGE_KEYS,
    label = "Manage signing keys",
    subtitle = "Device key alias + imported .p12 references.",
    labelRes = R.string.settings_signing_manage_keys_label,
    subtitleRes = R.string.settings_signing_manage_keys_subtitle,
    keywords = listOf("sign", "key", "manage", "alias"),
    icon = Icons.Filled.VerifiedUser,
    section = Section.Signing,
    group = SigningGroups.Keys,
    kind = RowKind.Navigate,
  ),
  SettingsCatalogEntry(
    id = SigningCatalogIds.ID_RESET_KEYS,
    label = "Reset signing keys",
    subtitle = "Wipes the device key and imported .p12 references.",
    labelRes = R.string.settings_signing_reset_keys_label,
    subtitleRes = R.string.settings_signing_reset_keys_subtitle,
    keywords = listOf("sign", "reset", "wipe", "delete"),
    icon = Icons.Filled.RestartAlt,
    section = Section.Signing,
    group = SigningGroups.Keys,
    kind = RowKind.Action,
  ),
  // ---- v2 reservations — greyed-out in the page renderer ----
  SettingsCatalogEntry(
    id = SigningCatalogIds.ID_TIMESTAMP_RESERVED,
    label = "Add timestamp to signatures (PAdES-B-T)",
    subtitle = "Reserved for v2 — adds an RFC 3161 timestamp.",
    labelRes = R.string.settings_signing_timestamp_label,
    subtitleRes = R.string.settings_signing_timestamp_subtitle,
    keywords = listOf("sign", "timestamp", "tsa", "pades", "rfc3161"),
    icon = Icons.Filled.HourglassEmpty,
    section = Section.Signing,
    group = SigningGroups.Keys,
    kind = RowKind.Toggle,
  ),
  SettingsCatalogEntry(
    id = SigningCatalogIds.ID_LTV_RESERVED,
    label = "Long-term validation (PAdES-B-LT)",
    subtitle = "Reserved for v2 — embeds revocation material.",
    labelRes = R.string.settings_signing_ltv_label,
    subtitleRes = R.string.settings_signing_ltv_subtitle,
    keywords = listOf("sign", "ltv", "ocsp", "crl", "long-term", "pades"),
    icon = Icons.Filled.EditNote,
    section = Section.Signing,
    group = SigningGroups.Keys,
    kind = RowKind.Toggle,
  ),
)

internal object SigningCatalogIds {
  const val ID_DEFAULT_KEY_SOURCE = "signing_default_key_source"
  const val ID_MANAGE_KEYS = "signing_manage_keys"
  const val ID_RESET_KEYS = "signing_reset_keys"
  const val ID_TIMESTAMP_RESERVED = "signing_timestamp_reserved"
  const val ID_LTV_RESERVED = "signing_ltv_reserved"
}
