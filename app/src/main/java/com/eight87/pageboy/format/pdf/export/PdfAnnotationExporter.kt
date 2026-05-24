package com.eight87.pageboy.format.pdf.export

import com.eight87.pageboy.data.annotation.AnnotationEntity
import com.eight87.pageboy.data.annotation.AnnotationKind
import com.eight87.pageboy.data.annotation.AnnotationPayload
import com.lowagie.text.Rectangle
import com.lowagie.text.pdf.PdfAnnotation
import com.lowagie.text.pdf.PdfArray
import com.lowagie.text.pdf.PdfName
import com.lowagie.text.pdf.PdfNumber
import com.lowagie.text.pdf.PdfReader
import com.lowagie.text.pdf.PdfStamper
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream

/**
 * Phase G.6 — bake [AnnotationEntity] rows into a copy of the source
 * PDF as proper `/Annot` dictionaries via OpenPDF (MPL-2.0).
 *
 * Source PDF is never mutated; the exporter writes to the caller's
 * `OutputStream` (typically a SAF "save as" target acquired via
 * `ACTION_CREATE_DOCUMENT`).
 *
 * Per-kind dispatch maps onto OpenPDF's `PdfAnnotation` factories:
 *  - Highlight  → `createMarkup(... MARKUP_HIGHLIGHT, quadPoints)`
 *  - Underline  → `createMarkup(... MARKUP_UNDERLINE, quadPoints)`
 *  - Strike     → `createMarkup(... MARKUP_STRIKEOUT, quadPoints)`
 *  - Freehand   → `createInk(rect, contents, gestures)`
 *  - StickyNote → `createText(rect, title, contents, open, icon)`
 *  - Stamp      → `createStamp(rect, contents, name)`
 *
 * Color goes through the `/C` array (PDF 1.7 §12.5.5) — we set it
 * directly via `put(PdfName.C, PdfArray)` so we never touch
 * `java.awt.Color` (Android doesn't ship the AWT color class; the
 * OpenPDF `setColor(Color)` overload would fail at runtime on
 * non-desugared APKs).
 *
 * v1 simplifications:
 *  - Ink strokes use the source point list as a single `/Gestures`
 *    sub-array (one stroke per annotation row).
 *  - Stamp annotations use a placeholder rect; the actual image
 *    embedding via `PdfTemplate` lands in Phase H (signature stamps).
 *  - PageIndex maps directly to OpenPDF's 1-based page parameter;
 *    rows with `pageIndex >= reader.numberOfPages` are skipped (the
 *    document has been edited externally since the annotation landed).
 *
 * R.X.1 — narrow: takes a JSON decoder + the source bytes + the
 * annotation list. No `Context`, no `AnnotationStore`. The caller (the
 * "Export with annotations…" menu entry) reads the rows out of
 * `AnnotationSource.list(documentId)` itself.
 */
