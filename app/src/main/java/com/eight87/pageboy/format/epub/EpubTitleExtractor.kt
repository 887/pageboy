package com.eight87.pageboy.format.epub

import org.readium.r2.shared.publication.Publication

/**
 * Phase M.3 — pure helper that resolves the display title for an open
 * [Publication]. EPUB metadata's `dc:title` lives at
 * `publication.metadata.title` (Readium normalises EPUB 2 + 3 to the
 * same field).
 *
 * Returns `null` when the publication declared no title or only a
 * blank one — the caller falls back to the SAF filename. Stays a free
 * function (R.X.1 narrow — no class state needed, deterministic
 * mapping).
 */
internal object EpubTitleExtractor {

  fun titleFor(publication: Publication): String? {
    val raw = publication.metadata.title ?: return null
    return raw.trim().ifEmpty { null }
  }
}
