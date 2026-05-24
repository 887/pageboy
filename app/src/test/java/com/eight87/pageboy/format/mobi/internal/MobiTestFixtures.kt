package com.eight87.pageboy.format.mobi.internal

import java.io.ByteArrayOutputStream

/**
 * Phase Q test fixtures — synth MOBI byte arrays the parser-internals
 * tests feed into [PalmDbReader] / [MobipocketHeaderReader] /
 * [PalmDocDecompressor] / [MobiImageExtractor].
 *
 * Kept under `test/` (not `main/`) so the bytes-builder never ships
 * in release. Lives next to the tests that use it.
 */
internal object MobiTestFixtures {

  /**
   * Build a complete PalmDB envelope from a list of record-payload
   * byte arrays. Returns bytes ready to feed into [PalmDbReader.read]:
   *  - 78-byte PalmDB header (database name `TestBook`, type `BOOK`,
   *    creator `MOBI`, numRecords = records.size).
   *  - `records.size` * 8-byte record-info entries with computed
   *    offsets.
   *  - Concatenated record payloads.
   *
   * The 2-byte gap PalmDB convention sticks between the record-info
   * table and the first record is omitted (the parser doesn't care).
   */
  fun palmDb(
    records: List<ByteArray>,
    typeCode: String = "BOOK",
    creatorCode: String = "MOBI",
  ): ByteArray {
    val out = ByteArrayOutputStream()
    val headerSize = 78
    val recordInfoSize = records.size * 8
    val firstRecordOffset = headerSize + recordInfoSize

    // Header.
    val header = ByteArray(headerSize)
    "TestBook".toByteArray(Charsets.US_ASCII).copyInto(header, destinationOffset = 0)
    typeCode.toByteArray(Charsets.US_ASCII).copyInto(header, destinationOffset = 60)
    creatorCode.toByteArray(Charsets.US_ASCII).copyInto(header, destinationOffset = 64)
    writeUShort(header, 76, records.size)
    out.write(header)

    // Record-info table.
    var cursor = firstRecordOffset
    for (r in records) {
      val entry = ByteArray(8)
      writeInt(entry, 0, cursor)
      // bytes 4..7 — flags + unique-id, parser ignores.
      out.write(entry)
      cursor += r.size
    }
    // Payloads.
    for (r in records) out.write(r)
    return out.toByteArray()
  }

  /**
   * Build a minimal valid Mobipocket record-0 header (PalmDOC +
   * Mobipocket) with the given compression mode, body record count,
   * and optional EXTH records.
   *
   * The header is intentionally minimal — just enough fields filled
   * in for the parser to walk it. Real-world headers have 200+ bytes
   * of additional fields the parser doesn't read.
   */
  fun mobiRecord0(
    compressionMode: Int = MobiCompressionMode.PALMDOC,
    bodyRecordCount: Int = 1,
    title: String? = null,
    exth: Map<Int, ByteArray> = emptyMap(),
    kf8BoundaryRecord: Int? = null,
    mobiType: Int = 2,
    textEncoding: Int = 65001,
  ): ByteArray {
    val titleBytes = title?.toByteArray(Charsets.UTF_8) ?: ByteArray(0)
    val exthBytes = if (exth.isNotEmpty()) buildExth(exth) else ByteArray(0)

    // PalmDOC header (16 bytes).
    val palmDoc = ByteArray(16)
    writeUShort(palmDoc, 0, compressionMode)
    writeUShort(palmDoc, 8, bodyRecordCount)
    writeUShort(palmDoc, 10, 4096) // record size

    // Mobipocket header begins at offset 16 of record 0.
    val mobiHeaderLen = 232
    val mobiHeader = ByteArray(mobiHeaderLen)
    "MOBI".toByteArray(Charsets.US_ASCII).copyInto(mobiHeader, 0)
    writeInt(mobiHeader, 4, mobiHeaderLen) // header length
    writeInt(mobiHeader, 8, mobiType)
    writeInt(mobiHeader, 12, textEncoding)
    // first image record index — offset 84 of record 0 = 84-16 = 68.
    writeInt(mobiHeader, 68, -1) // 0xFFFFFFFF (no images)
    // Title sits AFTER the EXTH bytes (the parser reads it via the
    // fullNameOffset, which is in record-0 byte coordinates).
    val titleOffsetInRecord = 16 + mobiHeaderLen + exthBytes.size
    val fullNameOffset = if (titleBytes.isNotEmpty()) titleOffsetInRecord else 0
    // full-name offset + length (offsets 96/100 of record 0 = 80/84
    // of mobi header).
    writeInt(mobiHeader, 80, fullNameOffset)
    writeInt(mobiHeader, 84, titleBytes.size)
    // KF8 boundary — offset 108 of record 0 = 92 of mobi header.
    writeInt(mobiHeader, 92, kf8BoundaryRecord ?: -1)
    // EXTH flag (offset 128 of record 0 = 112 of mobi header).
    if (exth.isNotEmpty()) {
      writeInt(mobiHeader, 112, 0x40)
    }

    val out = ByteArrayOutputStream()
    out.write(palmDoc)
    out.write(mobiHeader)
    if (exthBytes.isNotEmpty()) out.write(exthBytes)
    if (titleBytes.isNotEmpty()) out.write(titleBytes)
    return out.toByteArray()
  }

  /**
   * Build an EXTH section: 4-byte magic "EXTH", 4-byte header length,
   * 4-byte record count, then per-record (4-byte type, 4-byte total
   * record size, payload).
   */
  fun buildExth(records: Map<Int, ByteArray>): ByteArray {
    val out = ByteArrayOutputStream()
    val perRecordTotalLen = records.entries.sumOf { 8 + it.value.size }
    val headerLen = 12 + perRecordTotalLen
    out.write("EXTH".toByteArray(Charsets.US_ASCII))
    val headerLenBytes = ByteArray(4); writeInt(headerLenBytes, 0, headerLen); out.write(headerLenBytes)
    val countBytes = ByteArray(4); writeInt(countBytes, 0, records.size); out.write(countBytes)
    for ((type, payload) in records) {
      val typeBytes = ByteArray(4); writeInt(typeBytes, 0, type); out.write(typeBytes)
      val lenBytes = ByteArray(4); writeInt(lenBytes, 0, 8 + payload.size); out.write(lenBytes)
      out.write(payload)
    }
    return out.toByteArray()
  }

  private fun writeUShort(buf: ByteArray, offset: Int, value: Int) {
    buf[offset] = ((value ushr 8) and 0xFF).toByte()
    buf[offset + 1] = (value and 0xFF).toByte()
  }

  private fun writeInt(buf: ByteArray, offset: Int, value: Int) {
    buf[offset] = ((value ushr 24) and 0xFF).toByte()
    buf[offset + 1] = ((value ushr 16) and 0xFF).toByte()
    buf[offset + 2] = ((value ushr 8) and 0xFF).toByte()
    buf[offset + 3] = (value and 0xFF).toByte()
  }
}