class PdfAnnotationExporter(
  private val json: Json = DefaultJson,
) {

  /**
   * Bake [annotations] into a copy of the source PDF; write to [output].
   *
   * Caller owns both streams' lifecycle. The exporter does NOT close
   * either — the caller's `use { ... }` block does.
   *
   * @return the number of annotations actually written (≤ `annotations.size`;
   *         skipped rows include those past the last page + those
   *         whose payload fails to decode).
   */
  fun export(
    source: InputStream,
    output: OutputStream,
    annotations: List<AnnotationEntity>,
  ): Int {
    val reader = PdfReader(source)
    val stamper = PdfStamper(reader, output)
    var written = 0
    try {
      val pageCount = reader.numberOfPages
      for (entity in annotations) {
        val openPdfPage = entity.pageIndex + 1 // OpenPDF is 1-based.
        if (openPdfPage < 1 || openPdfPage > pageCount) continue
        val annotation = buildPdfAnnotation(stamper, entity) ?: continue
        stamper.addAnnotation(annotation, openPdfPage)
        written++
      }
    } finally {
      stamper.close()
      reader.close()
    }
    return written
  }

  private fun buildPdfAnnotation(
    stamper: PdfStamper,
    entity: AnnotationEntity,
  ): PdfAnnotation? {
    val kind = runCatching { AnnotationKind.valueOf(entity.kind) }.getOrNull() ?: return null
    val payload = runCatching {
      json.decodeFromString<AnnotationPayload>(entity.payloadJson)
    }.getOrNull() ?: return null
    val writer = stamper.writer
    val annotation: PdfAnnotation = when (kind) {
      AnnotationKind.Highlight -> {
        val p = payload as? AnnotationPayload.HighlightPayload ?: return null
        val rect = p.rect.toRectangle()
        val quad = quadPointsFromRect(p.rect)
        PdfAnnotation.createMarkup(writer, rect, "", PdfAnnotation.MARKUP_HIGHLIGHT, quad)
      }
      AnnotationKind.Underline -> {
        val p = payload as? AnnotationPayload.UnderlinePayload ?: return null
        val rect = p.rect.toRectangle()
        val quad = quadPointsFromRect(p.rect)
        PdfAnnotation.createMarkup(writer, rect, "", PdfAnnotation.MARKUP_UNDERLINE, quad)
      }
      AnnotationKind.Strikethrough -> {
        val p = payload as? AnnotationPayload.StrikethroughPayload ?: return null
        val rect = p.rect.toRectangle()
        val quad = quadPointsFromRect(p.rect)
        PdfAnnotation.createMarkup(writer, rect, "", PdfAnnotation.MARKUP_STRIKEOUT, quad)
      }
      AnnotationKind.FreehandInk -> {
        val p = payload as? AnnotationPayload.FreehandInkPayload ?: return null
        if (p.stroke.isEmpty()) return null
        // Bounding-box rect (one extra pt of padding to give the
        // viewer a non-degenerate frame).
        val xs = p.stroke.map { it.x }
        val ys = p.stroke.map { it.y }
        val rect = Rectangle(xs.min() - 1f, ys.min() - 1f, xs.max() + 1f, ys.max() + 1f)
        val gestures = Array(1) {
          FloatArray(p.stroke.size * 2).also { out ->
            p.stroke.forEachIndexed { i, point ->
              out[i * 2] = point.x
              out[i * 2 + 1] = point.y
            }
          }
        }
        PdfAnnotation.createInk(writer, rect, "", gestures)
      }
      AnnotationKind.StickyNote -> {
        val p = payload as? AnnotationPayload.StickyNotePayload ?: return null
        val rect = Rectangle(p.anchor.x, p.anchor.y, p.anchor.x + 18f, p.anchor.y + 18f)
        PdfAnnotation.createText(writer, rect, "Note", p.text, /* open = */ false, /* icon = */ "Note")
      }
      AnnotationKind.Stamp -> {
        val p = payload as? AnnotationPayload.StampPayload ?: return null
        val rect = p.rect.toRectangle()
        PdfAnnotation.createStamp(writer, rect, "", "Approved")
      }
    }
    annotation.applyColor(entity.colorArgb)
    return annotation
  }

  /**
   * Apply ARGB-packed color via the `/C` array. Three-element RGB
   * (0.0-1.0) per PDF 1.7 §12.5.5; alpha is honoured on highlights by
   * default via the highlight color's intensity (OpenPDF doesn't write
   * `/CA` from a high-level setter, but Adobe Reader renders highlight
   * markups with their built-in 50% alpha anyway).
   */
  private fun PdfAnnotation.applyColor(argb: Int) {
    val r = ((argb shr 16) and 0xFF) / 255f
    val g = ((argb shr 8) and 0xFF) / 255f
    val b = (argb and 0xFF) / 255f
    val arr = PdfArray()
    arr.add(PdfNumber(r))
    arr.add(PdfNumber(g))
    arr.add(PdfNumber(b))
    put(PdfName.C, arr)
  }

  private fun com.eight87.pageboy.data.annotation.PdfRect.toRectangle(): Rectangle =
    Rectangle(left, bottom, right, top)

  /**
   * One quadrilateral for a single rectangle: bottom-left,
   * bottom-right, top-left, top-right in user-space (PDF 1.7 §12.5.6.10
   * — note the order is _not_ the obvious clockwise; it's the
   * documented "two top corners then two bottom corners" pattern that
   * Adobe Reader expects for markup annotations).
   */
  private fun quadPointsFromRect(r: com.eight87.pageboy.data.annotation.PdfRect): FloatArray =
    floatArrayOf(
      r.left, r.top,    // top-left
      r.right, r.top,   // top-right
      r.left, r.bottom, // bottom-left
      r.right, r.bottom, // bottom-right
    )

  companion object {
    val DefaultJson: Json = Json {
      ignoreUnknownKeys = true
      classDiscriminator = "kind"
    }
  }
}
