package com.eight87.pageboy.format.mobi.internal

/**
 * Phase Q — Mobipocket header reader.
 *
 * Record 0 of a MOBI PalmDB is the PalmDOC + Mobipocket header. Its
 * layout:
 *  - Bytes 0..1: compression mode (1 / 2 / 17480 — see [MobiCompressionMode]).
 *  - Bytes 4..7: uncompressed text length (big-endian, informational).
 *  - Bytes 8..9: record count covering compressed text (the count of
 *    body records that follow record 0).
 *  - Bytes 10..11: record size (typically 4096).
 *  - Bytes 16..19: "MOBI" magic (validates this is a Mobipocket file
 *    rather than a vanilla PalmDOC).
 *  - Bytes 20..23: header length.
 *  - Bytes 24..27: MOBI type (2 = Mobipocket Book).
 *  - Bytes 28..31: text encoding (1252 = CP1252, 65001 = UTF-8).
 *  - Bytes 84..87: first image record index (0xFFFFFFFF = no images).
 *  - Bytes 96..99: full-name offset (within record 0).
 *  - Bytes 100..103: full-name length (book title bytes).
 *  - Bytes 108..111: KF8 boundary record index (or 0xFFFFFFFF). Combo
 *    files use this to point at the start of the KF8 section.
 *
 * EXTH records follow the Mobipocket header when bit 6 (0x40) of the
 * EXTH-flag word at offset 128 is set. EXTH layout:
 *  - Bytes 0..3: "EXTH" magic.
 *  - Bytes 4..7: EXTH header length (informational).
 *  - Bytes 8..11: record count.
 *  - Per record: 4-byte type, 4-byte total record size, payload.
 *
 * Per R.X.4 stays under 300 LOC.
 */
internal object MobipocketHeaderReader {

  // PalmDOC header offsets within record 0.
  private const val COMPRESSION_OFFSET = 0
  private const val RECORD_COUNT_OFFSET = 8
  private const val RECORD_SIZE_OFFSET = 10

  // Mobipocket header offsets within record 0.
  private const val MOBI_MAGIC_OFFSET = 16
  private const val MOBI_HEADER_LEN_OFFSET = 20
  private const val MOBI_TYPE_OFFSET = 24
  private const val TEXT_ENCODING_OFFSET = 28
  private const val FIRST_IMAGE_INDEX_OFFSET = 84
  private const val FULL_NAME_OFFSET_OFFSET = 96
  private const val FULL_NAME_LEN_OFFSET = 100
  private const val EXTH_FLAGS_OFFSET = 128
  private const val KF8_BOUNDARY_OFFSET = 108

  // EXTH record types we surface.
  const val EXTH_AUTHOR = 100
  const val EXTH_PUBLISHER = 101
  const val EXTH_DESCRIPTION = 103
  const val EXTH_ISBN = 104
  const val EXTH_SUBJECT = 105
  const val EXTH_COVER_OFFSET = 201
  const val EXTH_DRM_SERVER_ID = 401

  private const val EXTH_FLAG_PRESENT = 0x40
  private const val EXTH_MAGIC = "EXTH"
  private const val MOBI_MAGIC = "MOBI"

  private const val ENCODING_CP1252 = 1252
  private const val ENCODING_UTF8 = 65001

  /**
   * Read record 0 from [container]. Returns a [MobipocketHeader]
   * carrying everything downstream parsing needs. Throws
   * [MobiParseException] for DRM detection or malformed headers.
   */
  fun read(container: PalmDbContainer): MobipocketHeader {
    val record0 = container.recordBytes(0)
    if (record0.size < EXTH_FLAGS_OFFSET) {
      throw MobiParseException(
        MobiParseError.MalformedContainer("record 0 shorter than Mobipocket header"),
      )
    }
    val compressionMode = readUShort(record0, COMPRESSION_OFFSET)
    val bodyRecordCount = readUShort(record0, RECORD_COUNT_OFFSET)
    val recordSize = readUShort(record0, RECORD_SIZE_OFFSET)

    val magic = asciiAt(record0, MOBI_MAGIC_OFFSET, 4)
    if (magic != MOBI_MAGIC) {
      throw MobiParseException(
        MobiParseError.MalformedContainer("not a Mobipocket header (magic=$magic)"),
      )
    }
    val headerLength = readInt(record0, MOBI_HEADER_LEN_OFFSET)
    val mobiType = readInt(record0, MOBI_TYPE_OFFSET)
    val textEncoding = readInt(record0, TEXT_ENCODING_OFFSET)
    val firstImageIndex = readInt(record0, FIRST_IMAGE_INDEX_OFFSET)
    val fullNameOffset = readInt(record0, FULL_NAME_OFFSET_OFFSET)
    val fullNameLength = readInt(record0, FULL_NAME_LEN_OFFSET)
    val exthFlags = readInt(record0, EXTH_FLAGS_OFFSET)
    val kf8BoundaryRaw = if (record0.size >= KF8_BOUNDARY_OFFSET + 4) {
      readInt(record0, KF8_BOUNDARY_OFFSET)
    } else {
      NO_INDEX_SENTINEL
    }
    val kf8Boundary = if (kf8BoundaryRaw == NO_INDEX_SENTINEL || kf8BoundaryRaw <= 0) {
      null
    } else {
      kf8BoundaryRaw
    }

    val exth = if (exthFlags and EXTH_FLAG_PRESENT != 0) {
      val exthStart = MOBI_MAGIC_OFFSET + headerLength
      readExth(record0, exthStart)
    } else {
      emptyMap()
    }

    if (exth.containsKey(EXTH_DRM_SERVER_ID)) {
      throw MobiParseException(MobiParseError.DrmDetected)
    }

    val charsetName = when (textEncoding) {
      ENCODING_UTF8 -> "UTF-8"
      ENCODING_CP1252 -> "windows-1252"
      else -> "windows-1252"
    }
    val title = decodeTitle(record0, fullNameOffset, fullNameLength, charsetName)
    val variant = inferVariant(kf8Boundary, mobiType)

    return MobipocketHeader(
      compressionModeRaw = compressionMode,
      bodyRecordCount = bodyRecordCount,
      recordSize = recordSize,
      mobiType = mobiType,
      textEncoding = textEncoding,
      charsetName = charsetName,
      firstImageRecordIndex = if (firstImageIndex == NO_INDEX_SENTINEL) null else firstImageIndex,
      kf8BoundaryRecordIndex = kf8Boundary,
      title = title,
      exth = exth,
      variant = variant,
    )
  }

