package com.eight87.pageboy.format.pdf

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import com.eight87.pageboy.data.annotation.AnnotationEntity
import com.eight87.pageboy.data.annotation.AnnotationKind
import com.eight87.pageboy.data.annotation.AnnotationPayload
import com.eight87.pageboy.data.annotation.AnnotationSource
import com.eight87.pageboy.data.annotation.PdfPoint
import com.eight87.pageboy.data.annotation.PdfRect
import com.eight87.pageboy.format.pdf.internal.PdfCoordinates
import com.eight87.pageboy.format.pdf.internal.ScreenPoint
import com.eight87.pageboy.domain.render.annotation.AnnotationCommands
import com.eight87.pageboy.domain.render.annotation.AnnotationTool
import com.eight87.pageboy.domain.render.annotation.toKind
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * Phase G.4 — Compose overlay drawn on top of the [PdfBody] fragment
 * via a Box z-stack.
 *
 * Reads [AnnotationSource.observe] for the open document, draws each
 * annotation per its [AnnotationKind] via Compose [Canvas] primitives.
 * Tap gestures dispatch through [AnnotationCommands].
 *
 * v1 simplifications (documented for the Phase G+ followup):
 *  - Single-page model: the overlay assumes the visible page is page
 *    0 (androidx.pdf's `PdfViewerFragment` doesn't expose its
 *    `currentPage` flow publicly in alpha18). The coordinate system
 *    still works — annotations on page 0 render correctly, and
 *    `pageWidthPt` / `pageHeightPt` carry the actual page dims for
 *    when the multi-page API lands.
 *  - Page rotation defaults to 0; rotation-aware rendering uses
 *    [PdfCoordinates] but the rotation value is sourced from
 *    `pageRotationDegrees` once the fragment exposes it.
 *  - Long-press to delete an annotation is wired through the standard
 *    tap-gesture pipeline; the per-stroke hit-test is rect-based
 *    (annotations within ~24 px of the tap are selectable).
 *
 * The overlay is gated on the [AnnotationCommands.state] — when the
 * tool is null, the overlay passes pointer events through to the
 * PdfViewerFragment underneath (so reading + scrolling stays
 * unaffected). When a tool is selected, the overlay captures pointer
 * events to author new annotations.
 */
@Composable
internal fun PdfAnnotationOverlay(
  documentId: String,
  pageWidthPt: Float,
  pageHeightPt: Float,
  pageRotationDegrees: Int,
  annotationSource: AnnotationSource,
  annotationCommands: AnnotationCommands,
  modifier: Modifier = Modifier,
) {
  val toolState by annotationCommands.state.collectAsState()
  val annotations by remember(documentId, annotationSource) {
    annotationSource.observeForPage(documentId, pageIndex = 0)
  }.let { flow ->
    flow.collectAsState(initial = emptyList())
  }

  var canvasSize by remember { mutableStateOf(Size.Zero) }
  var inFlightInk by remember { mutableStateOf<List<ScreenPoint>>(emptyList()) }
  val coroutineScope = rememberCoroutineScopeWrapper()

  Box(
    modifier = modifier
      .fillMaxSize()
      .semantics { testTag = "pdf_annotation_overlay" }
      .let { base ->
        if (toolState.tool == null) base
        else base.pointerInput(toolState.tool, canvasSize) {
          detectTapGestures(
            onTap = { offset ->
              val tool = toolState.tool ?: return@detectTapGestures
              if (canvasSize == Size.Zero) return@detectTapGestures
              coroutineScope.launch {
                val entity = composeTapAnnotation(
                  tool = tool,
                  tapScreen = ScreenPoint(offset.x, offset.y),
                  canvasSize = canvasSize,
                  pageWidthPt = pageWidthPt,
                  pageHeightPt = pageHeightPt,
                  rotation = pageRotationDegrees,
                  colorArgb = toolState.colorArgb,
                  documentId = documentId,
                ) ?: return@launch
                annotationCommands.add(entity)
              }
            },
            onLongPress = { offset ->
              val hit = annotations.firstOrNull { entity ->
                hitTest(
                  entity = entity,
                  tap = ScreenPoint(offset.x, offset.y),
                  canvasSize = canvasSize,
                  pageWidthPt = pageWidthPt,
                  pageHeightPt = pageHeightPt,
                  rotation = pageRotationDegrees,
                )
              } ?: return@detectTapGestures
              coroutineScope.launch { annotationCommands.remove(hit.id) }
            },
          )
        }
      },
  ) {
    Canvas(modifier = Modifier.fillMaxSize()) {
      canvasSize = size
      annotations.forEach { entity ->
        drawAnnotation(
          entity = entity,
          canvasSize = size,
          pageWidthPt = pageWidthPt,
          pageHeightPt = pageHeightPt,
          rotation = pageRotationDegrees,
        )
      }
      // In-flight ink preview (Phase G.4 leaves the live-stroke
      // preview as a single onTap-and-commit shape in v1; the live
      // pointer-input path lands when the chrome ships androidx.ink's
      // InProgressStrokesView per format-pdf.md Phase G.3).
      if (inFlightInk.isNotEmpty()) {
        val path = Path()
        path.moveTo(inFlightInk.first().x, inFlightInk.first().y)
        inFlightInk.drop(1).forEach { p -> path.lineTo(p.x, p.y) }
        drawPath(path = path, color = Color(toolState.colorArgb), style = Stroke(width = 4f))
      }
    }
  }
}

