package com.eight87.pageboy.ui.library

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.eight87.pageboy.TestApplication
import com.eight87.pageboy.data.library.LibraryTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Compose smoke test for [LibraryRail], the vertical tab rail that replaced
 * the M3 NavigationRail + in-screen tab row. Asserts every [LibraryTab]
 * plus the settings button render and that taps are routed back out.
 */
@RunWith(RobolectricTestRunner::class)
// Tall portrait screen so all four 108dp rail items fit without scrolling.
@Config(sdk = [34], application = TestApplication::class, qualifiers = "w411dp-h891dp")
class LibraryRailSmokeTest {

  @get:Rule
  val composeRule = createComposeRule()

  @Test
  fun `LibraryRail renders one item per tab plus settings and routes taps`() {
    var selected: LibraryTab? = null
    var settingsOpened = false
    composeRule.setContent {
      LibraryRail(
        tabs = LibraryTab.entries,
        selectedTab = LibraryTab.All,
        onSelectTab = { selected = it },
        onOpenSettings = { settingsOpened = true },
      )
    }

    composeRule.onNodeWithTag("library_rail").assertExists()
    LibraryTab.entries.forEach { tab ->
      composeRule.onNodeWithTag("rail_tab_${tab.name}").assertIsDisplayed()
    }
    composeRule.onNodeWithTag("rail_settings").assertIsDisplayed()

    composeRule.onNodeWithTag("rail_tab_${LibraryTab.Pinned.name}").performClick()
    composeRule.onNodeWithTag("rail_settings").performClick()
    assertEquals(LibraryTab.Pinned, selected)
    assertTrue(settingsOpened)
  }
}
