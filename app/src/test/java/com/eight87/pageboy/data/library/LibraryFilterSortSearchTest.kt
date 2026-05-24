package com.eight87.pageboy.data.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase B.16 — pure JVM test for [LibraryFilters]. Covers tab filter,
 * format filter, collection filter, search substring match, sort order.
 */
class LibraryFilterSortSearchTest {

  private fun doc(
    id: String,
    title: String,
    format: DocumentFormat = DocumentFormat.Pdf,
    collection: String? = null,
    fileName: String = "$title.pdf",
    pinned: Boolean = false,
    lastReadPositionMs: Long = 0L,
    lastOpenedAt: Long? = null,
    addedAt: Long = 0L,
  ) = DocumentEntity(
    documentId = id,
    treeUriString = "content://tree/$id",
    relativePath = fileName,
    documentUriString = "content://doc/$id",
    title = title,
    fileName = fileName,
    format = DocumentFormat.id(format),
    sizeBytes = 1L,
    mtimeMs = 0L,
    collection = collection,
    addedAt = addedAt,
    lastOpenedAt = lastOpenedAt,
    lastReadPositionMs = lastReadPositionMs,
    readFraction = 0f,
    pinned = pinned,
  )

  private val catalog = listOf(
    doc("a", "Apple", format = DocumentFormat.Pdf, collection = "Fiction", lastReadPositionMs = 0L),
    doc("b", "Banana", format = DocumentFormat.Epub, collection = "Fiction", lastReadPositionMs = 5L, lastOpenedAt = 100L, pinned = true),
    doc("c", "Cherry", format = DocumentFormat.Markdown, collection = "Notes"),
    doc("d", "Date", format = DocumentFormat.Pdf, collection = "Manuals", addedAt = 999L),
    doc("e", "Eggplant", format = DocumentFormat.Txt, collection = null),
  )

  @Test
  fun `Started tab returns only documents with non-zero read position`() {
    val result = LibraryFilters.byTab(catalog, LibraryTab.Started)
    assertEquals(listOf("b"), result.map { it.documentId })
  }

  @Test
  fun `Pinned tab returns only pinned documents`() {
    val result = LibraryFilters.byTab(catalog, LibraryTab.Pinned)
    assertEquals(listOf("b"), result.map { it.documentId })
  }

  @Test
  fun `Recents tab returns only documents with non-null last-opened`() {
    val result = LibraryFilters.byTab(catalog, LibraryTab.Recents)
    assertEquals(listOf("b"), result.map { it.documentId })
  }

  @Test
  fun `All tab returns every document`() {
    val result = LibraryFilters.byTab(catalog, LibraryTab.All)
    assertEquals(catalog.size, result.size)
  }

  @Test
  fun `format filter narrows to selected formats only`() {
    val result = LibraryFilters.byFormat(catalog, setOf(DocumentFormat.Pdf, DocumentFormat.Epub))
    assertEquals(setOf("a", "b", "d"), result.map { it.documentId }.toSet())
  }

  @Test
  fun `empty format filter is a no-op`() {
    val result = LibraryFilters.byFormat(catalog, emptySet())
    assertEquals(catalog.size, result.size)
  }

  @Test
  fun `collection filter narrows to selected collections`() {
    val result = LibraryFilters.byCollection(catalog, setOf("Fiction"))
    assertEquals(setOf("a", "b"), result.map { it.documentId }.toSet())
  }

  @Test
  fun `search matches title case-insensitively`() {
    val result = LibraryFilters.bySearch(catalog, "ban")
    assertEquals(listOf("b"), result.map { it.documentId })
  }

  @Test
  fun `search matches collection name`() {
    val result = LibraryFilters.bySearch(catalog, "fiction")
    assertEquals(setOf("a", "b"), result.map { it.documentId }.toSet())
  }

  @Test
  fun `empty search returns full input`() {
    val result = LibraryFilters.bySearch(catalog, "  ")
    assertEquals(catalog.size, result.size)
  }

  @Test
  fun `TitleAsc sorts case-insensitively ascending`() {
    val result = LibraryFilters.sorted(catalog, LibrarySortKey.TitleAsc)
    assertEquals(
      listOf("Apple", "Banana", "Cherry", "Date", "Eggplant"),
      result.map { it.title },
    )
  }

  @Test
  fun `TitleDesc sorts case-insensitively descending`() {
    val result = LibraryFilters.sorted(catalog, LibrarySortKey.TitleDesc)
    assertEquals(
      listOf("Eggplant", "Date", "Cherry", "Banana", "Apple"),
      result.map { it.title },
    )
  }

  @Test
  fun `DateAdded sort orders most recent first`() {
    val result = LibraryFilters.sorted(catalog, LibrarySortKey.DateAdded)
    assertEquals("d", result.first().documentId)
  }

  @Test
  fun `LastOpened sort puts opened documents first`() {
    val result = LibraryFilters.sorted(catalog, LibrarySortKey.LastOpened)
    assertEquals("b", result.first().documentId)
  }

  @Test
  fun `Format sort groups by format then title`() {
    val result = LibraryFilters.sorted(catalog, LibrarySortKey.Format)
    // Format enum order: Markdown, Txt, Pdf, Epub, Docx, Xlsx, Odt, Ods, Unknown.
    // Within format, title asc.
    val byFormat = result.groupBy { it.format }
    // Markdown comes before Pdf.
    val markdownIdx = result.indexOfFirst { it.format == DocumentFormat.id(DocumentFormat.Markdown) }
    val pdfIdx = result.indexOfFirst { it.format == DocumentFormat.id(DocumentFormat.Pdf) }
    assertTrue("markdown $markdownIdx should precede pdf $pdfIdx", markdownIdx < pdfIdx)
    assertEquals(catalog.size, byFormat.values.sumOf { it.size })
  }

  @Test
  fun `apply composes format collection search and sort`() {
    val result = LibraryFilters.apply(
      docs = catalog,
      formats = setOf(DocumentFormat.Pdf),
      collections = emptySet(),
      search = "",
      sortKey = LibrarySortKey.TitleAsc,
    )
    assertEquals(listOf("Apple", "Date"), result.map { it.title })
  }
}
