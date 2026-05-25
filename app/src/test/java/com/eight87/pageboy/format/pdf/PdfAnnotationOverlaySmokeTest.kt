package com.eight87.pageboy.format.pdf

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.eight87.pageboy.TestApplication
import com.eight87.pageboy.data.annotation.AnnotationEntity
import com.eight87.pageboy.data.annotation.AnnotationKind
import com.eight87.pageboy.data.annotation.AnnotationSource
import com.eight87.pageboy.domain.render.annotation.AnnotationCommands
import com.eight87.pageboy.domain.render.annotation.AnnotationTool
import com.eight87.pageboy.domain.render.annotation.AnnotationToolState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Phase G.7 — Compose smoke test for [PdfAnnotationOverlay] and
 * [PdfAnnotationToolbar].
 *
 * Asserts the overlay + toolbar compose against fake
 * [AnnotationCommands] + [AnnotationSource] handles and surface the
 * expected test tags. Doesn't drive the actual gesture handlers
 * (those require an active PdfViewerFragment + pointer-input plumbing
 * the Robolectric Compose harness doesn't fully support).
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = TestApplication::class)
class PdfAnnotationOverlaySmokeTest {

  @get:Rule
  val composeRule = createComposeRule()

  private class FakeCommands(
    initial: AnnotationToolState = AnnotationToolState(),
  ) : AnnotationCommands {
    private val _state = MutableStateFlow(initial)
    override val state: StateFlow<AnnotationToolState> = _state
    override suspend fun add(annotation: AnnotationEntity) { /* no-op */ }
    override suspend fun remove(id: String) { /* no-op */ }
    override fun setTool(tool: AnnotationTool?) { _state.value = _state.value.copy(tool = tool) }
    override fun setColor(colorArgb: Int) { _state.value = _state.value.copy(colorArgb = colorArgb) }
    override suspend fun updateStickyNote(id: String, text: String) { /* no-op */ }
  }

  private class FakeSource(private val annotations: List<AnnotationEntity>) : AnnotationSource {
    override fun observe(documentId: String): Flow<List<AnnotationEntity>> = flowOf(annotations)
    override fun observeForPage(documentId: String, pageIndex: Int): Flow<List<AnnotationEntity>> =
      flowOf(annotations.filter { it.pageIndex == pageIndex })
    override suspend fun list(documentId: String): List<AnnotationEntity> = annotations
  }

  private fun highlight(id: String, page: Int = 0) = AnnotationEntity(
    id = id,
    documentId = "doc",
    pageIndex = page,
    kind = AnnotationKind.Highlight.name,
    payloadJson = """{"kind":"HighlightPayload","rect":{"left":50,"bottom":700,"right":200,"top":720}}""",
    colorArgb = 0x66FFEB3B.toInt(),
    pageWidthPt = 612f, pageHeightPt = 792f,
    createdAt = 1L, modifiedAt = 1L,
  )

  @Test
  fun `overlay renders with empty annotation list`() {
    composeRule.setContent {
      MaterialTheme {
        PdfAnnotationOverlay(
          documentId = "doc",
          pageWidthPt = 612f,
          pageHeightPt = 792f,
          pageRotationDegrees = 0,
          annotationSource = FakeSource(emptyList()),
          annotationCommands = FakeCommands(),
        )
      }
    }
    composeRule.onNodeWithTag("pdf_annotation_overlay").assertExists()
  }

  @Test
  fun `overlay renders with one highlight annotation`() {
    composeRule.setContent {
      MaterialTheme {
        PdfAnnotationOverlay(
          documentId = "doc",
          pageWidthPt = 612f,
          pageHeightPt = 792f,
          pageRotationDegrees = 0,
          annotationSource = FakeSource(listOf(highlight("a"))),
          annotationCommands = FakeCommands(),
        )
      }
    }
    composeRule.onNodeWithTag("pdf_annotation_overlay").assertExists()
  }

  @Test
  fun `toolbar exposes one chip per AnnotationTool`() {
    composeRule.setContent {
      MaterialTheme {
        PdfAnnotationToolbar(commands = FakeCommands())
      }
    }
    composeRule.onNodeWithTag("pdf_annotation_toolbar").assertIsDisplayed()
    AnnotationTool.values().forEach { tool ->
      composeRule.onNodeWithTag("pdf_tool_${tool.name}").assertExists()
    }
  }

  @Test
  fun `toolbar reflects active tool selection`() {
    val commands = FakeCommands(initial = AnnotationToolState(tool = AnnotationTool.Highlight))
    composeRule.setContent {
      MaterialTheme {
        PdfAnnotationToolbar(commands = commands)
      }
    }
    composeRule.onNodeWithTag("pdf_tool_Highlight").assertExists()
  }
}
