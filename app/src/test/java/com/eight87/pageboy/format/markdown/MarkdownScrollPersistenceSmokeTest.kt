package com.eight87.pageboy.format.markdown

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.eight87.pageboy.TestApplication
import com.eight87.pageboy.domain.render.RendererContext
import com.eight87.pageboy.domain.render.RendererScrollSink
import com.eight87.pageboy.domain.render.ScrollPosition
import com.eight87.pageboy.ui.reader.control.NoopRendererFindSink
import com.eight87.pageboy.ui.reader.control.NoopRendererReadingPrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Phase E.6 — Robolectric Compose smoke for the scroll-persistence
 * wiring inside [MarkdownBody]. Verifies the renderer calls
 * `scrollSink.load()` on first compose and records an initial position
 * via `scrollSink.record(...)` once the LazyColumn has measured.
 * Closes O.D.3 verification.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = TestApplication::class)
class MarkdownScrollPersistenceSmokeTest {

  @get:Rule
  val composeRule = createComposeRule()

  private class RecordingScrollSink(private val savedPosition: ScrollPosition? = null) : RendererScrollSink {
    var loadCalls = 0
    val records = mutableListOf<ScrollPosition>()
    override suspend fun load(): ScrollPosition? {
      loadCalls++
      return savedPosition
    }
    override fun record(position: ScrollPosition) {
      records += position
    }
  }

  @Test
  fun `scrollSink load is called on first compose`() {
    val sink = RecordingScrollSink()
    val handle = MarkdownHandle(
      ast = MarkdownParser().parse("# Title\n\nbody"),
      rawText = "# Title\n\nbody",
      frontMatter = emptyMap(),
      title = "Title",
    )
    val ctx = RendererContext(
      documentId = "doc-A",
      scrollSink = sink,
      findSink = NoopRendererFindSink(),
      readingPrefs = NoopRendererReadingPrefs(),
    )
    composeRule.setContent {
      MaterialTheme {
        MarkdownBody(handle = handle, context = ctx)
      }
    }
    composeRule.onNodeWithTag("markdown_body").assertExists()
    composeRule.waitForIdle()
    assertEquals(1, sink.loadCalls)
  }

  @Test
  fun `scrollSink record fires for the initial visible position`() {
    val sink = RecordingScrollSink()
    val handle = MarkdownHandle(
      ast = MarkdownParser().parse("# Title\n\nbody\n\nmore"),
      rawText = "# Title\n\nbody\n\nmore",
      frontMatter = emptyMap(),
      title = "Title",
    )
    val ctx = RendererContext(
      documentId = "doc-B",
      scrollSink = sink,
      findSink = NoopRendererFindSink(),
      readingPrefs = NoopRendererReadingPrefs(),
    )
    composeRule.setContent {
      MaterialTheme {
        MarkdownBody(handle = handle, context = ctx)
      }
    }
    composeRule.waitForIdle()
    // The very first snapshotFlow emission records (0, 0).
    assertTrue("expected at least one scroll record", sink.records.isNotEmpty())
    assertEquals(0, (sink.records.first() as ScrollPosition.LazyColumn).itemIndex)
  }
}
