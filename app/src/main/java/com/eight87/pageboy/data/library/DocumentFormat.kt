package com.eight87.pageboy.data.library

/**
 * Phase B.4 — the closed set of document formats pageboy recognises.
 *
 * Stored on every [DocumentEntity] row so the library UI can colour-code,
 * filter, and route to the right per-format renderer. New formats land as
 * new enum cases (open/closed); the reader dispatch is an exhaustive
 * `when` so the compiler flags every site that has to grow.
 *
 * `Unknown` is the safety-net bucket — files whose magic header didn't
 * match anything and whose extension also wasn't a known marker. The
 * scanner still records them (so the user can see the surface they
 * pointed at) but they're not routable to a renderer in Phase C+.
 */
enum class DocumentFormat {
  Markdown,
  Txt,
  Pdf,
  Epub,
  Docx,
  Xlsx,
  Odt,
  Ods,
  Unknown,
  ;

  companion object {
    /** Stable string id used for persistence (Room TypeConverter). */
    fun id(format: DocumentFormat): String = format.name

    fun fromId(id: String): DocumentFormat = entries.firstOrNull { it.name == id } ?: Unknown
  }
}
