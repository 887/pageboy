package com.eight87.pageboy.format.pdf

import com.eight87.pageboy.data.annotation.AnnotationEntity
import com.eight87.pageboy.data.annotation.AnnotationKind
import com.eight87.pageboy.data.annotation.AnnotationPayload
import com.eight87.pageboy.data.annotation.PdfPoint
import com.eight87.pageboy.data.annotation.PdfRect
import com.eight87.pageboy.format.pdf.export.PdfAnnotationExporter
import com.lowagie.text.Document
import com.lowagie.text.PageSize
import com.lowagie.text.Paragraph
import com.lowagie.text.pdf.PdfReader
import com.lowagie.text.pdf.PdfWriter
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * Phase G.7 — JVM unit test for [PdfAnnotationExporter].
 *
 * Builds a minimal in-memory PDF via OpenPDF itself, runs each
 * annotation kind through the exporter, then re-parses the output
 * with [PdfReader] and asserts the per-page `/Annots` arrays carry
 * the right subtypes (`/Highlight`, `/Underline`, `/StrikeOut`,
 * `/Ink`, `/Text`, `/Stamp`).
 *
 * No Robolectric — exporter is pure JVM (OpenPDF + kotlinx-serialization).
 */
class PdfAnnotationExporterTest {

  private val json = Json { ignoreUnknownKeys = true; classDiscriminator = "kind" }
  private val exporter = PdfAnnotationExporter(json)

  /** Build a tiny 1-page PDF in memory and return its bytes. */
  private fun smallSourcePdf(pages: Int = 1): ByteArray {
    val out = ByteArrayOutputStream()
    val doc = Document(PageSize.LETTER)
    PdfWriter.getInstance(doc, out)
    doc.open()
    repeat(pages) { i ->
      doc.add(Paragraph("Page ${i + 1} content"))
      if (i < pages - 1) doc.newPage()
    }
    doc.close()
    return out.toByteArray()
  }

  private fun annotation(
    id: String,
    kind: AnnotationKind,
    payload: AnnotationPayload,
    page: Int = 0,
  ): AnnotationEntity {
    val payloadJson = json.encodeToString(AnnotationPayload.serializer(), payload)
    return AnnotationEntity(
      id = id,
      documentId = "doc",
      pageIndex = page,
      kind = kind.name,
      payloadJson = payloadJson,
      colorArgb = 0xFFFFEB3B.toInt(),
      pageWidthPt = 612f,
      pageHeightPt = 792f,
      createdAt = 1L,
      modifiedAt = 1L,
    )
  }

  @Test
  fun `export writes a non-empty PDF when annotations is empty`() {
    val source = smallSourcePdf()
    val output = ByteArrayOutputStream()
    val written = exporter.export(ByteArrayInputStream(source), output, emptyList())
    assertEquals(0, written)
    assertTrue(output.size() > 0)
    // Output is a valid PDF: parses with PdfReader.
    val reader = PdfReader(output.toByteArray())
    assertEquals(1, reader.numberOfPages)
    reader.close()
  }

  @Test
  fun `export writes highlight as a Highlight markup annotation`() {
    val source = smallSourcePdf()
    val output = ByteArrayOutputStream()
    val a = annotation(
      "a", AnnotationKind.Highlight,
      AnnotationPayload.HighlightPayload(PdfRect(50f, 700f, 200f, 720f)),
    )
    val written = exporter.export(ByteArrayInputStream(source), output, listOf(a))
    assertEquals(1, written)
    assertAnnotationSubtypeOnPage1(output.toByteArray(), "Highlight")
  }

  @Test
  fun `export writes underline as Underline markup`() {
    val source = smallSourcePdf()
    val output = ByteArrayOutputStream()
    val a = annotation(
      "a", AnnotationKind.Underline,
      AnnotationPayload.UnderlinePayload(PdfRect(50f, 700f, 200f, 720f)),
    )
    exporter.export(ByteArrayInputStream(source), output, listOf(a))
    assertAnnotationSubtypeOnPage1(output.toByteArray(), "Underline")
  }

  @Test
  fun `export writes strike as StrikeOut markup`() {
    val source = smallSourcePdf()
    val output = ByteArrayOutputStream()
    val a = annotation(
      "a", AnnotationKind.Strikethrough,
      AnnotationPayload.StrikethroughPayload(PdfRect(50f, 700f, 200f, 720f)),
    )
    exporter.export(ByteArrayInputStream(source), output, listOf(a))
    assertAnnotationSubtypeOnPage1(output.toByteArray(), "StrikeOut")
  }

  @Test
  fun `export writes ink stroke as Ink annotation`() {
    val source = smallSourcePdf()
    val output = ByteArrayOutputStream()
    val a = annotation(
      "a", AnnotationKind.FreehandInk,
      AnnotationPayload.FreehandInkPayload(
        stroke = listOf(PdfPoint(100f, 700f), PdfPoint(150f, 720f), PdfPoint(200f, 700f)),
        thicknessPt = 2f,
      ),
    )
    exporter.export(ByteArrayInputStream(source), output, listOf(a))
    assertAnnotationSubtypeOnPage1(output.toByteArray(), "Ink")
  }

