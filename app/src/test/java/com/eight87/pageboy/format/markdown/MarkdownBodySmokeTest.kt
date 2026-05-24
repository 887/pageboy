package com.eight87.pageboy.format.markdown

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.eight87.pageboy.TestApplication
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Phase D / D.11 — Robolectric Compose smoke that the Markdown body
 * lays out a representative document covering every supported block
 * kind without throwing. We assert on visible text fragments + on the
 * `markdown_body` test-tag so a regression in MarkdownBody's tree
 * surfaces here.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = TestApplication::class)
class MarkdownBodySmokeTest {

  @get:Rule
  val composeRule = createComposeRule()

  private val sample = """
    # Smoke Title

    A paragraph with **bold** and *italic* and `code` and a [link](https://example.com).

    ## Lists

    - Bullet one
    - Bullet two
      - Nested

    1. Numbered one
    2. Numbered two

    - [x] Done task
    - [ ] Open task

    > Quoted paragraph
    > continued

    ```kotlin
    val x = 1
    ```

    ---

    | A | B |
    | --- | --- |
    | 1 | 2 |

    Strike: ~~done~~ text.

    ![alt-text](https://example.com/image.png)
  """.trimIndent()

  @Test
  fun `renders a document covering every supported block kind`() {
    val parser = MarkdownParser()
    val ast = parser.parse(sample)
    val handle = MarkdownHandle(
      ast = ast,
      rawText = sample,
      frontMatter = emptyMap(),
      title = "Smoke Title",
    )
    composeRule.setContent {
      MaterialTheme {
        MarkdownBody(handle = handle)
      }
    }
    composeRule.onNodeWithTag("markdown_body").assertExists()
    // The first item should be visible — assert on the heading text.
    composeRule.onNodeWithText("Smoke Title", substring = true).assertExists()
  }
}
