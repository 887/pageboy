package com.eight87.pageboy.format.mobi

import com.eight87.pageboy.format.mobi.internal.MobipocketHeaderReader
import com.eight87.pageboy.format.mobi.internal.PalmDbReader

/**
 * Phase Q — cheap title probe for the library scanner.
 *
 * The scanner needs a readable display title without a full body
 * decompress. We read the PalmDB envelope + record 0 only — the
 * Mobipocket header already carries the title (either via the
 * `fullName` field at offsets 96..103 of record 0, or via EXTH record
 * type 503 in some variants). Skipping body record decompression
 * keeps this O(small) — only the first record is touched.
 *
 * Returns `null` to mean "fall back to filename-derived title". DRM-
 * detected files also return `null` here (the scanner shouldn't fail
 * the library refresh just because one file is encrypted — the user
 * sees the DRM error message on open instead).
 */
internal object MobiTitleExtractor {

  fun extractFrom(bytes: ByteArray): String? = runCatching {
    val container = PalmDbReader.read(bytes)
    if (!container.isMobiFamily()) return@runCatching null
    val header = MobipocketHeaderReader.read(container)
    header.title?.takeIf { it.isNotBlank() }
  }.getOrNull()
}
