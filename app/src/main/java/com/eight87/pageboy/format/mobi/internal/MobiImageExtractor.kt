package com.eight87.pageboy.format.mobi.internal

/**
 * Phase Q — MOBI inline-image extractor.
 *
 * Image records sit at PalmDB indices `[firstImageRecordIndex ..)` and
 * end at either the KF8 boundary (combo files) or the end of the
 * container (MOBI 6 only). We only surface records whose first few
 * bytes carry a recognised image-magic (JPEG / PNG / GIF) so we don't
 * mis-classify the FDST / FCIS / FLIS / SRCS auxiliary records that
 * also live in the image-record range.
 *
 * Each surfaced image gets a stable id derived from its 1-based offset
 * inside the image range (matches Mobipocket's `<img recindex="N">`
 * convention so the WebViewClient resolver can look up by recindex).
 *
 * Stays under 150 LOC per R.X.4.
 */
internal object MobiImageExtractor {

  /**
   * Extract every image record from [container] given the header's
   * declared image range. Returns a map keyed by Mobipocket recindex
   * (1-based, the same id the WebView's `pageboy://mobi/<id>` URLs
   * carry).
   *
   * The map values are the raw image bytes (JPEG / PNG / GIF — the
   * formats Mobipocket spec'd). Empty when no images are present.
   */
  fun extract(
    container: PalmDbContainer,
    header: MobipocketHeader,
  ): Map<String, MobiImage> {
    val first = header.firstImageRecordIndex ?: return emptyMap()
    if (first < 0 || first >= container.recordCount) return emptyMap()
    val end = header.kf8BoundaryRecordIndex ?: container.recordCount
    val safeEnd = end.coerceAtMost(container.recordCount)
    if (safeEnd <= first) return emptyMap()
    val out = LinkedHashMap<String, MobiImage>()
    for (idx in first until safeEnd) {
      val bytes = container.recordBytes(idx)
      val mime = sniffImageMime(bytes) ?: continue
      val recindex = idx - first + 1
      out[recindex.toString()] = MobiImage(recindex = recindex, mime = mime, bytes = bytes)
    }
    return out
  }

  /**
   * Match an image-format magic header. Returns the canonical
   * mime-type the WebView's `WebResourceResponse` should advertise,
   * or `null` if the record's bytes don't look like any recognised
   * image format (in which case it's probably an FDST/FCIS aux
   * record).
   */
  private fun sniffImageMime(bytes: ByteArray): String? {
    if (bytes.size < 4) return null
    // JPEG: FF D8 FF
    if (bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte()) {
      return "image/jpeg"
    }
    // PNG: 89 50 4E 47 0D 0A 1A 0A
    if (bytes.size >= 8 &&
      bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() &&
      bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte() &&
      bytes[4] == 0x0D.toByte() && bytes[5] == 0x0A.toByte() &&
      bytes[6] == 0x1A.toByte() && bytes[7] == 0x0A.toByte()
    ) {
      return "image/png"
    }
    // GIF: "GIF8"
    if (bytes[0] == 'G'.code.toByte() && bytes[1] == 'I'.code.toByte() &&
      bytes[2] == 'F'.code.toByte() && bytes[3] == '8'.code.toByte()
    ) {
      return "image/gif"
    }
    return null
  }
}

/**
 * One Mobipocket image record. [recindex] is the 1-based index inside
 * the image-record range — the WebView's `pageboy://mobi/<recindex>`
 * URLs resolve through here.
 */
internal data class MobiImage(
  val recindex: Int,
  val mime: String,
  val bytes: ByteArray,
) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is MobiImage) return false
    return recindex == other.recindex && mime == other.mime && bytes.contentEquals(other.bytes)
  }

  override fun hashCode(): Int {
    var result = recindex
    result = 31 * result + mime.hashCode()
    result = 31 * result + bytes.contentHashCode()
    return result
  }
}
