package com.eight87.pageboy.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.eight87.pageboy.TestApplication
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Phase A.3 smoke test. Renders [PageboyApp] under Robolectric and asserts
 * the shell (root row + Navigation 3 host) composes. This catches a
 * regression where `MaterialExpressiveTheme` or the NavDisplay fails to
 * compose. The library rail only renders once the data graph is wired, so
 * its tabs are covered by `LibraryRailSmokeTest` instead.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = TestApplication::class)
class MainScreenSmokeTest {

  @get:Rule
  val composeRule = createComposeRule()

  @Test
  fun `PageboyApp renders the root shell and nav host`() {
    composeRule.setContent { PageboyApp() }

    composeRule.onNodeWithTag("pageboy_root").assertExists()
    composeRule.onNodeWithTag("pageboy_nav_host").assertIsDisplayed()
  }

  @Test
  fun `PageboyApp lands on the Library placeholder by default when no graph wired`() {
    // No data-layer overrides + Robolectric's TestApplication => fallback
    // placeholder renders. With a real PageboyApplication the LibraryScreen
    // lands instead; that path is exercised by LibraryScreenSmokeTest.
    composeRule.setContent { PageboyApp() }
    composeRule.onNodeWithTag("library_placeholder").assertExists()
  }
}
