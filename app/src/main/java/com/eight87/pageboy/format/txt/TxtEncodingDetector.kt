package com.eight87.pageboy.format.txt

import java.nio.charset.Charset
import java.nio.charset.CharsetDecoder
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/**
 * Phase E.4 — encoding detection for the plain-text renderer. Pure
 * JVM; no Android dependencies; no third-party library (per
 * `docs/plans/format-txt.md`'s license-gate decision against
 * juniversalchardet (MPL-1.1, not on allowlist) and ICU4J (~12 MB, blows
 * the < 50 KB TXT format budget)).
 *
 * Strategy mirrors what ripgrep does by default:
 *
 *  1. **BOM sniff** — UTF-8 BOM `EF BB BF`, UTF-16-LE BOM `FF FE`,
 *     UTF-16-BE BOM `FE FF`, UTF-32 BOMs `FF FE 00 00` / `00 00 FE FF`.
 *     Detects ~30% of real-world non-UTF-8 files for free; the BOM is
 *     stripped from the resulting [DetectedEncoding] so callers don't
 *     need to handle it again.
 *  2. **UTF-8 trial decode** on the head sample with
 *     `CodingErrorAction.REPORT`. Clean decode → UTF-8.
 *  3. **UTF-16 heuristic** — without a BOM, if every other byte in the
 *     head is `0x00`, treat as UTF-16-LE. Rare in 2026 but caught for
 *     ~5 LOC. (`docs/plans/format-txt.md` G1.)
 *  4. **Windows-1252 fallback** — the codepage 95% of legacy ASCII-ish
 *     English / Western European text actually is. Always succeeds.
 *
 * Returns a [DetectedEncoding] carrying the resolved [Charset] + the
 * [Source] tag so the UI can show "detected: cp1252 — switch to…" if
 * later phases expose an override affordance.
 */
object TxtEncodingDetector {

  /** Maximum byte count fed to the detector. ~64 KiB per format-txt.md G1. */
  const val HEAD_SAMPLE_BYTES = 65_536

  /** Where the detection came from — surfaceable as user-facing diagnostics. */
  enum class Source { Bom, Utf8Trial, Utf16Heuristic, Cp1252Fallback }

  data class DetectedEncoding(
    val charset: Charset,
    val source: Source,
    /** Byte count of any BOM that should be skipped when decoding the body. */
    val bomLength: Int = 0,
  )

  private val WINDOWS_1252: Charset by lazy { Charset.forName("windows-1252") }

  /**
   * Detect the encoding of a [head] byte sample. Callers should feed at
   * least [HEAD_SAMPLE_BYTES] bytes when available; smaller samples
   * still produce a sensible verdict but UTF-8 trial-decode becomes
   * less informative.
   */
  fun detect(head: ByteArray): DetectedEncoding {
    sniffBom(head)?.let { return it }
    if (head.isEmpty()) {
      return DetectedEncoding(StandardCharsets.UTF_8, Source.Utf8Trial, bomLength = 0)
    }
    // UTF-16 heuristic runs BEFORE the UTF-8 trial: ASCII-in-UTF-16 looks
    // like alternating `XX 00 XX 00` bytes, which decode "cleanly" as
    // UTF-8 (each `00` is a valid NUL codepoint) but produces an
    // unreadable mojibake-string. Catching the high-zero-byte-density
    // case first avoids that.
    if (looksLikeUtf16Le(head)) {
      return DetectedEncoding(StandardCharsets.UTF_16LE, Source.Utf16Heuristic, bomLength = 0)
    }
    if (looksLikeUtf16Be(head)) {
      return DetectedEncoding(StandardCharsets.UTF_16BE, Source.Utf16Heuristic, bomLength = 0)
    }
    if (decodesCleanlyAsUtf8(head)) {
      return DetectedEncoding(StandardCharsets.UTF_8, Source.Utf8Trial, bomLength = 0)
    }
    return DetectedEncoding(WINDOWS_1252, Source.Cp1252Fallback, bomLength = 0)
  }

  private fun sniffBom(head: ByteArray): DetectedEncoding? {
    if (head.size >= 4 && head[0] == 0xFF.toByte() && head[1] == 0xFE.toByte() &&
      head[2] == 0x00.toByte() && head[3] == 0x00.toByte()
    ) return DetectedEncoding(Charset.forName("UTF-32LE"), Source.Bom, bomLength = 4)
    if (head.size >= 4 && head[0] == 0x00.toByte() && head[1] == 0x00.toByte() &&
      head[2] == 0xFE.toByte() && head[3] == 0xFF.toByte()
    ) return DetectedEncoding(Charset.forName("UTF-32BE"), Source.Bom, bomLength = 4)
    if (head.size >= 3 && head[0] == 0xEF.toByte() && head[1] == 0xBB.toByte() &&
      head[2] == 0xBF.toByte()
    ) return DetectedEncoding(StandardCharsets.UTF_8, Source.Bom, bomLength = 3)
    if (head.size >= 2 && head[0] == 0xFF.toByte() && head[1] == 0xFE.toByte()
    ) return DetectedEncoding(StandardCharsets.UTF_16LE, Source.Bom, bomLength = 2)
    if (head.size >= 2 && head[0] == 0xFE.toByte() && head[1] == 0xFF.toByte()
    ) return DetectedEncoding(StandardCharsets.UTF_16BE, Source.Bom, bomLength = 2)
    return null
  }

  private fun decodesCleanlyAsUtf8(head: ByteArray): Boolean {
    val decoder: CharsetDecoder = StandardCharsets.UTF_8.newDecoder()
      .onMalformedInput(CodingErrorAction.REPORT)
      .onUnmappableCharacter(CodingErrorAction.REPORT)
    return runCatching { decoder.decode(java.nio.ByteBuffer.wrap(head)); true }
      .getOrDefault(false)
  }

  /** Heuristic: at least half of the even-indexed bytes are zero. */
  private fun looksLikeUtf16Le(head: ByteArray): Boolean {
    if (head.size < 16) return false
    var zeroOdd = 0
    var oddCount = 0
    var i = 1
    while (i < head.size) {
      oddCount++
      if (head[i] == 0x00.toByte()) zeroOdd++
      i += 2
    }
    return oddCount > 0 && zeroOdd.toDouble() / oddCount >= 0.5
  }

  /** Mirror of [looksLikeUtf16Le] for the BE flavour. */
  private fun looksLikeUtf16Be(head: ByteArray): Boolean {
    if (head.size < 16) return false
    var zeroEven = 0
    var evenCount = 0
    var i = 0
    while (i < head.size) {
      evenCount++
      if (head[i] == 0x00.toByte()) zeroEven++
      i += 2
    }
    return evenCount > 0 && zeroEven.toDouble() / evenCount >= 0.5
  }
}
