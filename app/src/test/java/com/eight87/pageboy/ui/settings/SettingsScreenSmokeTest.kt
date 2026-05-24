package com.eight87.pageboy.ui.settings

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.eight87.pageboy.TestApplication
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Phase A.4 smoke test. Renders [SettingsScreen] under Robolectric and
 * asserts the placeholder About row is present + clickable. Catches a
 * regression where the catalog goes empty, the About entry loses its
 * `Navigate` binding, or the testTag plumbing in `SettingsRow` breaks.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = TestApplication::class)
class SettingsScreenSmokeTest {

  @get:Rule
  val composeRule = createComposeRule()

  @Test
  fun `SettingsScreen renders with the About entry`() {
    composeRule.setContent { SettingsScreen(onAbout = {}) }

    composeRule.onNodeWithTag("settings_screen").assertExists()
    composeRule.onNodeWithTag("settings_title").assertExists()
    // The About row's testTag is `settings_row_<id>` (see SettingsCardDsl).
    composeRule.onNodeWithTag("settings_row_${SettingsCatalog.ID_ABOUT}")
      .assertExists()
      .assertHasClickAction()
  }

  @Test
  fun `SettingsScreen About row click fires onAbout`() {
    var clicks = 0
    composeRule.setContent { SettingsScreen(onAbout = { clicks += 1 }) }

    composeRule.onNodeWithTag("settings_row_${SettingsCatalog.ID_ABOUT}").performClick()
    composeRule.runOnIdle {
      assertEquals(1, clicks)
    }
  }

  @Test
  fun `SettingsCatalog contains the About entry`() {
    val about = SettingsCatalog.byId(SettingsCatalog.ID_ABOUT)
    assertEquals(Section.Root, about.section)
    assertEquals(RowKind.Navigate, about.kind)
  }
}
