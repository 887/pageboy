package com.eight87.pageboy.data.signing

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.eight87.pageboy.data.settings.EnumSetting
import com.eight87.pageboy.data.settings.Setting
import com.eight87.pageboy.data.settings.enumSetting
import com.eight87.pageboy.data.settings.setting

/**
 * Phase H.6 — signing-side settings facet. Narrow per-axis interface
 * (R.X.1) — the Signing settings sub-page reads this; the per-document
 * sign sheet reads only what it needs.
 *
 * **Why a separate facet (not on ReaderSettings).** Signing has its own
 * lifecycle (key generation, persisted alias, imported PKCS#12 SAF
 * refs) and its own settings sub-screen; lumping it under
 * `ReaderSettings` would grow that facet beyond its single-responsibility
 * boundary (R.X.4 — `ReaderSettings` is the *reading* preferences, not
 * the cryptographic-identity store).
 *
 * **What this stores.**
 *
 *  - [defaultKeySource] — what to default the cryptographic-sign sheet
 *    to. `KEYSTORE` (use device-resident EC P-256 key, generating one
 *    on first sign) or `ASK_EACH_TIME` (always show the picker). Per
 *    Phase H.6 settings spec.
 *  - [keystoreAlias] — the Android Keystore alias pageboy uses for the
 *    signing key. Single alias per device for v1 (multi-identity comes
 *    later if anyone asks). Empty string = no key generated yet; the
 *    PadesSigner generates one on first use and writes it back here.
 *  - [importedPkcs12Refs] — comma-separated SAF URI strings the user
 *    has imported. The keystore itself stays on disk via the SAF
 *    provider; pageboy only stores the URI reference. Password is per
 *    session, never persisted.
 */
interface SigningSettings {
  val defaultKeySource: EnumSetting<DefaultKeySource>
  val keystoreAlias: Setting<String>
  val importedPkcs12Refs: Setting<String>
}

/** Sealed-style enum for the two default key sources Phase H.6 surfaces. */
enum class DefaultKeySource {
  /** Pageboy generates / re-uses a single Android Keystore EC P-256 key (the casual path). */
  KEYSTORE,
  /** The signing sheet shows the key-picker every time (the qualified path or paranoid users). */
  ASK_EACH_TIME,
}

/**
 * DataStore-backed impl. Lives behind the [SigningSettings] interface;
 * `AppGraph` is the only place that constructs it (R.X.3).
 */
class AndroidSigningSettings(
  private val dataStore: DataStore<Preferences>,
) : SigningSettings {

  override val defaultKeySource: EnumSetting<DefaultKeySource> =
    dataStore.enumSetting(KEY_DEFAULT_KEY_SOURCE, default = DefaultKeySource.KEYSTORE)

  override val keystoreAlias: Setting<String> =
    dataStore.setting(stringPreferencesKey(KEY_KEYSTORE_ALIAS), default = "")

  override val importedPkcs12Refs: Setting<String> =
    dataStore.setting(stringPreferencesKey(KEY_PKCS12_REFS), default = "")

  private companion object {
    const val KEY_DEFAULT_KEY_SOURCE = "signing_default_key_source"
    const val KEY_KEYSTORE_ALIAS = "signing_keystore_alias"
    const val KEY_PKCS12_REFS = "signing_pkcs12_refs"
  }
}