/**
 * Compose-friendly draw extension. Per-kind dispatch (R.X.2 sealed
 * exhaustive); kind values come from the enum-name string in
 * [AnnotationEntity.kind] so unknown kinds (forward-compat from a
 * future v2 row) silently no-op.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawAnnotation(
  entity: AnnotationEntity,
  canvasSize: Size,
  pageWidthPt: Float,
  pageHeightPt: Float,
  rotation: Int,
) {
  val kind = runCatching { AnnotationKind.valueOf(entity.kind) }.getOrNull() ?: return
  val payload = runCatching {
    PdfAnnotationOverlayJson.decodeFromString<AnnotationPayload>(entity.payloadJson)
  }.getOrNull() ?: return

  val color = Color(entity.colorArgb)
  when (kind) {
    AnnotationKind.Highlight -> {
      val rect = (payload as? AnnotationPayload.HighlightPayload)?.rect ?: return
      val s = PdfCoordinates.pdfRectToScreen(
        rect, pageWidthPt, pageHeightPt, canvasSize.width, canvasSize.height, rotation,
      )
      drawRect(
        color = color,
        topLeft = Offset(s.left, s.top),
        size = Size(s.width, s.height),
      )
    }
    AnnotationKind.Underline -> {
      val rect = (payload as? AnnotationPayload.UnderlinePayload)?.rect ?: return
      val s = PdfCoordinates.pdfRectToScreen(
        rect, pageWidthPt, pageHeightPt, canvasSize.width, canvasSize.height, rotation,
      )
      drawLine(
        color = color,
        start = Offset(s.left, s.bottom),
        end = Offset(s.right, s.bottom),
        strokeWidth = 3f,
      )
    }
    AnnotationKind.Strikethrough -> {
      val rect = (payload as? AnnotationPayload.StrikethroughPayload)?.rect ?: return
      val s = PdfCoordinates.pdfRectToScreen(
        rect, pageWidthPt, pageHeightPt, canvasSize.width, canvasSize.height, rotation,
      )
      val midY = (s.top + s.bottom) / 2f
      drawLine(
        color = color,
        start = Offset(s.left, midY),
        end = Offset(s.right, midY),
        strokeWidth = 3f,
      )
    }
    AnnotationKind.FreehandInk -> {
      val ink = (payload as? AnnotationPayload.FreehandInkPayload) ?: return
      if (ink.stroke.size < 2) return
      val path = Path()
      ink.stroke.forEachIndexed { i, point ->
        val s = PdfCoordinates.pdfPointToScreen(
          point, pageWidthPt, pageHeightPt, canvasSize.width, canvasSize.height, rotation,
        )
        if (i == 0) path.moveTo(s.x, s.y) else path.lineTo(s.x, s.y)
      }
      drawPath(path = path, color = color, style = Stroke(width = ink.thicknessPt))
    }
    AnnotationKind.StickyNote -> {
      val pin = (payload as? AnnotationPayload.StickyNotePayload)?.anchor ?: return
      val s = PdfCoordinates.pdfPointToScreen(
        pin, pageWidthPt, pageHeightPt, canvasSize.width, canvasSize.height, rotation,
      )
      drawCircle(color = color, radius = 12f, center = Offset(s.x, s.y))
    }
    AnnotationKind.Stamp -> {
      // Phase G.4 v1 — render stamp as a translucent rect placeholder;
      // the bitmap fetch path consumes the imageRef in Phase H once
      // the signature-capture screen lands the saved-stamp bytes.
      val rect = (payload as? AnnotationPayload.StampPayload)?.rect ?: return
      val s = PdfCoordinates.pdfRectToScreen(
        rect, pageWidthPt, pageHeightPt, canvasSize.width, canvasSize.height, rotation,
      )
      drawRect(
        color = color,
        topLeft = Offset(s.left, s.top),
        size = Size(s.width, s.height),
        style = Stroke(width = 2f),
      )
    }
  }
}

/**
 * Tap-or-drag → annotation payload. v1 is single-tap dispatch only:
 *  - StickyNote → drop pin at the tap point.
 *  - Highlight / Underline / Strikethrough → drop a ~80x18 pt rect
 *    centered at the tap (drag-rectangle authoring lands when the
 *    pointer-input path captures down/up events through the
 *    PdfViewerFragment, which is a Phase G+ polish).
 *  - FreehandInk → drop a tiny dot stroke (the proper continuous-pen
 *    capture lives behind androidx.ink in Phase G.3, see
 *    format-pdf.md G.3).
 *  - Stamp → drop an 80x80 pt rect; the imageRef stays empty until
 *    Phase H's signature-capture screen wires the saved stamp.
 */
