package com.eight87.pageboy.format.mobi.internal

/**
 * Phase Q — sealed parse-error taxonomy for [MobiParser].
 *
 * Per R.X.2: branching on parse failure happens through an exhaustive
 * `when (error)` against this sealed hierarchy, not via string-matching
 * an exception message. The reader chrome's error state picks a copy
 * variant per concrete subtype.
 *
 * The parser throws [MobiParseException] wrapping one of these values
 * when it cannot produce a [MobiBookContent]; the chrome catches at the
 * `DefaultReaderStateProjector` boundary and projects to
 * `ReaderState.Failed`.
 */
sealed interface MobiParseError {

  /**
   * EXTH record type 401 (`drm_server_id`) was present in the
   * Mobipocket header, meaning the body bytes are encrypted with
   * Amazon's proprietary MobiDRM scheme. pageboy does not attempt
   * decryption — see docs/plans/format-mobi.md "DRM detection".
   */
  data object DrmDetected : MobiParseError

  /**
   * PalmDOC record-compression mode was something other than `1`
   * (uncompressed) or `2` (PalmDOC LZ77). The most common observed
   * value is `17480` (HUFF/CDIC), which v1 explicitly does not ship —
   * see Q.5 in format-mobi.md for the v1.x deferral.
   */
  data class UnsupportedCompression(val mode: Int) : MobiParseError

  /**
   * Container-level shape violation: the PalmDB record offset table
   * pointed past EOF, the Mobipocket header's declared length was
   * negative, etc. [reason] is plain English suitable for the reader
   * chrome's error state, not user-facing copy.
   */
  data class MalformedContainer(val reason: String) : MobiParseError

  /**
   * Parser succeeded structurally but produced no text records — an
   * empty MOBI file, or a file where every body record decompressed
   * to zero bytes.
   */
  data object EmptyContent : MobiParseError
}

/**
 * Thin checked-style wrapper so the parser can `throw` a sealed error
 * without losing the discriminator. Caught at the chrome boundary; the
 * chrome's error state dispatches on `error` via exhaustive `when`.
 */
class MobiParseException(val error: MobiParseError) : RuntimeException(describe(error)) {
  private companion object {
    fun describe(error: MobiParseError): String = when (error) {
      MobiParseError.DrmDetected -> "MOBI file is DRM-protected"
      is MobiParseError.UnsupportedCompression -> "MOBI compression mode ${error.mode} not supported"
      is MobiParseError.MalformedContainer -> "Malformed MOBI container: ${error.reason}"
      MobiParseError.EmptyContent -> "MOBI file has no readable content"
    }
  }
}
