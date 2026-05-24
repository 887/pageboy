package com.eight87.pageboy.format.pdf.signing

import com.lowagie.text.Image
import com.lowagie.text.pdf.PdfReader
import com.lowagie.text.pdf.PdfStamper
import java.io.InputStream
import java.io.OutputStream

/**
 * Phase H.4 — visual-signature stamp burn-in. The "Save signed PDF"
 * overflow entry calls this with the captured signature PNG + the
 * target page + the rectangle the user tap-to-placed.
 *
 * **Two flows, one tool.** This is the trivial path — no cryptography,
 * just rendering an image annotation into the PDF at a specified
 * rectangle so other readers (Adobe Reader, Preview, browser PDF
 * viewers) see the stamp without any plugin. The cryptographic path
 * is [PadesSigner].
 *
 * **Why OpenPDF's [PdfStamper.getOverContent] (and not
 * [PdfStamper.addAnnotation]).** Adding via the page's content stream
 * (over-content) bakes the bitmap into the PDF's render path so every
 * PDF reader shows it identically; annotation dictionaries are
 * format-correct but historically have been honoured inconsistently
 * across the lighter-weight viewers (browser viewers, file-manager
 * preview, etc.). The over-content path is the canonical
 * "rubber-stamp" approach.
 *
 * Coordinate system: PDF user-space, origin at the bottom-left of the
 * page, units = 1/72 inch. The caller resolves the on-screen tap
 * coordinates against the rendered page transform — see Phase G's
 * coordinate matrix (when Phase G merges) or the simple
 * 0..1-normalised rect surface the signing sheet uses in v1.
 */
class PdfStampBurnIn {

  /**
   * Burn the [pngBytes] stamp into [pageIndex] (1-based per OpenPDF
   * convention) of [input], writing the result to [output].
   *
   * @param rectInPoints (llx, lly, urx, ury) in PDF user-space points.
   *   The image is scaled to fit. Origin bottom-left.
   */
  fun burn(
    input: InputStream,
    output: OutputStream,
    pngBytes: ByteArray,
    pageIndex: Int,
    rectInPoints: Rect,
  ) {
    val reader = PdfReader(input)
    val stamper = PdfStamper(reader, output)
    val img = Image.getInstance(pngBytes)
    val w = rectInPoints.urx - rectInPoints.llx
    val h = rectInPoints.ury - rectInPoints.lly
    img.scaleAbsolute(w, h)
    img.setAbsolutePosition(rectInPoints.llx, rectInPoints.lly)
    stamper.getOverContent(pageIndex).addImage(img)
    stamper.close()
    reader.close()
  }

  /** Tiny inline rect type so callers don't pull `android.graphics.RectF` into JVM tests. */
  data class Rect(val llx: Float, val lly: Float, val urx: Float, val ury: Float)
}