private fun composeTapAnnotation(
  tool: AnnotationTool,
  tapScreen: ScreenPoint,
  canvasSize: Size,
  pageWidthPt: Float,
  pageHeightPt: Float,
  rotation: Int,
  colorArgb: Int,
  documentId: String,
): AnnotationEntity? {
  val pdfPoint = PdfCoordinates.screenPointToPdf(
    tapScreen, pageWidthPt, pageHeightPt, canvasSize.width, canvasSize.height, rotation,
  )
  val now = System.currentTimeMillis()
  val id = UUID.randomUUID().toString()
  val payload: AnnotationPayload = when (tool) {
    AnnotationTool.Highlight -> AnnotationPayload.HighlightPayload(
      rect = defaultTextRect(pdfPoint),
    )
    AnnotationTool.Underline -> AnnotationPayload.UnderlinePayload(
      rect = defaultTextRect(pdfPoint),
    )
    AnnotationTool.Strikethrough -> AnnotationPayload.StrikethroughPayload(
      rect = defaultTextRect(pdfPoint),
    )
    AnnotationTool.FreehandInk -> AnnotationPayload.FreehandInkPayload(
      stroke = listOf(pdfPoint, PdfPoint(pdfPoint.x + 1f, pdfPoint.y + 1f)),
      thicknessPt = 2f,
    )
    AnnotationTool.StickyNote -> AnnotationPayload.StickyNotePayload(
      anchor = pdfPoint,
      text = "",
    )
    AnnotationTool.Stamp -> AnnotationPayload.StampPayload(
      imageRef = "",
      rect = PdfRect(
        left = pdfPoint.x - 40f,
        bottom = pdfPoint.y - 40f,
        right = pdfPoint.x + 40f,
        top = pdfPoint.y + 40f,
      ),
    )
  }
  return AnnotationEntity(
    id = id,
    documentId = documentId,
    pageIndex = 0,
    kind = tool.toKind().name,
    payloadJson = PdfAnnotationOverlayJson.encodeToString(payload),
    colorArgb = colorArgb,
    pageWidthPt = pageWidthPt,
    pageHeightPt = pageHeightPt,
    createdAt = now,
    modifiedAt = now,
  )
}

/** Default ~80x18 pt rect centered on a tap (the typical text-line height). */
private fun defaultTextRect(p: PdfPoint): PdfRect = PdfRect(
  left = p.x - 40f, bottom = p.y - 9f, right = p.x + 40f, top = p.y + 9f,
)

/** Simple bounding-box hit test for long-press select. */
private fun hitTest(
  entity: AnnotationEntity,
  tap: ScreenPoint,
  canvasSize: Size,
  pageWidthPt: Float,
  pageHeightPt: Float,
  rotation: Int,
): Boolean {
  val payload = runCatching {
    PdfAnnotationOverlayJson.decodeFromString<AnnotationPayload>(entity.payloadJson)
  }.getOrNull() ?: return false
  val rect: PdfRect = when (payload) {
    is AnnotationPayload.HighlightPayload -> payload.rect
    is AnnotationPayload.UnderlinePayload -> payload.rect
    is AnnotationPayload.StrikethroughPayload -> payload.rect
    is AnnotationPayload.StampPayload -> payload.rect
    is AnnotationPayload.StickyNotePayload -> PdfRect(
      left = payload.anchor.x - 12f,
      bottom = payload.anchor.y - 12f,
      right = payload.anchor.x + 12f,
      top = payload.anchor.y + 12f,
    )
    is AnnotationPayload.FreehandInkPayload -> {
      val xs = payload.stroke.map { it.x }
      val ys = payload.stroke.map { it.y }
      if (xs.isEmpty() || ys.isEmpty()) return false
      PdfRect(
        left = xs.min() - 6f,
        bottom = ys.min() - 6f,
        right = xs.max() + 6f,
        top = ys.max() + 6f,
      )
    }
  }
  val s = PdfCoordinates.pdfRectToScreen(
    rect, pageWidthPt, pageHeightPt, canvasSize.width, canvasSize.height, rotation,
  )
  return tap.x in s.left..s.right && tap.y in s.top..s.bottom
}

/** Local JSON encoder so the overlay decodes payloads the same way the chrome encodes them. */
internal val PdfAnnotationOverlayJson: Json = Json {
  ignoreUnknownKeys = true
  classDiscriminator = "kind"
}

@Composable
private fun rememberCoroutineScopeWrapper() = androidx.compose.runtime.rememberCoroutineScope()
