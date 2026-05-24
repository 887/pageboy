package com.eight87.pageboy

import android.app.Application

/**
 * Application subclass. Owns the hand-rolled [AppGraph] (Room database,
 * DataStore handles, SAF library scanner, library repository) — the
 * family's composition-root pattern. Activities reach in via
 * `(applicationContext as PageboyApplication).appGraph`.
 *
 * Tests use `TestApplication` instead to keep Robolectric runs zero-cost
 * (no Room DB open per test).
 */
class PageboyApplication : Application() {

  /**
   * Lazily-built composition root. Constructed on first access so unit
   * tests that don't reach into the graph never spin up Room. The
   * lifetime matches the process — fine for our usage (no
   * application-multi-process configuration in the manifest).
   */
  val appGraph: AppGraph by lazy { AppGraph(this) }
}
