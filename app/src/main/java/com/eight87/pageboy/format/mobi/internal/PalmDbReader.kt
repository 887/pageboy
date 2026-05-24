package com.eight87.pageboy.format.mobi.internal

/**
 * Phase Q — PalmDB container reader.
 *
 * MOBI / KF8 / AZW / AZW3 / PRC all sit inside a Palm Database (PDB)
 * container. The PDB header is a fixed 78-byte preamble followed by
 * `numRecords` * 8-byte record-info entries, followed by the record
 * payload bytes the entries point at.
 *
 * This reader is pure-stdlib, JVM-only — no Android types, no streams,
 * just a byte array. The body of a MOBI file routinely fits in tens of
 * MB; we load the whole thing into memory rather than streaming because
 * the Mobipocket header points back into the same byte range for image
 * record extraction, and seeking through a `ContentResolver` input
 * stream multiple times is more expensive than the memory cost.
 *
 * Per R.X.4 stays under 300 LOC; per R.X.1 narrow interface — exposes
 * exactly the record-byte slicing the upstream parser needs.
 */
internal object PalmDbReader {

  private const val HEADER_SIZE = 78
  private const val TYPE_OFFSET = 60
  private const val CREATOR_OFFSET = 64
  private const val NUM_RECORDS_OFFSET = 76
  private const val RECORD_INFO_ENTRY_SIZE = 8

  /**
   * Parse the PalmDB container envelope. Returns a [PalmDbContainer]
   * describing the record offsets and surfacing the raw bytes for
   * downstream record extraction. Throws [MobiParseException] wrapping
   * a [MobiParseError.MalformedContainer] when the container is
   * structurally invalid.
   */
  fun read(bytes: ByteArray): PalmDbContainer {
    if (bytes.size < HEADER_SIZE) {
      throw MobiParseException(MobiParseError.MalformedContainer("file shorter than PalmDB header"))
    }
    val type = asciiAt(bytes, TYPE_OFFSET, 4)
    val creator = asciiAt(bytes, CREATOR_OFFSET, 4)
    val numRecords = readUShort(bytes, NUM_RECORDS_OFFSET)
    if (numRecords <= 0) {
      throw MobiParseException(MobiParseError.MalformedContainer("PalmDB has zero records"))
    }
    val recordInfoEnd = HEADER_SIZE + numRecords * RECORD_INFO_ENTRY_SIZE
    if (recordInfoEnd > bytes.size) {
      throw MobiParseException(MobiParseError.MalformedContainer("record-info table truncated"))
    }

    val offsets = IntArray(numRecords)
    for (i in 0 until numRecords) {
      val base = HEADER_SIZE + i * RECORD_INFO_ENTRY_SIZE
      val offset = readInt(bytes, base)
      if (offset < 0 || offset > bytes.size) {
        throw MobiParseException(
          MobiParseError.MalformedContainer("record $i offset $offset past EOF"),
        )
      }
      offsets[i] = offset
    }
    // Records are stored contiguously and we slice each one as
    // `bytes[offsets[i] until offsets[i+1]]`; the last record runs to
    // EOF. PalmDB doesn't store per-record lengths.
    for (i in 0 until numRecords - 1) {
      if (offsets[i + 1] < offsets[i]) {
        throw MobiParseException(
          MobiParseError.MalformedContainer("record $i offsets out of order"),
        )
      }
    }

    return PalmDbContainer(
      typeCode = type,
      creatorCode = creator,
      recordOffsets = offsets,
      bytes = bytes,
    )
  }

  private fun asciiAt(buf: ByteArray, start: Int, len: Int): String {
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
}

/**
 * Parsed PalmDB envelope: the type/creator codes, the record offsets
 * (one per record), and the raw byte buffer the offsets index into.
 *
 * `recordOffsets[i]` is the start of record `i`; `recordEnd(i)` returns
 * the end (offset of the next record or EOF for the last one).
 */
internal data class PalmDbContainer(
  val typeCode: String,
  val creatorCode: String,
  val recordOffsets: IntArray,
  val bytes: ByteArray,
) {
  val recordCount: Int get() = recordOffsets.size

  /** Inclusive start, exclusive end. */
  fun recordRange(index: Int): IntRange {
    val start = recordOffsets[index]
    val end = if (index + 1 < recordOffsets.size) recordOffsets[index + 1] else bytes.size
    return start until end
  }

  /** Materialise record bytes as a copy. Cheap for small records. */
  fun recordBytes(index: Int): ByteArray {
    val range = recordRange(index)
    return bytes.copyOfRange(range.first, range.last + 1)
  }

  /** True when this PalmDB looks like a MOBI/AZW/PRC container. */
  fun isMobiFamily(): Boolean =
    (typeCode == "BOOK" || typeCode == "TEXt") && creatorCode == "MOBI"

  // Generated equals/hashCode would do array comparison; we don't
  // compare PalmDbContainers in production paths but the autogen
  // works for tests using the same input bytes.
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is PalmDbContainer) return false
    return typeCode == other.typeCode &&
      creatorCode == other.creatorCode &&
      recordOffsets.contentEquals(other.recordOffsets) &&
      bytes.contentEquals(other.bytes)
  }

  override fun hashCode(): Int {
    var result = typeCode.hashCode()
    result = 31 * result + creatorCode.hashCode()
    result = 31 * result + recordOffsets.contentHashCode()
    result = 31 * result + bytes.contentHashCode()
    return result
  }
}
