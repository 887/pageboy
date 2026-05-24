package com.eight87.pageboy

import android.app.Application

/**
 * Application subclass. Currently a placeholder for the lifecycle hook —
 * future phases will hang the `AppGraph` (Room database, DataStore
 * preferences, the SAF library scanner, the per-format `DocumentRenderer`
 * registry, etc.) off this surface following the family's hand-rolled
 * composition-root pattern.
 */
class PageboyApplication : Application()
