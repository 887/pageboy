package com.eight87.pageboy.format.mobi.internal

/**
 * Phase Q — closed variant set for what kind of MOBI we're looking at.
 *
 * MOBI files come in three meaningful flavours from a parser's
 * standpoint:
 *  - **Mobi6**: only the legacy MOBI 6 PalmDOC body present. Older
 *    Mobipocket-era ebooks + early Amazon AZW are here.
 *  - **Kf8**: only the KF8 (HTML5 + CSS3) body present. Pure AZW3 from
 *    newer Kindle pipelines.
 *  - **Combo**: both bodies present in one PalmDB. Amazon's combo
 *    files so older Kindles fall back to MOBI 6 while newer ones
 *    render KF8. The parser prefers KF8.
 *
 * Sealed per R.X.2 so the parser's dispatch is exhaustive.
 */
sealed interface MobiVariant {
  data object Mobi6 : MobiVariant
  data object Kf8 : MobiVariant
  data object Combo : MobiVariant
}

/**
 * Phase Q — PalmDOC record-compression mode tag (closed set v1 ships).
 *
 * Per the PalmDOC spec, the first 2 bytes of the Mobipocket header
 * carry the compression mode for every body record:
 *  - `1` = uncompressed.
 *  - `2` = PalmDOC LZ77.
 *  - `17480` = HUFF/CDIC. Out of scope for v1; the parser raises
 *    `MobiParseError.UnsupportedCompression(17480)` so the chrome's
 *    error state can show a friendly message rather than crash.
 */
sealed interface MobiCompressionMode {
  data object Uncompressed : MobiCompressionMode
  data object PalmDoc : MobiCompressionMode

  companion object {
    /** Mode 1 — uncompressed records ship inline as-is. */
    const val UNCOMPRESSED: Int = 1

    /** Mode 2 — PalmDOC LZ77 variant. */
    const val PALMDOC: Int = 2

    /** Mode 17480 — HUFF/CDIC. Closed by v1.x; raised as unsupported. */
    const val HUFF_CDIC: Int = 17480

    fun fromInt(value: Int): MobiCompressionMode? = when (value) {
      UNCOMPRESSED -> Uncompressed
      PALMDOC -> PalmDoc
      else -> null
    }
  }
}
