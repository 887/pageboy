package com.eight87.pageboy.format.mobi.internal

/**
 * Phase Q — PalmDOC LZ77 decompressor (compression mode 2).
 *
 * The PalmDOC LZ77 variant is a simple sliding-window scheme:
 *  - Bytes `0x00`: literal NUL (output as-is).
 *  - Bytes `0x01..0x08`: count of following literal bytes to copy
 *    through to the output verbatim.
 *  - Bytes `0x09..0x7F`: single literal ASCII byte (output as-is).
 *  - Bytes `0x80..0xBF`: pair-code (this byte + next form a 14-bit
 *    big-endian field). The top 3 bits stay as `10x`; the low 11 bits
 *    encode `(distance << 3) | (length - 3)`. Look back `distance`
 *    bytes in the output, copy `length` bytes forward.
 *  - Bytes `0xC0..0xFF`: literal pair — output a space (0x20) then
 *    the byte's low 7 bits as an ASCII letter (`byte xor 0x80`).
 *
 * Stays under 200 LOC per R.X.4.
 */
internal object PalmDocDecompressor {

  /**
   * Decompress a single PalmDOC LZ77 record. Returns the decoded
   * bytes. Treats truncated input defensively — the parser preferring
   * "render what we have" over "crash the reader" on a slightly
   * corrupted file.
   */
  fun decompress(input: ByteArray): ByteArray {
    val output = ArrayList<Byte>(input.size * 2)
    var i = 0
    while (i < input.size) {
      val b = input[i].toInt() and 0xFF
      when {
        b == 0x00 -> {
          output.add(0)
          i++
        }
        b in 0x01..0x08 -> {
          // Next `b` bytes are literal.
          val count = b
          i++
          var copied = 0
          while (copied < count && i < input.size) {
            output.add(input[i])
            i++
            copied++
          }
        }
        b in 0x09..0x7F -> {
          output.add(b.toByte())
          i++
        }
        b in 0x80..0xBF -> {
          // Two-byte sequence: this byte + next form an LZ77 pair.
          if (i + 1 >= input.size) {
            // Truncated pair — drop and stop.
            break
          }
          val b2 = input[i + 1].toInt() and 0xFF
          val pair = ((b and 0x3F) shl 8) or b2
          val distance = pair ushr 3
          val length = (pair and 0x07) + 3
          // Copy `length` bytes from `distance` back in the output.
          if (distance <= 0 || distance > output.size) {
            // Malformed back-reference — skip the pair rather than
            // throw; the reader can still surface the rest.
            i += 2
            continue
          }
          val start = output.size - distance
          for (k in 0 until length) {
            // Reads from `output` as we append — supports
            // overlap-style RLE expansions.
            output.add(output[start + k])
          }
          i += 2
        }
        b in 0xC0..0xFF -> {
          output.add(0x20.toByte()) // space
          output.add((b xor 0x80).toByte())
          i++
        }
      }
    }
    val arr = ByteArray(output.size)
    for (k in output.indices) arr[k] = output[k]
    return arr
  }

  /**
   * Decompress an entire sequence of body records under PalmDOC LZ77.
   * Concatenates the per-record outputs into one [ByteArray]. The
   * Mobipocket spec guarantees record boundaries align with logical
   * breaks, so naive concatenation is safe.
   */
  fun decompressAll(records: List<ByteArray>): ByteArray {
    val totalGuess = records.sumOf { it.size * 2 }
    val acc = ArrayList<Byte>(totalGuess)
    for (record in records) {
      val decoded = decompress(record)
      for (b in decoded) acc.add(b)
    }
    val arr = ByteArray(acc.size)
    for (k in acc.indices) arr[k] = acc[k]
    return arr
  }

  /**
   * Closed by v1.x — HUFF/CDIC decompression (compression mode 17480)
   * requires the HUFF + CDIC dictionary records and a Huffman decode
   * pass. Out of scope for v1 per docs/plans/format-mobi.md; the
   * parser raises `MobiParseError.UnsupportedCompression(17480)` so
   * the chrome shows a friendly error rather than calling this
   * function.
   */
  @Suppress("UnusedPrivateMember")
  private fun huffCdicNotImplemented() {
    // closed by v1.x — HUFF/CDIC decompression
    throw NotImplementedError("HUFF/CDIC (mode 17480) deferred to v1.x")
  }
}
