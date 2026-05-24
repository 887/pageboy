package com.eight87.pageboy.format.mobi

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.eight87.pageboy.TestApplication
import com.eight87.pageboy.format.mobi.internal.MobiVariant
import com.eight87.pageboy.ui.reader.control.noopRendererContext
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Phase Q.7 — Robolectric Compose smoke for [MobiBody]. Verifies the
 * `AndroidView<WebView>` host composes with the expected test tag and
 * doesn't throw on initial layout.
 *
 * The Robolectric WebView shadow doesn't actually render HTML — we
 * assert on the `mobi_body` test tag presence + that the composable
 * tree builds without crash. End-to-end visual rendering verification
 * lives in the orchestrator's post-merge AVD smoke.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = TestApplication::class)
class MobiBodySmokeTest {

  @get:Rule
  val composeRule = createComposeRule()

  @Test
  fun `MobiBody hosts a WebView under the mobi_body test tag`() {
    val content = MobiBookContent(
      html = "<html><body><p>Phase Q smoke</p></body></html>",
      metadata = MobiMetadata(
        title = "Phase Q",
        author = null,
        publisher = null,
        description = null,
        isbn = null,
        subject = null,
        coverRecordIndex = null,
      ),
      images = emptyMap(),
      variant = MobiVariant.Mobi6,
    )
    val handle = MobiHandle(content = content, title = "Phase Q")
    composeRule.setContent {
      MaterialTheme {
        MobiBody(handle = handle, context = noopRendererContext("phaseq"))
      }
    }
    composeRule.onNodeWithTag("mobi_body").assertExists()
  }
}
