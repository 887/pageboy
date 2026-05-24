package com.eight87.pageboy.ui.reader

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.eight87.pageboy.TestApplication
import com.eight87.pageboy.data.library.DocumentFormat
import com.eight87.pageboy.format.placeholder.PlaceholderHandle
import com.eight87.pageboy.format.placeholder.PlaceholderRenderer
import com.eight87.pageboy.format.registry.CompiledFormatRegistry
import com.eight87.pageboy.domain.render.RendererReadingPrefs
import com.eight87.pageboy.domain.render.ScrollPosition
import com.eight87.pageboy.ui.reader.control.InMemoryFindInDocCommands
import com.eight87.pageboy.ui.reader.control.NoopRendererReadingPrefs
import com.eight87.pageboy.ui.reader.control.ReaderState
import com.eight87.pageboy.ui.reader.control.ReaderStateProjector
import com.eight87.pageboy.ui.reader.control.ScrollPersistence
import com.eight87.pageboy.ui.reader.control.ShareExportCommands
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Phase C.9 — full chrome smoke. Renders [ReaderScreen] with a projector
 * pre-set to [ReaderState.Open] holding a [PlaceholderHandle]; asserts
 * the chrome scaffolds + the placeholder body lands.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = TestApplication::class)
class ReaderScreenSmokeTest {

  @get:Rule
  val composeRule = createComposeRule()

  private class FakeProjector(initial: ReaderState) : ReaderStateProjector {
    private val _state = MutableStateFlow(initial)
    override val state: StateFlow<ReaderState> = _state.asStateFlow()
    override fun open(documentId: String) {}
    override fun close() {}
  }

  private class NoopShare : ShareExportCommands {
    override fun shareCurrentDocument(documentUriString: String, displayName: String) {}
  }

  private class NoopScroll : ScrollPersistence {
    override suspend fun lastPosition(documentId: String): ScrollPosition? = null
    override fun recordPosition(documentId: String, position: ScrollPosition) {}
  }

  private fun fakeFindCommands(): InMemoryFindInDocCommands = InMemoryFindInDocCommands()
  private fun fakeReadingPrefs(): RendererReadingPrefs = NoopRendererReadingPrefs()

  @Test
  fun `renders chrome plus placeholder body when state is Open`() {
    val registry = CompiledFormatRegistry(emptyMap())
    val handle = PlaceholderHandle(format = DocumentFormat.Pdf, title = "manual.pdf")
    val projector = FakeProjector(ReaderState.Open(handle))
    // Pre-register an explicit placeholder so the registry returns one
    // (the fallback also works, but pinning the impl makes the test
    // deterministic about which path is exercised).
    val registryWithPlaceholder = CompiledFormatRegistry(
      mapOf(DocumentFormat.Pdf to PlaceholderRenderer(DocumentFormat.Pdf)),
    )

    composeRule.setContent {
      ReaderScreen(
        documentId = "doc-1",
        readerStateProjector = projector,
        formatRegistry = registryWithPlaceholder,
        findInDocCommands = fakeFindCommands(),
        shareExportCommands = NoopShare(),
        scrollPersistence = NoopScroll(),
        readingPrefs = fakeReadingPrefs(),
        onBack = {},
      )
    }
    composeRule.onNodeWithTag("reader_screen").assertExists()
    composeRule.onNodeWithTag("reader_top_bar").assertExists()
    composeRule.onNodeWithTag("reader_body").assertExists()
    composeRule.onNodeWithTag("reader_placeholder_body").assertExists()
    // Registry param is supplied but unused in this scenario.
    @Suppress("UNUSED_VARIABLE")
    val unused = registry
  }

  @Test
  fun `renders the error state when state is Failed`() {
    val registry = CompiledFormatRegistry(emptyMap())
    val projector = FakeProjector(ReaderState.Failed("test failure"))
    composeRule.setContent {
      ReaderScreen(
        documentId = "doc-1",
        readerStateProjector = projector,
        formatRegistry = registry,
        findInDocCommands = fakeFindCommands(),
        shareExportCommands = NoopShare(),
        scrollPersistence = NoopScroll(),
        readingPrefs = fakeReadingPrefs(),
        onBack = {},
      )
    }
    composeRule.onNodeWithTag("reader_screen").assertExists()
    composeRule.onNodeWithTag("reader_error_state").assertExists()
  }
}
