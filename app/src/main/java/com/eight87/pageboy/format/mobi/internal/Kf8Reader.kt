package com.eight87.pageboy.format.mobi.internal

/**
 * Phase Q — KF8 / AZW3 section reader.
 *
 * A KF8 ("MOBI 8") section is a second PalmDB-style segment within the
 * same outer container, starting at the record index recorded in the
 * MOBI 6 header's KF8 boundary field. Combo files use this for backward
 * compatibility; pure AZW3 files have a degenerate single-segment
 * layout.
 *
 * For v1 we extract the KF8 body the same way we extract MOBI 6 body:
 *  - The KF8 header is itself a PalmDOC + Mobipocket header (record 0
 *    of the KF8 segment) using its own compression scheme.
 *  - Body records following the KF8 header decompress under PalmDOC
 *    (mode 2) — the dominant in-the-wild case for DRM-free KF8/AZW3.
 *  - Mode 17480 (HUFF/CDIC) inside KF8 raises
 *    `MobiParseError.UnsupportedCompression` exactly like in MOBI 6.
 *
 * KF8-specific markup walking (HTML5 + CSS3 inlined as one big
 * payload) is the WebView's job, not ours. We just hand the decoded
 * bytes back as a `String` for the renderer to feed to WebView.
 *
 * Stays under 200 LOC per R.X.4.
 */
internal object Kf8Reader {

  /**
   * Read the KF8 segment from [container] starting at [boundaryIndex].
   * Returns the decoded HTML5 body. Throws [MobiParseException] for
   * compression modes outside `{1, 2}` or for malformed shapes.
   *
   * The MOBI 6 outer container's record table indexes into the SAME
   * byte buffer for KF8 — the segment's records are the outer
   * records `[boundaryIndex .. recordCount)`. Record 0 of the segment
   * is `boundaryIndex`; body records start at `boundaryIndex + 1`.
   */
  fun read(container: PalmDbContainer, boundaryIndex: Int): String {
    if (boundaryIndex < 0 || boundaryIndex >= container.recordCount) {
      throw MobiParseException(
        MobiParseError.MalformedContainer("KF8 boundary $boundaryIndex out of range"),
      )
    }

    // The KF8 segment header has the same Mobipocket-header shape as
    // MOBI 6 record 0. Reuse the reader by synthesising a child
    // container view rooted at `boundaryIndex`.
    val childOffsets = IntArray(container.recordCount - boundaryIndex)
    for (i in childOffsets.indices) {
      childOffsets[i] = container.recordOffsets[boundaryIndex + i]
    }
    val childContainer = PalmDbContainer(
      typeCode = container.typeCode,
      creatorCode = container.creatorCode,
      recordOffsets = childOffsets,
      bytes = container.bytes,
    )
    val header = MobipocketHeaderReader.read(childContainer)
    val mode = MobiCompressionMode.fromInt(header.compressionModeRaw)
      ?: throw MobiParseException(
        MobiParseError.UnsupportedCompression(header.compressionModeRaw),
      )

    val bodyRecordCount = header.bodyRecordCount.coerceAtLeast(0)
      .coerceAtMost(childContainer.recordCount - 1)
    if (bodyRecordCount <= 0) {
      throw MobiParseException(MobiParseError.EmptyContent)
    }

    val rawRecords = ArrayList<ByteArray>(bodyRecordCount)
    for (i in 1..bodyRecordCount) {
      rawRecords.add(childContainer.recordBytes(i))
    }

    val decompressed = when (mode) {
      MobiCompressionMode.Uncompressed -> concat(rawRecords)
      MobiCompressionMode.PalmDoc -> PalmDocDecompressor.decompressAll(rawRecords)
    }
    if (decompressed.isEmpty()) {
      throw MobiParseException(MobiParseError.EmptyContent)
    }
    return String(decompressed, java.nio.charset.Charset.forName(header.charsetName))
  }

  private fun concat(records: List<ByteArray>): ByteArray {
    val total = records.sumOf { it.size }
    val out = ByteArray(total)
    var pos = 0
    for (r in records) {
      System.arraycopy(r, 0, out, pos, r.size)
      pos += r.size
    }
    return out
  }
}
