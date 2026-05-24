package com.eight87.pageboy.format.epub

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentFactory
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication

/**
 * Phase M.3 — bridge between a Readium [Publication] and the
 * [FragmentFactory] the FragmentManager uses when `AndroidFragment`
 * inflates an [EpubNavigatorFragment].
 *
 * Readium's `EpubNavigatorFragment` requires constructor-injected
 * publication / locator / preferences / listener references, which the
 * `FragmentManager`'s reflective default-constructor instantiation
 * cannot provide. The recommended pattern (per Readium's
 * `EpubNavigatorFactory.createFragmentFactory(...)`) is to build a
 * dedicated [FragmentFactory] per publication and install it onto the
 * host activity's `FragmentManager.fragmentFactory` *before*
 * `AndroidFragment<EpubNavigatorFragment>(...)` triggers instantiation.
 *
 * This file owns the factory construction so [EpubBody] can stay tight
 * (just the Compose host + the scroll-position / find / ToC bridges).
 *
 * SOLID notes:
 *  - **R.X.1** narrow — single-method object. Takes the inputs it
 *    needs to produce a factory; never the chrome's god-handles.
 *  - **R.X.7 Compose ISP** — caller passes only the four narrow
 *    parameters [build] accepts, not a parent god-state object.
 */
internal object EpubFragmentHost {

  /**
   * Build a [FragmentFactory] that returns the configured
   * [EpubNavigatorFragment] when the [FragmentManager] asks for one.
   *
   * @param publication the Readium [Publication] from [EpubParser].
   * @param initialLocator restore-on-open locator (null = render from
   *   the beginning). Built by [EpubBody] from the saved
   *   [com.eight87.pageboy.domain.render.ScrollPosition.EpubCfi].
   * @param preferences EPUB-side preferences (font size, theme, etc.).
   *   v1 ships the defaults; pageboy's settings sheet edits them in a
   *   later phase.
   * @param configuration WebView-security gate. v1 always passes the
   *   hardened configuration that disables JS + restricts URL loading
   *   per `docs/plans/format-epub.md` §spec gotcha #3.
   */
  fun build(
    publication: Publication,
    initialLocator: Locator?,
    preferences: EpubPreferences,
    configuration: EpubNavigatorFragment.Configuration,
  ): FragmentFactory {
    val factory = EpubNavigatorFactory(publication = publication)
    // Positional call against Readium's `createFragmentFactory(
    //   initialLocator, listOfLinks, preferences, listener,
    //   paginationListener, configuration)` so we don't depend on
    //   parameter-name stability across Readium minor versions.
    return factory.createFragmentFactory(
      initialLocator,
      emptyList(),
      preferences,
      null,
      null,
      configuration,
    )
  }

  /**
   * Marker-class instantiator used as a fallback FragmentFactory when
   * Readium's factory needs to delegate up the chain for non-Epub
   * fragments. Pageboy never asks the FragmentManager to construct
   * anything other than the EPUB fragment, so this returns the default
   * `Fragment()` for any other class (the same behaviour as
   * `FragmentFactory()`'s default impl).
   */
  fun defaultFactory(): FragmentFactory = object : FragmentFactory() {
    override fun instantiate(classLoader: ClassLoader, className: String): Fragment {
      return super.instantiate(classLoader, className)
    }
  }
}
