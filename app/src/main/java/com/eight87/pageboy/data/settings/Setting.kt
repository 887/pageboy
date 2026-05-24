package com.eight87.pageboy.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Phase C.8 — R.B.1 value type collapsing the
 * `<Preferences.Key + Flow + setter + decode-default>` quartet into one named
 * handle. Mirrors whisperboy's `Setting<T>`, landed here on the first
 * pageboy phase that adds a settings surface beyond the Library section.
 *
 * **Why this exists.** Tonearmboy grew a 826-LOC `SettingsRepository` with
 * 25+ hand-rolled key/flow/setter/snapshot quartets and a 27-field
 * `SettingsSnapshot` that every sub-page subscribed to — toggling theme
 * recomposed the audio surface. Phase B's `LibraryUiSettings` already
 * encodes the quartet by hand; once a second facet (the Reader settings
 * Phase C ships) wants the same shape, the cost of the boilerplate starts
 * compounding. [Setting] is the shared value type future facets pivot on.
 *
 * **What this is NOT.** A god `SettingsRepository`. Each facet
 * ([ReaderSettings], the future `LibrarySettings` / `ThemeSettings` /
 * etc.) is its own interface; the implementations use the [setting] /
 * [enumSetting] factories below to build [Setting] handles backed by
 * DataStore Preferences keys. Consumers read `.flow` for observation and
 * `set(value)` / `invoke(value)` for writes. No `SettingsSnapshot` ever.
 *
 * **Migration of [com.eight87.pageboy.data.library.LibraryUiSettings].**
 * Out of scope for Phase C. The existing facet stays on its hand-rolled
 * quartet; switching it to [Setting] is a pure refactor that doesn't
 * earn the churn yet. New facets land [Setting]-shaped from day one.
 */
class Setting<T>(
  val flow: Flow<T>,
  private val setter: suspend (T) -> Unit,
) {
  /** Symmetric with `setting.flow` — call-site reads as a function. */
  suspend operator fun invoke(value: T) {
    setter(value)
  }

  /** More readable alternative to `setting(value)`. */
  suspend fun set(value: T) {
    setter(value)
  }
}

/**
 * Enum specialisation — round-trips through `enum.name` and coerces an
 * unknown / corrupted stored value back to [default] on read. Same
 * defensive `runCatching { enumValueOf<E>(it) }` discipline every
 * hand-rolled facet has been doing inline.
 *
 * Wraps rather than extends [Setting] so call sites that take a typed
 * `Setting<E>` parameter can accept `EnumSetting.asSetting()` without
 * variance gymnastics.
 */
class EnumSetting<E : Enum<E>>(
  val flow: Flow<E>,
  private val setter: suspend (E) -> Unit,
) {
  suspend operator fun invoke(value: E) {
    setter(value)
  }

  suspend fun set(value: E) {
    setter(value)
  }

  fun asSetting(): Setting<E> = Setting(flow, setter)
}

/**
 * Build a [Setting] from a typed Preferences key. The flow falls back to
 * [default] when the key is absent; the setter writes through
 * `dataStore.edit { ... }`. Replaces the hand-rolled key + flow + setter
 * trio.
 */
fun <T> DataStore<Preferences>.setting(
  key: Preferences.Key<T>,
  default: T,
): Setting<T> = Setting(
  flow = data.map { prefs -> prefs[key] ?: default },
  setter = { value -> edit { it[key] = value } },
)

/**
 * Build an [EnumSetting] backed by a string preference storing `enum.name`.
 * Unknown / null values coerce to [default] on read.
 */
inline fun <reified E : Enum<E>> DataStore<Preferences>.enumSetting(
  name: String,
  default: E,
): EnumSetting<E> {
  val key = stringPreferencesKey(name)
  return EnumSetting(
    flow = data.map { prefs ->
      prefs[key]?.let { raw -> runCatching { enumValueOf<E>(raw) }.getOrNull() } ?: default
    },
    setter = { value -> edit { it[key] = value.name } },
  )
}
