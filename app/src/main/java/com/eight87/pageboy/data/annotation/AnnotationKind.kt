package com.eight87.pageboy.data.annotation

/**
 * Phase G.1 — closed sealed enum of annotation kinds pageboy supports
 * in v1. Each kind has a per-kind JSON payload shape (see
 * [com.eight87.pageboy.data.annotation.AnnotationPayload]).
 *
 * Listed in the order they appear in the annotation toolbar (Phase G.4).
 * Adding a kind = adding an enum case + a payload-shape branch in
 * [AnnotationPayload] + a renderer branch in `PdfAnnotationOverlay` +
 * an exporter branch in `PdfAnnotationExporter`. No `if (kind == ...)`
 * chains outside those dispatch sites (R.X.2 sealed dispatch).
 */
enum class AnnotationKind {
  /** Translucent rect over text quadPoints. */
  Highlight,

  /** Line at the bottom edge of quadPoints. */
  Underline,

  /** Line through the midline of quadPoints. */
  Strikethrough,

  /** Freehand ink strokes (one stroke = list of points). */
  FreehandInk,

  /** Tap-anchored pin + comment text. */
  StickyNote,

  /** Saved bitmap dropped at a rectangle (signature etc.). */
  Stamp,
}
