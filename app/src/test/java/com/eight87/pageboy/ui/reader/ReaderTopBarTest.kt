package com.eight87.pageboy.ui.reader

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
 * Phase C.9 — top-bar smoke. Verifies back / find / share / overflow
 * icons render under a Robolectric Compose harness.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = TestApplication::class)
class ReaderTopBarTest {

  @get:Rule
  val composeRule = createComposeRule()

  @Test
  fun `top bar renders title and all four affordances`() {
    composeRule.setContent {
      ReaderTopBar(
        title = "Sample document",
        findActive = false,
        onBack = {},
        onToggleFind = {},
        onShare = {},
      )
    }
    composeRule.onNodeWithTag("reader_top_bar").assertExists()
    composeRule.onNodeWithTag("reader_title").assertIsDisplayed()
    composeRule.onNodeWithTag("reader_back_button").assertIsDisplayed()
    composeRule.onNodeWithTag("reader_find_button").assertIsDisplayed()
    composeRule.onNodeWithTag("reader_share_button").assertIsDisplayed()
    composeRule.onNodeWithTag("reader_overflow_button").assertIsDisplayed()
  }
}
