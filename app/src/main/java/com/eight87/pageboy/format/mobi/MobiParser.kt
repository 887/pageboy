package com.eight87.pageboy.format.mobi

import com.eight87.pageboy.format.mobi.internal.Kf8Reader
import com.eight87.pageboy.format.mobi.internal.MobiCompressionMode
import com.eight87.pageboy.format.mobi.internal.MobiImage
import com.eight87.pageboy.format.mobi.internal.MobiImageExtractor
import com.eight87.pageboy.format.mobi.internal.MobiParseError
import com.eight87.pageboy.format.mobi.internal.MobiParseException
import com.eight87.pageboy.format.mobi.internal.MobiVariant
import com.eight87.pageboy.format.mobi.internal.MobipocketHeader
import com.eight87.pageboy.format.mobi.internal.MobipocketHeaderReader
import com.eight87.pageboy.format.mobi.internal.PalmDbContainer
import com.eight87.pageboy.format.mobi.internal.PalmDbReader
import com.eight87.pageboy.format.mobi.internal.PalmDocDecompressor

/**
 * Phase Q — MOBI parse pipeline orchestrator.
 *
 * Pipeline:
 *  1. [PalmDbReader] reads the outer container envelope.
 *  2. [MobipocketHeaderReader] reads record 0 (PalmDOC + Mobipocket
 *     headers + EXTH metadata). DRM detection happens here and short-
 *     circuits with [MobiParseError.DrmDetected].
 *  3. **KF8 dispatch:** if the header points at a KF8 boundary record,
 *     [Kf8Reader] reads the KF8 segment and we render KF8 over MOBI 6
 *     (newer Kindles' behaviour). Otherwise we decompress the MOBI 6
 *     body records via [PalmDocDecompressor].
 *  4. [MobiImageExtractor] surfaces inline image records so the
 *     WebViewClient can resolve `pageboy://mobi/<recindex>` URLs.
 *
 * Per R.X.4 stays under 200 LOC; per R.X.1 narrow surface — single
 * entry point [parse] returning a [MobiBookContent] value type. Errors
 * surface as [MobiParseException] wrapping the sealed taxonomy.
 */
internal class MobiParser {

  fun parse(bytes: ByteArray): MobiBookContent {
    val container = PalmDbReader.read(bytes)
    if (!container.isMobiFamily()) {
      throw MobiParseException(
        MobiParseError.MalformedContainer(
          "PalmDB type/creator ${container.typeCode}/${container.creatorCode} not MOBI family",
        ),
      )
    }
    val header = MobipocketHeaderReader.read(container)

    val html = when (header.variant) {
      MobiVariant.Mobi6 -> decodeMobi6Body(container, header)
      MobiVariant.Kf8 -> Kf8Reader.read(container, boundaryIndex = 0)
      MobiVariant.Combo -> {
        // Combo: prefer KF8 per format-mobi.md Q.5 spec gotchas.
        val boundary = header.kf8BoundaryRecordIndex
          ?: error("Combo variant without KF8 boundary should be impossible — header.variant inferred from boundary presence")
        runCatching { Kf8Reader.read(container, boundary) }
          // Defensive: if the KF8 segment is malformed but the MOBI 6
          // body is fine, fall back rather than failing the open.
          .getOrElse { decodeMobi6Body(container, header) }
      }
    }
    if (html.isBlank()) {
      throw MobiParseException(MobiParseError.EmptyContent)
    }
    val images = MobiImageExtractor.extract(container, header)
    val metadata = MobiMetadataExtractor.extract(header)

    return MobiBookContent(
      html = html,
      metadata = metadata,
      images = images,
      variant = header.variant,
    )
  }