  private fun inferVariant(kf8Boundary: Int?, mobiType: Int): MobiVariant {
    return when {
      kf8Boundary != null && kf8Boundary > 0 -> MobiVariant.Combo
      // mobiType 248 was Amazon's tag for KF8-only files.
      mobiType == 248 -> MobiVariant.Kf8
      else -> MobiVariant.Mobi6
    }
  }

  private fun decodeTitle(record0: ByteArray, offset: Int, length: Int, charset: String): String? {
    if (length <= 0) return null
    if (offset < 0 || offset + length > record0.size) return null
    return runCatching {
      String(record0, offset, length, java.nio.charset.Charset.forName(charset))
        .trim()
        .takeIf { it.isNotBlank() }
    }.getOrNull()
  }

  /**
   * EXTH section parser. Returns a map of EXTH-record-type → raw
   * payload bytes; consumers decode per the per-record convention.
   */
  private fun readExth(record0: ByteArray, start: Int): Map<Int, ByteArray> {
    if (start + 12 > record0.size) return emptyMap()
    val magic = asciiAt(record0, start, 4)
    if (magic != EXTH_MAGIC) return emptyMap()
    val recordCount = readInt(record0, start + 8)
    if (recordCount <= 0) return emptyMap()
    val result = LinkedHashMap<Int, ByteArray>(recordCount)
    var cursor = start + 12
    repeat(recordCount) {
      if (cursor + 8 > record0.size) return result
      val type = readInt(record0, cursor)
      val recordTotalLen = readInt(record0, cursor + 4)
      if (recordTotalLen < 8 || cursor + recordTotalLen > record0.size) return result
      val payloadLen = recordTotalLen - 8
      val payload = record0.copyOfRange(cursor + 8, cursor + 8 + payloadLen)
      result.putIfAbsent(type, payload)
      cursor += recordTotalLen
    }
    return result
  }

  private fun asciiAt(buf: ByteArray, start: Int, len: Int): String {
    if (start + len > buf.size) return ""
    val chars = CharArray(len)
    for (i in 0 until len) {
      val b = buf[start + i].toInt() and 0xFF
      chars[i] = if (b in 0x20..0x7E) b.toChar() else '?'
    }
    return String(chars)
  }

  private fun readUShort(buf: ByteArray, offset: Int): Int {
    val hi = buf[offset].toInt() and 0xFF
    val lo = buf[offset + 1].toInt() and 0xFF
    return (hi shl 8) or lo
  }

  private fun readInt(buf: ByteArray, offset: Int): Int {
    val b0 = buf[offset].toInt() and 0xFF
    val b1 = buf[offset + 1].toInt() and 0xFF
    val b2 = buf[offset + 2].toInt() and 0xFF
    val b3 = buf[offset + 3].toInt() and 0xFF
    return (b0 shl 24) or (b1 shl 16) or (b2 shl 8) or b3
  }

  /** PalmDB's "no such record" sentinel, used in several header fields. */
  private const val NO_INDEX_SENTINEL: Int = -1 // 0xFFFFFFFF read as signed Int
}

/**
 * Decoded Mobipocket-header fields the rest of the parser pipeline
 * needs. Image extraction, KF8 dispatch, title extraction all read
 * from here.
 */
internal data class MobipocketHeader(
  val compressionModeRaw: Int,
  val bodyRecordCount: Int,
  val recordSize: Int,
  val mobiType: Int,
  val textEncoding: Int,
  val charsetName: String,
  val firstImageRecordIndex: Int?,
  val kf8BoundaryRecordIndex: Int?,
  val title: String?,
  val exth: Map<Int, ByteArray>,
  val variant: MobiVariant,
)
