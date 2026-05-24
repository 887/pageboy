package com.eight87.pageboy.data.library

/**
 * Phase B.10 — pure functions that take the full document catalog +
 * filter state and produce the list the LibraryScreen actually renders.
 *
 * Kept module-level (no class state) so [LibraryFilterSortSearchTest]
 * can exercise the logic without any Android dependency. The
 * LibraryScreen pipes the catalog Flow through these via `remember`
 * blocks keyed on the inputs (cold-start-perf pattern from the family
 * playbook: filter in compose, not in the DAO query, so chip flicks are
 * cost-free).
 */
object LibraryFilters {

  /**
   * Tab filter — pre-step before format / collection / search / sort.
   * "All" returns every scanned-library doc; "Started" is the same
   * subset with `lastReadPositionMs > 0`; "Recents" is
   * `lastOpenedAt != null` across both library + ad-hoc rows (caller
   * separately sorts + caps); "Pinned" is `pinned = true` across both.
   *
   * Phase N.5 / locked decision #4 — Started / All hide ad-hoc
   * `AdHocOpen` rows so the open-with ingest doesn't pollute the
   * scanned-library tabs. Recents + Pinned surface ad-hoc rows so the
   * user can find a recently-opened share or re-open a pinned ad-hoc
   * document.
   */
  fun byTab(docs: List<DocumentEntity>, tab: LibraryTab): List<DocumentEntity> = when (tab) {
    LibraryTab.All -> docs.filter { it.toSourceKind() is DocumentSourceKind.LibraryRoot }
    LibraryTab.Started -> docs.filter {
      it.lastReadPositionMs > 0L && it.toSourceKind() is DocumentSourceKind.LibraryRoot
    }
    LibraryTab.Recents -> docs.filter { it.lastOpenedAt != null }
    LibraryTab.Pinned -> docs.filter { it.pinned }
  }

  fun byFormat(docs: List<DocumentEntity>, formats: Set<DocumentFormat>): List<DocumentEntity> {
    if (formats.isEmpty()) return docs
    val allowed = formats.map { DocumentFormat.id(it) }.toSet()
    return docs.filter { it.format in allowed }
  }

  fun byCollection(docs: List<DocumentEntity>, collections: Set<String>): List<DocumentEntity> {
    if (collections.isEmpty()) return docs
    return docs.filter { it.collection in collections }
  }

  fun bySearch(docs: List<DocumentEntity>, query: String): List<DocumentEntity> {
    val q = query.trim()
    if (q.isEmpty()) return docs
    val needle = q.lowercase()
    return docs.filter { doc ->
      doc.title.lowercase().contains(needle) ||
        doc.fileName.lowercase().contains(needle) ||
        (doc.collection?.lowercase()?.contains(needle) == true)
    }
  }

  fun sorted(docs: List<DocumentEntity>, sortKey: LibrarySortKey): List<DocumentEntity> =
    when (sortKey) {
      LibrarySortKey.TitleAsc -> docs.sortedBy { it.title.lowercase() }
      LibrarySortKey.TitleDesc -> docs.sortedByDescending { it.title.lowercase() }
      LibrarySortKey.DateAdded -> docs.sortedByDescending { it.addedAt }
      LibrarySortKey.LastOpened -> docs.sortedByDescending { it.lastOpenedAt ?: 0L }
      LibrarySortKey.Format -> docs.sortedWith(
        compareBy<DocumentEntity> { it.format }.thenBy { it.title.lowercase() },
      )
    }

  /**
   * One-shot composition of every filter step except the tab — used by
   * Started / All / Pinned tabs. The Recents tab uses a DAO query that
   * already handles ordering + capping so it skips the sort step.
   */
  fun apply(
    docs: List<DocumentEntity>,
    formats: Set<DocumentFormat>,
    collections: Set<String>,
    search: String,
    sortKey: LibrarySortKey,
  ): List<DocumentEntity> {
    val a = byFormat(docs, formats)
    val b = byCollection(a, collections)
    val c = bySearch(b, search)
    return sorted(c, sortKey)
  }
}