  private fun decodeMobi6Body(
    container: PalmDbContainer,
    header: MobipocketHeader,
  ): String {
    val mode = MobiCompressionMode.fromInt(header.compressionModeRaw)
      ?: throw MobiParseException(
        MobiParseError.UnsupportedCompression(header.compressionModeRaw),
      )
    val bodyCount = header.bodyRecordCount.coerceAtLeast(0)
      .coerceAtMost(container.recordCount - 1)
    if (bodyCount <= 0) throw MobiParseException(MobiParseError.EmptyContent)
    val records = ArrayList<ByteArray>(bodyCount)
    for (i in 1..bodyCount) {
      records.add(container.recordBytes(i))
    }
    val decoded = when (mode) {
      MobiCompressionMode.Uncompressed -> {
        val total = records.sumOf { it.size }
        val out = ByteArray(total)
        var pos = 0
        for (r in records) {
          System.arraycopy(r, 0, out, pos, r.size)
          pos += r.size
        }
        out
      }
      MobiCompressionMode.PalmDoc -> PalmDocDecompressor.decompressAll(records)
    }
    return String(decoded, java.nio.charset.Charset.forName(header.charsetName))
  }
}

/**
 * The decoded MOBI book content the renderer feeds into WebView.
 *
 *  - [html]: the body markup ready for `WebView.loadDataWithBaseURL`.
 *  - [metadata]: EXTH-derived fields (author, publisher, etc.) for
 *    the library card.
 *  - [images]: inline images keyed by Mobipocket recindex; the
 *    WebViewClient resolves `pageboy://mobi/<recindex>` requests
 *    here.
 *  - [variant]: which MOBI flavour we extracted (for diagnostics /
 *    test assertions).
 */
internal data class MobiBookContent(
  val html: String,
  val metadata: MobiMetadata,
  val images: Map<String, MobiImage>,
  val variant: MobiVariant,
)

/**
 * EXTH-derived book metadata. All fields nullable — MOBI files in the
 * wild routinely ship with partial EXTH records.
 */
internal data class MobiMetadata(
  val title: String?,
  val author: String?,
  val publisher: String?,
  val description: String?,
  val isbn: String?,
  val subject: String?,
  val coverRecordIndex: Int?,
)

/**
 * Standalone helper to decode the EXTH metadata. Kept out of the
 * parser orchestrator so [MobiParser] stays focused on the bytes
 * pipeline.
 */
internal object MobiMetadataExtractor {
  fun extract(header: MobipocketHeader): MobiMetadata {
    val charset = java.nio.charset.Charset.forName(header.charsetName)
    val author = decodeString(header.exth[MobipocketHeaderReader.EXTH_AUTHOR], charset)
    val publisher = decodeString(header.exth[MobipocketHeaderReader.EXTH_PUBLISHER], charset)
    val description = decodeString(header.exth[MobipocketHeaderReader.EXTH_DESCRIPTION], charset)
    val isbn = decodeString(header.exth[MobipocketHeaderReader.EXTH_ISBN], charset)
    val subject = decodeString(header.exth[MobipocketHeaderReader.EXTH_SUBJECT], charset)
    val coverIndex = header.exth[MobipocketHeaderReader.EXTH_COVER_OFFSET]
      ?.takeIf { it.size >= 4 }
      ?.let { bytes ->
        val b0 = bytes[0].toInt() and 0xFF
        val b1 = bytes[1].toInt() and 0xFF
        val b2 = bytes[2].toInt() and 0xFF
        val b3 = bytes[3].toInt() and 0xFF
        (b0 shl 24) or (b1 shl 16) or (b2 shl 8) or b3
      }
    return MobiMetadata(
      title = header.title,
      author = author,
      publisher = publisher,
      description = description,
      isbn = isbn,
      subject = subject,
      coverRecordIndex = coverIndex,
    )
  }

  private fun decodeString(bytes: ByteArray?, charset: java.nio.charset.Charset): String? {
    if (bytes == null || bytes.isEmpty()) return null
    return runCatching {
      String(bytes, charset).trim().takeIf { it.isNotBlank() }
    }.getOrNull()
  }
}
