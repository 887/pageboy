package com.eight87.pageboy.format.epub

import androidx.fragment.app.Fragment
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Phase M.8 — smoke test for the [EpubFragmentHost.defaultFactory]
 * fallback. The full `build(...)` factory requires a live
 * [org.readium.r2.shared.publication.Publication], which the JVM tests
 * can't manufacture without a real EPUB asset; this test exercises the
 * defensive fallback path that the chrome restores when the EPUB
 * fragment is gone (e.g. nav-graph re-entry).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EpubFragmentHostTest {

  @Test
  fun `defaultFactory instantiates the FragmentManager default Fragment`() {
    val factory = EpubFragmentHost.defaultFactory()
    val fragment: Fragment = factory.instantiate(
      EpubFragmentHostTest::class.java.classLoader!!,
      Fragment::class.java.name,
    )
    assertNotNull("defaultFactory must return a Fragment instance", fragment)
  }
}
