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

  override fun onCreate() {
    // Phase I.2 / J.1 — Apache POI's StAX bootstrap MUST land before any
    // POI class loads. Android does not ship `javax.xml.stream.*`
    // factories; POI 5.x respects these three `org.apache.poi.*`
    // overrides explicitly so the stripped Android runtime never gets
    // asked for the missing factories. Aalto is the StAX impl we ship
    // (com.fasterxml:aalto-xml). See docs/plans/format-docx.md §Android.
    PoiStaxBootstrap.installAaltoOnce()
    super.onCreate()
  }

  /**
   * Lazily-built composition root. Constructed on first access so unit
   * tests that don't reach into the graph never spin up Room. The
   * lifetime matches the process — fine for our usage (no
   * application-multi-process configuration in the manifest).
   */
  val appGraph: AppGraph by lazy { AppGraph(this) }
}
