package com.eight87.pageboy.data.annotation

import kotlinx.serialization.Serializable

/**
 * Phase G.1 — sealed per-kind payload. One JSON shape per
 * [AnnotationKind]; encoded into the [AnnotationEntity.payloadJson]
 * TEXT column via `kotlinx.serialization`. Adding a new kind = adding
 * a [Serializable] subclass here + an enum case in [AnnotationKind].
 *
 * Coordinates use PDF user-space (origin bottom-left, units = points,
 * 1 pt = 1/72 inch) — see `format/pdf/internal/PdfCoordinates.kt` for
 * the conversion helpers the overlay + exporter share.
 */
@Serializable
sealed class AnnotationPayload {

  /**
   * Single rectangle covering the text the user selected. Quad-points
   * stay future-friendly (multi-line text selections will land as
   * multiple [HighlightPayload] rows in v1; the proper quadPoints array
   * variant lands when androidx.pdf exposes the selection API publicly).
   */
  @Serializable
  data class HighlightPayload(
    val rect: PdfRect,
  ) : AnnotationPayload()

  @Serializable
  data class UnderlinePayload(
    val rect: PdfRect,
  ) : AnnotationPayload()

  @Serializable
  data class StrikethroughPayload(
    val rect: PdfRect,
  ) : AnnotationPayload()

  /**
   * One stroke = a list of (x,y) points in PDF user-space. Pressure /
   * timestamp samples are stretch goals (Phase H surface — stylus
   * pressure curves), v1 carries position only.
   */
  @Serializable
  data class FreehandInkPayload(
    val stroke: List<PdfPoint>,
    val thicknessPt: Float = 1.0f,
  ) : AnnotationPayload()

  /**
   * Anchor point + sticky-note text. The bottom-sheet editor in
   * `PdfAnnotationOverlay` opens on tap of the pin.
   */
  @Serializable
  data class StickyNotePayload(
    val anchor: PdfPoint,
    val text: String,
  ) : AnnotationPayload()

  /**
   * Image stamp. [imageRef] points at a file under app private
   * storage (`<files>/stamps/<uuid>.png`). Phase H consumes this same
   * payload for signature placement (visual stamp branch).
   */
  @Serializable
  data class StampPayload(
    val imageRef: String,
    val rect: PdfRect,
  ) : AnnotationPayload()
}

/** PDF user-space point (origin bottom-left, units = points). */
@Serializable
data class PdfPoint(val x: Float, val y: Float)

/** PDF user-space rectangle (LLx, LLy, URx, URy). */
@Serializable
data class PdfRect(val left: Float, val bottom: Float, val right: Float, val top: Float) {
  val width: Float get() = right - left
  val height: Float get() = top - bottom
}
