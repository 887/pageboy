package com.eight87.pageboy.format.markdown

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.eight87.pageboy.TestApplication
import com.eight87.pageboy.domain.render.FindMatch
import com.eight87.pageboy.domain.render.RendererContext
import com.eight87.pageboy.domain.render.RendererFindSink
import com.eight87.pageboy.ui.reader.control.NoopRendererReadingPrefs
import com.eight87.pageboy.ui.reader.control.NoopRendererScrollSink
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Phase E.6 — Robolectric Compose smoke for the find-in-doc wiring
 * inside [MarkdownBody]. Verifies the renderer observes the chrome's
 * find query + publishes matches back through the sink (closes O.D.1
 * deferral verification).
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = TestApplication::class)
class MarkdownBodyFindSmokeTest {

  @get:Rule
  val composeRule = createComposeRule()

  private class RecordingFindSink(initialQuery: String = "") : RendererFindSink {
    private val q = MutableStateFlow(initialQuery)
    private val idx = MutableStateFlow(-1)
    var submitted: List<FindMatch> = emptyList()
    override val query: StateFlow<String> = q.asStateFlow()
    override val currentMatchIndex: StateFlow<Int> = idx.asStateFlow()
    override fun submitMatches(matches: List<FindMatch>) {
      submitted = matches
    }
    fun setQuery(value: String) { q.value = value }
    fun setCurrent(value: Int) { idx.value = value }
  }

  private val sample = """
    # Title

    Paragraph with the word table inside.

    | A | B |
    | --- | --- |
    | 1 | 2 |
  """.trimIndent()

  @Test
  fun `find sink receives matches after the query is set`() {
    val find = RecordingFindSink(initialQuery = "table")
    val handle = MarkdownHandle(
      ast = MarkdownParser().parse(sample),
      rawText = sample,
      frontMatter = emptyMap(),
      title = "Title",
    )
    val ctx = RendererContext(
      documentId = "doc",
      scrollSink = NoopRendererScrollSink(),
      findSink = find,
      readingPrefs = NoopRendererReadingPrefs(),
    )
    composeRule.setContent {
      MaterialTheme {
        MarkdownBody(handle = handle, context = ctx)
      }
    }
    composeRule.onNodeWithTag("markdown_body").assertExists()
    composeRule.waitForIdle()
    // We submitted matches via the rawText scan; the recording sink
    // captured them.
    assertTrue("expected at least one match for the word 'table'", find.submitted.isNotEmpty())
    // Each match carries the offset of an occurrence in the raw text.
    val firstHit = sample.lowercase().indexOf("table")
    assertEquals(firstHit, find.submitted.first().rangeStart)
  }

  @Test
  fun `empty query produces no matches`() {
    val find = RecordingFindSink(initialQuery = "")
    val handle = MarkdownHandle(
      ast = MarkdownParser().parse("hello"),
      rawText = "hello",
      frontMatter = emptyMap(),
      title = "h",
    )
    val ctx = RendererContext(
      documentId = "doc",
      scrollSink = NoopRendererScrollSink(),
      findSink = find,
      readingPrefs = NoopRendererReadingPrefs(),
    )
    composeRule.setContent {
      MaterialTheme {
        MarkdownBody(handle = handle, context = ctx)
      }
    }
    composeRule.waitForIdle()
    assertTrue(find.submitted.isEmpty())
  }
}
