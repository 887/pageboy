package com.eight87.pageboy.format.pdf.internal

import com.eight87.pageboy.data.annotation.PdfPoint
import com.eight87.pageboy.data.annotation.PdfRect

/**
 * Phase G.5 — pure JVM-testable coordinate-space conversions between
 * PDF user-space (origin bottom-left, units = points = 1/72 inch) and
 * Compose pixel-space (origin top-left, units = device px) for the
 * annotation overlay.
 *
 * Pageboy persists annotations in PDF user-space (per format-pdf.md's
 * locked annotation model) so the same coords survive zoom changes,
 * rotation changes, and export-via-OpenPDF.
 *
 * Rotations follow the PDF /Rotate entry convention: 0 / 90 / 180 /
 * 270 degrees, clockwise. The display rect is the on-screen rectangle
 * the page renders into AFTER rotation (so `displayWidthPx` /
 * `displayHeightPx` already reflect the rotated dimensions).
 *
 * Two functions in v1 — point + rect; complex transformations
 * (matrix-product, perspective) deferred to v1.x.
 */
object PdfCoordinates {

  /**
   * Convert a single PDF user-space [PdfPoint] to Compose pixel-space
   * (origin top-left), respecting the page's [rotationDegrees] and
   * its rendered display rect.
   *
   * @param point        Annotation anchor in PDF user-space.
   * @param pageWidthPt  Original (un-rotated) page width in points.
   * @param pageHeightPt Original (un-rotated) page height in points.
   * @param displayWidthPx  Width of the rendered page on screen in px.
   * @param displayHeightPx Height of the rendered page on screen in px.
   * @param rotationDegrees PDF /Rotate value (0/90/180/270).
   */
  fun pdfPointToScreen(
    point: PdfPoint,
    pageWidthPt: Float,
    pageHeightPt: Float,
    displayWidthPx: Float,
    displayHeightPx: Float,
    rotationDegrees: Int = 0,
  ): ScreenPoint {
    val normalized = normalizeRotation(rotationDegrees)
    // First map PDF (bottom-left origin) to normalized [0,1] x [0,1]
    // (origin top-left to match Compose).
    val nx0 = point.x / pageWidthPt
    val ny0 = 1f - (point.y / pageHeightPt)

    // Apply rotation in normalized space. The display rect is sized
    // for the rotated dimensions, so after rotating we map straight
    // through.
    val (nx, ny) = when (normalized) {
      0 -> nx0 to ny0
      90 -> (1f - ny0) to nx0
      180 -> (1f - nx0) to (1f - ny0)
      270 -> ny0 to (1f - nx0)
      else -> nx0 to ny0
    }

    return ScreenPoint(
      x = nx * displayWidthPx,
      y = ny * displayHeightPx,
    )
  }

  /**
   * Convert a PDF user-space [PdfRect] to Compose pixel-space. Returns
   * the AXIS-ALIGNED bounding rect after rotation (sufficient for
   * highlight / underline / stamp + sticky-note pin placement; the ink
   * stroke path renders each point through [pdfPointToScreen] so
   * curved strokes survive rotation without an enclosing-rect
   * approximation).
   */
  fun pdfRectToScreen(
    rect: PdfRect,
    pageWidthPt: Float,
    pageHeightPt: Float,
    displayWidthPx: Float,
    displayHeightPx: Float,
    rotationDegrees: Int = 0,
  ): ScreenRect {
    val a = pdfPointToScreen(
      PdfPoint(rect.left, rect.bottom),
      pageWidthPt, pageHeightPt,
      displayWidthPx, displayHeightPx,
      rotationDegrees,
    )
    val b = pdfPointToScreen(
      PdfPoint(rect.right, rect.top),
      pageWidthPt, pageHeightPt,
      displayWidthPx, displayHeightPx,
      rotationDegrees,
    )
    val left = minOf(a.x, b.x)
    val top = minOf(a.y, b.y)
    val right = maxOf(a.x, b.x)
    val bottom = maxOf(a.y, b.y)
    return ScreenRect(left = left, top = top, right = right, bottom = bottom)
  }

  /**
   * Inverse of [pdfPointToScreen] — convert a Compose pixel-space tap
   * back to PDF user-space so the gesture handler can persist
   * annotations in the canonical coordinate frame.
   */
  fun screenPointToPdf(
    screen: ScreenPoint,
    pageWidthPt: Float,
    pageHeightPt: Float,
    displayWidthPx: Float,
    displayHeightPx: Float,
    rotationDegrees: Int = 0,
  ): PdfPoint {
    val normalized = normalizeRotation(rotationDegrees)
    val nx = (screen.x / displayWidthPx).coerceIn(0f, 1f)
    val ny = (screen.y / displayHeightPx).coerceIn(0f, 1f)

    // Inverse rotation: take the rotated normalized coords back to the
    // un-rotated normalized frame.
    val (nx0, ny0) = when (normalized) {
      0 -> nx to ny
      90 -> ny to (1f - nx)
      180 -> (1f - nx) to (1f - ny)
      270 -> (1f - ny) to nx
      else -> nx to ny
    }

    val pdfX = nx0 * pageWidthPt
    val pdfY = (1f - ny0) * pageHeightPt
    return PdfPoint(pdfX, pdfY)
  }

  /**
   * Normalize an arbitrary integer rotation to the canonical 0/90/180/270 set.
   * Negative values + values past 360 both fold cleanly.
   */
  fun normalizeRotation(rotationDegrees: Int): Int {
    val m = ((rotationDegrees % 360) + 360) % 360
    return when (m) {
      in 0..44 -> 0
      in 45..134 -> 90
      in 135..224 -> 180
      in 225..314 -> 270
      else -> 0
    }
  }
}

/** Pure value type — Compose pixel-space point (origin top-left). */
data class ScreenPoint(val x: Float, val y: Float)

/** Pure value type — Compose pixel-space rect (origin top-left). */
data class ScreenRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
  val width: Float get() = right - left
  val height: Float get() = bottom - top
}
