package com.eight87.pageboy.ui

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Top-level navigation keys. Names are tentative per Phase A.3 — the format
 * work in later phases may reshape the rail (e.g. drop "Pinned" if no one
 * uses it, add "Authors" if EPUB metadata surfaces it cheaply). The
 * COUNT stays at four (rail crowding past five degrades the family's
 * visual register — see `ui-shell.md`).
 */
@Serializable data object LibraryRoute : NavKey

@Serializable data object RecentsRoute : NavKey

@Serializable data object PinnedRoute : NavKey

@Serializable data object SettingsRootRoute : NavKey

/** Sub-route inside Settings — shown when the user taps the About row. */
@Serializable data object SettingsAboutRoute : NavKey

/** Sub-route inside About — the open-source licenses sub-page. */
@Serializable data object SettingsLicensesRoute : NavKey
