package com.eight87.pageboy.format.pdf

import com.eight87.pageboy.data.annotation.PdfPoint
import com.eight87.pageboy.data.annotation.PdfRect
import com.eight87.pageboy.format.pdf.internal.PdfCoordinates
import com.eight87.pageboy.format.pdf.internal.ScreenPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase G.7 — pure JVM unit test for [PdfCoordinates].
 *
 * Asserts the four canonical PDF rotations (0/90/180/270) all map
 * cleanly, that round-trips through `screenPointToPdf` →
 * `pdfPointToScreen` come back to the source point (within float
 * epsilon), and that the helpers respect non-square display rects.
 */
class PdfCoordinatesTest {

  private val eps = 0.001f

  @Test
  fun `rotation 0 — origin bottom-left maps to screen top-left of a square page`() {
    val s = PdfCoordinates.pdfPointToScreen(
      point = PdfPoint(0f, 612f), // top-left in PDF space (origin BL)
      pageWidthPt = 612f, pageHeightPt = 612f,
      displayWidthPx = 1000f, displayHeightPx = 1000f,
      rotationDegrees = 0,
    )
    assertEquals(0f, s.x, eps)
    assertEquals(0f, s.y, eps)
  }

  @Test
  fun `rotation 0 — bottom-right of page maps to screen bottom-right`() {
    val s = PdfCoordinates.pdfPointToScreen(
      point = PdfPoint(612f, 0f),
      pageWidthPt = 612f, pageHeightPt = 612f,
      displayWidthPx = 1000f, displayHeightPx = 1000f,
    )
    assertEquals(1000f, s.x, eps)
    assertEquals(1000f, s.y, eps)
  }

  @Test
  fun `rotation 90 — PDF top-left maps to screen top-right`() {
    val s = PdfCoordinates.pdfPointToScreen(
      point = PdfPoint(0f, 100f), // top-left
      pageWidthPt = 100f, pageHeightPt = 100f,
      displayWidthPx = 1000f, displayHeightPx = 1000f,
      rotationDegrees = 90,
    )
    assertEquals(1000f, s.x, eps)
    assertEquals(0f, s.y, eps)
  }

  @Test
  fun `rotation 180 — PDF top-left maps to screen bottom-right`() {
    val s = PdfCoordinates.pdfPointToScreen(
      point = PdfPoint(0f, 100f),
      pageWidthPt = 100f, pageHeightPt = 100f,
      displayWidthPx = 1000f, displayHeightPx = 1000f,
      rotationDegrees = 180,
    )
    assertEquals(1000f, s.x, eps)
    assertEquals(1000f, s.y, eps)
  }

  @Test
  fun `rotation 270 — PDF top-left maps to screen bottom-left`() {
    val s = PdfCoordinates.pdfPointToScreen(
      point = PdfPoint(0f, 100f),
      pageWidthPt = 100f, pageHeightPt = 100f,
      displayWidthPx = 1000f, displayHeightPx = 1000f,
      rotationDegrees = 270,
    )
    assertEquals(0f, s.x, eps)
    assertEquals(1000f, s.y, eps)
  }

  @Test
  fun `non-square display rect scales x and y independently`() {
    val s = PdfCoordinates.pdfPointToScreen(
      point = PdfPoint(50f, 50f), // dead-centre
      pageWidthPt = 100f, pageHeightPt = 100f,
      displayWidthPx = 200f, displayHeightPx = 400f,
    )
    assertEquals(100f, s.x, eps)
    assertEquals(200f, s.y, eps)
  }

  @Test
  fun `pdfRectToScreen returns axis-aligned bounding box`() {
    val sRect = PdfCoordinates.pdfRectToScreen(
      rect = PdfRect(left = 0f, bottom = 0f, right = 50f, top = 50f),
      pageWidthPt = 100f, pageHeightPt = 100f,
      displayWidthPx = 200f, displayHeightPx = 200f,
    )
    assertEquals(0f, sRect.left, eps)
    assertEquals(100f, sRect.top, eps)
    assertEquals(100f, sRect.right, eps)
    assertEquals(200f, sRect.bottom, eps)
  }

  @Test
  fun `roundtrip through screenPointToPdf and pdfPointToScreen`() {
    val original = PdfPoint(123.4f, 234.5f)
    val screen = PdfCoordinates.pdfPointToScreen(
      original,
      pageWidthPt = 612f, pageHeightPt = 792f,
      displayWidthPx = 1080f, displayHeightPx = 1920f,
    )
    val back = PdfCoordinates.screenPointToPdf(
      screen,
      pageWidthPt = 612f, pageHeightPt = 792f,
      displayWidthPx = 1080f, displayHeightPx = 1920f,
    )
    assertEquals(original.x, back.x, 0.1f)
    assertEquals(original.y, back.y, 0.1f)
  }

  @Test
  fun `roundtrip survives rotation 90`() {
    val original = PdfPoint(100f, 200f)
    val screen = PdfCoordinates.pdfPointToScreen(
      original,
      pageWidthPt = 612f, pageHeightPt = 792f,
      displayWidthPx = 792f, displayHeightPx = 612f,
      rotationDegrees = 90,
    )
    val back = PdfCoordinates.screenPointToPdf(
      screen,
      pageWidthPt = 612f, pageHeightPt = 792f,
      displayWidthPx = 792f, displayHeightPx = 612f,
      rotationDegrees = 90,
    )
    assertEquals(original.x, back.x, 0.5f)
    assertEquals(original.y, back.y, 0.5f)
  }

  @Test
  fun `roundtrip survives rotation 270`() {
    val original = PdfPoint(300f, 400f)
    val screen = PdfCoordinates.pdfPointToScreen(
      original,
      pageWidthPt = 612f, pageHeightPt = 792f,
      displayWidthPx = 792f, displayHeightPx = 612f,
      rotationDegrees = 270,
    )
    val back = PdfCoordinates.screenPointToPdf(
      screen,
      pageWidthPt = 612f, pageHeightPt = 792f,
      displayWidthPx = 792f, displayHeightPx = 612f,
      rotationDegrees = 270,
    )
    assertEquals(original.x, back.x, 0.5f)
    assertEquals(original.y, back.y, 0.5f)
  }

  @Test
  fun `normalizeRotation folds negative and large values`() {
    assertEquals(0, PdfCoordinates.normalizeRotation(0))
    assertEquals(90, PdfCoordinates.normalizeRotation(90))
    assertEquals(180, PdfCoordinates.normalizeRotation(180))
    assertEquals(270, PdfCoordinates.normalizeRotation(270))
    assertEquals(0, PdfCoordinates.normalizeRotation(360))
    assertEquals(90, PdfCoordinates.normalizeRotation(450))
    assertEquals(270, PdfCoordinates.normalizeRotation(-90))
    assertEquals(0, PdfCoordinates.normalizeRotation(-720))
  }

  @Test
  fun `screenPointToPdf clamps out-of-bounds taps to the page rect`() {
    val pdf = PdfCoordinates.screenPointToPdf(
      ScreenPoint(-100f, -100f),
      pageWidthPt = 100f, pageHeightPt = 100f,
      displayWidthPx = 1000f, displayHeightPx = 1000f,
    )
    // Clamped to (0, 100) in PDF space (top-left).
    assertTrue(pdf.x >= 0f && pdf.x <= 100f)
    assertTrue(pdf.y >= 0f && pdf.y <= 100f)
  }
}
