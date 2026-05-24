package com.eight87.pageboy.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.eight87.pageboy.TestApplication
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Phase A.3 smoke test. Renders [PageboyApp] under Robolectric and asserts
 * the four navigation rail entries (Library / Recents / Pinned / Settings)
 * are present. This catches a regression where the rail composable is
 * accidentally removed, the rail items lose their testTags, or
 * `MaterialExpressiveTheme` fails to compose under the m3-expressive
 * `1.5.0-alpha18` override.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = TestApplication::class)
class MainScreenSmokeTest {

  @get:Rule
  val composeRule = createComposeRule()

  @Test
  fun `PageboyApp renders the nav rail with four destinations`() {
    composeRule.setContent { PageboyApp() }

    composeRule.onNodeWithTag("pageboy_root").assertExists()
    composeRule.onNodeWithTag("pageboy_nav_rail").assertExists()

    composeRule.onNodeWithTag("nav_rail_library").assertIsDisplayed()
    composeRule.onNodeWithTag("nav_rail_recents").assertIsDisplayed()
    composeRule.onNodeWithTag("nav_rail_pinned").assertIsDisplayed()
    composeRule.onNodeWithTag("nav_rail_settings").assertIsDisplayed()
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
