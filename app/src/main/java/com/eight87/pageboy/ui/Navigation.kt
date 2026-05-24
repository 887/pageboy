package com.eight87.pageboy.ui

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Top-level navigation keys. Names finalised at Phase B — the rail keeps
 * Library / Recents / Pinned / Settings (count stays at four; rail
 * crowding past five degrades the family's visual register, see
 * `ui-shell.md`).
 *
 * Recents and Pinned at the top level are redundant once the LibraryScreen
 * has its own tab row, but the user's brief specifically named "multiple
 * tabs for started, just browsing, etc." — so the rail surfaces the same
 * tab as a shortcut for one-tap access. The shortcut is wired via
 * pre-setting `LibraryUiSettings.tab` before navigating to LibraryRoute.
 */
@Serializable data object LibraryRoute : NavKey

@Serializable data object RecentsRoute : NavKey

@Serializable data object PinnedRoute : NavKey

@Serializable data object SettingsRootRoute : NavKey

/** Sub-route inside Settings — shown when the user taps the About row. */
@Serializable data object SettingsAboutRoute : NavKey

/** Sub-route inside About — the open-source licenses sub-page. */
@Serializable data object SettingsLicensesRoute : NavKey

/** Phase B — sub-route inside Settings → Library → Source folders. */
@Serializable data object SettingsLibraryFoldersRoute : NavKey

/** Phase B — placeholder reader route. Real reader is Phase C+. */
@Serializable data class ReaderRoute(val documentId: String, val title: String) : NavKey
