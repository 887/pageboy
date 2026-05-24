package com.eight87.pageboy.format.txt

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.eight87.pageboy.TestApplication
import com.eight87.pageboy.ui.reader.control.noopRendererContext
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.nio.charset.StandardCharsets

/**
 * Phase E.6 — Robolectric Compose smoke for the TXT body. Asserts the
 * `LazyColumn` composes with the expected test tag + a known line
 * surfaces in the visible viewport.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = TestApplication::class)
class TxtBodySmokeTest {

  @get:Rule
  val composeRule = createComposeRule()

  @Test
  fun `renders a small txt document inside a LazyColumn`() {
    val source = InMemoryTxtLineSource(
      bytes = "first\nsecond\nthird".toByteArray(StandardCharsets.UTF_8),
      charset = StandardCharsets.UTF_8,
    )
    val handle = TxtHandle(
      lineSource = source,
      encodingLabel = "UTF-8",
      title = "sample.txt",
    )
    composeRule.setContent {
      MaterialTheme {
        TxtBody(handle = handle, context = noopRendererContext("sample.txt"))
      }
    }
    composeRule.onNodeWithTag("txt_body").assertExists()
    composeRule.onNodeWithText("first", substring = false).assertExists()
  }
}