  @Test
  fun `export writes sticky note as Text annotation`() {
    val source = smallSourcePdf()
    val output = ByteArrayOutputStream()
    val a = annotation(
      "a", AnnotationKind.StickyNote,
      AnnotationPayload.StickyNotePayload(anchor = PdfPoint(100f, 700f), text = "hello"),
    )
    exporter.export(ByteArrayInputStream(source), output, listOf(a))
    assertAnnotationSubtypeOnPage1(output.toByteArray(), "Text")
  }

  @Test
  fun `export writes stamp as Stamp annotation`() {
    val source = smallSourcePdf()
    val output = ByteArrayOutputStream()
    val a = annotation(
      "a", AnnotationKind.Stamp,
      AnnotationPayload.StampPayload(imageRef = "", rect = PdfRect(100f, 700f, 200f, 800f)),
    )
    exporter.export(ByteArrayInputStream(source), output, listOf(a))
    assertAnnotationSubtypeOnPage1(output.toByteArray(), "Stamp")
  }

  @Test
  fun `export skips annotations past last page`() {
    val source = smallSourcePdf(pages = 1)
    val output = ByteArrayOutputStream()
    val a = annotation(
      "a", AnnotationKind.Highlight,
      AnnotationPayload.HighlightPayload(PdfRect(0f, 0f, 1f, 1f)),
      page = 5, // past the only page
    )
    val written = exporter.export(ByteArrayInputStream(source), output, listOf(a))
    assertEquals(0, written)
  }

  @Test
  fun `export writes per-page count correctly across multiple pages`() {
    val source = smallSourcePdf(pages = 3)
    val output = ByteArrayOutputStream()
    val annotations = listOf(
      annotation("a", AnnotationKind.Highlight,
        AnnotationPayload.HighlightPayload(PdfRect(50f, 700f, 200f, 720f)), page = 0),
      annotation("b", AnnotationKind.Highlight,
        AnnotationPayload.HighlightPayload(PdfRect(50f, 700f, 200f, 720f)), page = 2),
    )
    val written = exporter.export(ByteArrayInputStream(source), output, annotations)
    assertEquals(2, written)

    val reader = PdfReader(output.toByteArray())
    val page1Annots = reader.getPageN(1).getAsArray(com.lowagie.text.pdf.PdfName.ANNOTS)
    val page3Annots = reader.getPageN(3).getAsArray(com.lowagie.text.pdf.PdfName.ANNOTS)
    assertNotNull(page1Annots)
    assertNotNull(page3Annots)
    assertEquals(1, page1Annots.size())
    assertEquals(1, page3Annots.size())
    reader.close()
  }

  @Test
  fun `export survives malformed payload JSON without crashing`() {
    val source = smallSourcePdf()
    val output = ByteArrayOutputStream()
    val a = AnnotationEntity(
      id = "a", documentId = "doc", pageIndex = 0,
      kind = AnnotationKind.Highlight.name,
      payloadJson = "{not json",
      colorArgb = 0,
      pageWidthPt = 612f, pageHeightPt = 792f,
      createdAt = 1L, modifiedAt = 1L,
    )
    val written = exporter.export(ByteArrayInputStream(source), output, listOf(a))
    assertEquals(0, written)
  }

  @Test
  fun `applied color writes a 3-element C array`() {
    val source = smallSourcePdf()
    val output = ByteArrayOutputStream()
    val a = annotation(
      "a", AnnotationKind.Highlight,
      AnnotationPayload.HighlightPayload(PdfRect(50f, 700f, 200f, 720f)),
    )
    exporter.export(ByteArrayInputStream(source), output, listOf(a))
    val reader = PdfReader(output.toByteArray())
    val annots = reader.getPageN(1).getAsArray(com.lowagie.text.pdf.PdfName.ANNOTS)
    val annot = annots.getAsDict(0)
    val c = annot.getAsArray(com.lowagie.text.pdf.PdfName.C)
    assertNotNull(c)
    assertEquals(3, c.size())
    reader.close()
  }

  private fun assertAnnotationSubtypeOnPage1(pdfBytes: ByteArray, expectedSubtype: String) {
    val reader = PdfReader(pdfBytes)
    try {
      val page = reader.getPageN(1)
      val annots = page.getAsArray(com.lowagie.text.pdf.PdfName.ANNOTS)
      assertNotNull("/Annots array present on page 1", annots)
      assertTrue("at least one /Annot entry", annots.size() >= 1)
      val first = annots.getAsDict(0)
      val subtype = first.getAsName(com.lowagie.text.pdf.PdfName.SUBTYPE)
      assertNotNull("/Subtype present", subtype)
      assertEquals("/$expectedSubtype", subtype.toString())
    } finally {
      reader.close()
    }
  }
}
