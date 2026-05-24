package com.eight87.pageboy.format.odt

import com.eight87.pageboy.format.api.DocumentBytesSource
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * Phase K — hand-rolled ODT parser. ODT is a ZIP archive of XML parts;
 * we only need `content.xml`, `styles.xml`, and `meta.xml` for v1. The
 * parser uses Android's bundled [XmlPullParser] + the JDK's
 * [ZipInputStream] — zero third-party deps (Apache ODF Toolkit was
 * rejected on APK budget; see `docs/plans/format-odt.md`).
 *
 * Two-step open:
 *   1. Inflate the relevant ZIP entries into memory (typical ODT under
 *      a few MB; we never hold the full inflater open).
 *   2. Build an [OdfStyleResolver] from `styles.xml` + automatic
 *      styles inside `content.xml`.
 *   3. Stream-walk `content.xml`'s `<office:text>` body into a list of
 *      [OdfTextBlock]s; spec-gotcha branches:
 *        - `<text:tracked-changes>` — skipped subtree (display final
 *          without markup).
 *        - `<text:change>` / `<text:change-start>` / `<text:change-end>`
 *          inline markers — consumed without rendering bubbles.
 *        - `<draw:object>` / `<draw:object-ole>` / `<chart:chart>` /
 *          `<draw:frame>` — emit [OdfTextBlock.EmbeddedPlaceholder].
 *        - Unknown elements — default-skip the subtree (never crash on
 *          unrecognised namespaces).
 *        - Unknown attributes on text:p (e.g. spreadsheet-converted
 *          docs sneaking `office:value-type` in) — silently ignored.
 *
 * R.X.1 — narrow: takes only a [DocumentBytesSource]; no `Context`, no
 * SAF, no repository. JVM-testable from `ByteArrayInputStream` fixtures.
 */
internal class OdtParser {

  data class Parsed(
    val blocks: List<OdfTextBlock>,
    val title: String?,
  )

  /**
   * Parse the document. Returns the flat block list (lists / tables
   * nest inside their own variants) + the optional title from
   * `meta.xml` if present.
   */
  suspend fun parse(source: DocumentBytesSource): Parsed {
    val entries = source.openStream().use { readZipEntries(it) }
    val styles = entries[STYLES_XML]
    val content = entries[CONTENT_XML] ?: error("ODT missing content.xml")
    val meta = entries[META_XML]

    val resolver = OdfStyleResolver.build(
      *listOfNotNull(styles, content).map { ByteArrayInputStream(it) }.toTypedArray(),
    )
    val blocks = ContentWalker(resolver).walk(ByteArrayInputStream(content))
    val title = meta?.let { extractMetaTitle(ByteArrayInputStream(it)) }
    return Parsed(blocks = blocks, title = title)
  }

  /**
   * Cheap title probe — reads only `meta.xml` from the archive, parses
   * the `<dc:title>` text. Returns null if the entry doesn't exist or
   * the title is empty / whitespace.
   */
  suspend fun extractTitle(source: DocumentBytesSource): String? {
    val entries = source.openStream().use { readZipEntries(it, onlyNames = setOf(META_XML)) }
    val meta = entries[META_XML] ?: return null
    return extractMetaTitle(ByteArrayInputStream(meta))
  }

  /**
   * Inflate the named entries into in-memory byte arrays. If [onlyNames]
   * is non-null, the walker stops as soon as every requested entry has
   * been seen (cheap-probe path).
   */
  private fun readZipEntries(
    stream: InputStream,
    onlyNames: Set<String>? = null,
  ): Map<String, ByteArray> {
    val wants = onlyNames ?: WANTED_ENTRIES
    val out = HashMap<String, ByteArray>(wants.size)
    ZipInputStream(stream).use { zip ->
      var entry: ZipEntry? = zip.nextEntry
      while (entry != null) {
        val name = entry.name
        if (name in wants) {
          out[name] = zip.readBytes()
          if (out.size == wants.size) return out
        }
        zip.closeEntry()
        entry = zip.nextEntry
      }
    }
    return out
  }

  private fun extractMetaTitle(stream: InputStream): String? {
    val parser = newPullParser()
    parser.setInput(stream, null)
    var event = parser.eventType
    while (event != XmlPullParser.END_DOCUMENT) {
      if (event == XmlPullParser.START_TAG && parser.name == "title") {
        val text = readElementText(parser).trim()
        return text.ifEmpty { null }
      }
      event = parser.next()
    }
    return null
  }

  /**
   * Element-text reader. Consumes from the current `START_TAG` to its
   * matching `END_TAG`, returning the concatenated text content. Robust
   * to nested empty elements.
   */
  private fun readElementText(parser: XmlPullParser): String {
    val sb = StringBuilder()
    var depth = 1
    while (depth > 0) {
      when (parser.next()) {
        XmlPullParser.START_TAG -> depth++
        XmlPullParser.END_TAG -> depth--
        XmlPullParser.TEXT -> sb.append(parser.text)
      }
    }
    return sb.toString()
  }

  companion object {
    const val CONTENT_XML = "content.xml"
    const val STYLES_XML = "styles.xml"
    const val META_XML = "meta.xml"
    private val WANTED_ENTRIES = setOf(CONTENT_XML, STYLES_XML, META_XML)

    internal fun newPullParser(): XmlPullParser {
      val factory = XmlPullParserFactory.newInstance()
      factory.isNamespaceAware = true
      return factory.newPullParser()
    }
  }

  /**
   * Walks `content.xml` into the block list. Internal to keep the
   * recursive helpers under one roof; the top-level [OdtParser.parse]
   * is the only public entry point.
   *
   * The walker is iterative (`parser.next()`) rather than recursive on
   * the XML tree — pull parsing forces this anyway, and it keeps the
   * stack bounded on documents with deep nesting.
   */
  private class ContentWalker(
    private val resolver: OdfStyleResolver,
  ) {

    fun walk(stream: InputStream): List<OdfTextBlock> {
      val out = ArrayList<OdfTextBlock>()
      val parser = newPullParser()
      parser.setInput(stream, null)
      var event = parser.eventType
      // Skip until we hit <office:text>. Everything before is metadata
      // / settings / styles.
      while (event != XmlPullParser.END_DOCUMENT) {
        if (event == XmlPullParser.START_TAG && parser.name == "text") {
          // The <office:text> body — walk children.
          walkBody(parser, out)
          break
        }
        event = parser.next()
      }
      return out
    }

    /**
     * Walk the children of the current container (`<office:text>` or a
     * `<table:table-cell>` or a `<text:list-item>`). Consumes events
     * up to and including the matching END_TAG.
     */
    private fun walkBody(parser: XmlPullParser, into: MutableList<OdfTextBlock>) {
      // Caller positioned us at a START_TAG; consume until matching END_TAG.
      var depth = 1
      while (depth > 0) {
        val event = parser.next()
        when (event) {
          XmlPullParser.START_TAG -> {
            val name = parser.name
            when (name) {
              "p" -> into.add(readParagraph(parser))
              "h" -> into.add(readHeading(parser))
              "list" -> into.add(readList(parser))
              "table" -> into.add(readTable(parser))
              "tracked-changes" -> skipSubtree(parser)
              "object", "object-ole" -> {
                into.add(OdfTextBlock.EmbeddedPlaceholder(kind = "object"))
                skipSubtree(parser)
              }
              "chart" -> {
                into.add(OdfTextBlock.EmbeddedPlaceholder(kind = "chart"))
                skipSubtree(parser)
              }
              "frame" -> {
                into.add(OdfTextBlock.EmbeddedPlaceholder(kind = "frame"))
                skipSubtree(parser)
              }
              else -> skipSubtree(parser)
            }
          }
          XmlPullParser.END_TAG -> depth--
          XmlPullParser.END_DOCUMENT -> return
        }
      }
    }

    private fun readParagraph(parser: XmlPullParser): OdfTextBlock.Paragraph {
      val styleName = parser.getAttributeValueLocal("style-name")
      val runs = readInlineRuns(parser)
      return OdfTextBlock.Paragraph(
        runs = runs,
        style = resolver.paragraphStyleFor(styleName),
      )
    }

    private fun readHeading(parser: XmlPullParser): OdfTextBlock.Heading {
      val level = parser.getAttributeValueLocal("outline-level")?.toIntOrNull()?.coerceIn(1, 6) ?: 1
      val runs = readInlineRuns(parser)
      return OdfTextBlock.Heading(level = level, runs = runs)
    }

    /**
     * Read inline content up to the matching `END_TAG` of the current
     * `<text:p>` / `<text:h>`. Spans wrap their inner text; `text:a`
     * carries a `xlink:href`; bare text between tags becomes a run with
     * default style. Tracked-changes inline markers are consumed
     * silently. `text:s` / `text:tab` / `text:line-break` expand to
     * whitespace / tab / newline.
     */
    private fun readInlineRuns(parser: XmlPullParser): List<OdfRun> {
      val runs = ArrayList<OdfRun>()
      // Inline state — most recent style + href.
      val styleStack = ArrayDeque<OdfCharStyle>()
      val hrefStack = ArrayDeque<String?>()
      styleStack.addLast(OdfCharStyle.Default)
      hrefStack.addLast(null)
      var depth = 1
      val buffer = StringBuilder()
      fun flush() {
        if (buffer.isNotEmpty()) {
          runs.add(OdfRun(text = buffer.toString(), style = styleStack.last(), href = hrefStack.last()))
          buffer.clear()
        }
      }
      while (depth > 0) {
        val event = parser.next()
        when (event) {
          XmlPullParser.TEXT -> buffer.append(parser.text)
          XmlPullParser.START_TAG -> {
            depth++
            when (parser.name) {
              "span" -> {
                flush()
                val sn = parser.getAttributeValueLocal("style-name")
                styleStack.addLast(resolver.charStyleFor(sn))
                hrefStack.addLast(hrefStack.last())
              }
              "a" -> {
                flush()
                styleStack.addLast(styleStack.last())
                hrefStack.addLast(parser.getAttributeValueLocal("href") ?: hrefStack.last())
              }
              "s" -> {
                val count = parser.getAttributeValueLocal("c")?.toIntOrNull() ?: 1
                buffer.append(" ".repeat(count.coerceAtMost(MAX_SPACE_EXPAND)))
              }
              "tab" -> buffer.append('\t')
              "line-break" -> buffer.append('\n')
              "change", "change-start", "change-end" -> {
                // Tracked-changes markers — consumed silently.
              }
              else -> {
                // Unknown inline child — push neutral so the matching
                // END_TAG pops cleanly. Their subtree gets walked as
                // inline content; if they have unexpected children
                // those get consumed too.
                styleStack.addLast(styleStack.last())
                hrefStack.addLast(hrefStack.last())
              }
            }
          }
          XmlPullParser.END_TAG -> {
            depth--
            if (depth == 0) break
            when (parser.name) {
              "span", "a" -> {
                flush()
                styleStack.removeLast()
                hrefStack.removeLast()
              }
              "s", "tab", "line-break", "change", "change-start", "change-end" -> {
                // Self-closing in practice — nothing to pop.
              }
              else -> {
                // Mirror the neutral push from the unknown START_TAG branch.
                if (styleStack.size > 1) {
                  flush()
                  styleStack.removeLast()
                  hrefStack.removeLast()
                }
              }
            }
          }
          XmlPullParser.END_DOCUMENT -> {
            depth = 0
            break
          }
        }
      }
      flush()
      return runs
    }

    private fun readList(parser: XmlPullParser): OdfTextBlock.ListBlock {
      // ODF doesn't put ordered/unordered on the list element itself —
      // it's encoded in the referenced list style. For v1, infer from
      // the first <text:list-style> match if present; otherwise treat
      // as bulleted. The Compose body renders both with leading marks.
      val styleName = parser.getAttributeValueLocal("style-name")
      val ordered = isOrderedListStyle(styleName)
      val items = ArrayList<List<OdfTextBlock>>()
      var depth = 1
      while (depth > 0) {
        val event = parser.next()
        when (event) {
          XmlPullParser.START_TAG -> {
            when (parser.name) {
              "list-item" -> {
                val itemBlocks = ArrayList<OdfTextBlock>()
                walkBody(parser, itemBlocks)
                items.add(itemBlocks)
              }
              "list-header" -> {
                // Treat the optional header as an extra item so its text
                // surfaces; this is the LibreOffice convention.
                val itemBlocks = ArrayList<OdfTextBlock>()
                walkBody(parser, itemBlocks)
                items.add(itemBlocks)
              }
              else -> skipSubtree(parser)
            }
          }
          XmlPullParser.END_TAG -> depth--
          XmlPullParser.END_DOCUMENT -> break
        }
      }
      return OdfTextBlock.ListBlock(ordered = ordered, items = items)
    }

    /**
     * Heuristic: ordered if the style name suggests numbering. The full
     * resolution would parse `text:list-style` definitions and check
     * for `text:list-level-style-number` children; that's v1.1. For
     * v1 we lean on the convention LibreOffice / MS Word -> ODT
     * converters follow (style names ending in "Numbering" / containing
     * "L1" / etc. for ordered lists).
     */
    private fun isOrderedListStyle(name: String?): Boolean {
      if (name == null) return false
      val lower = name.lowercase()
      return "number" in lower || "ordered" in lower
    }

    private fun readTable(parser: XmlPullParser): OdfTextBlock.Table {
      val rows = ArrayList<List<List<OdfTextBlock>>>()
      var depth = 1
      while (depth > 0) {
        val event = parser.next()
        when (event) {
          XmlPullParser.START_TAG -> {
            when (parser.name) {
              "table-row" -> rows.add(readTableRow(parser))
              "table-header-rows" -> {
                // Spec-gotcha: a header-row group wraps real rows.
                // Recurse so the inner rows still land in `rows`.
                var inner = 1
                while (inner > 0) {
                  val innerEvent = parser.next()
                  when (innerEvent) {
                    XmlPullParser.START_TAG -> {
                      if (parser.name == "table-row") rows.add(readTableRow(parser)) else skipSubtree(parser)
                    }
                    XmlPullParser.END_TAG -> inner--
                    XmlPullParser.END_DOCUMENT -> { depth = 0; inner = 0 }
                  }
                }
              }
              else -> skipSubtree(parser)
            }
          }
          XmlPullParser.END_TAG -> depth--
          XmlPullParser.END_DOCUMENT -> break
        }
      }
      return OdfTextBlock.Table(rows = rows)
    }

    private fun readTableRow(parser: XmlPullParser): List<List<OdfTextBlock>> {
      val cells = ArrayList<List<OdfTextBlock>>()
      var depth = 1
      while (depth > 0) {
        val event = parser.next()
        when (event) {
          XmlPullParser.START_TAG -> {
            when (parser.name) {
              "table-cell" -> {
                val cellBlocks = ArrayList<OdfTextBlock>()
                walkBody(parser, cellBlocks)
                cells.add(cellBlocks)
              }
              "covered-table-cell" -> {
                // Skip — spec-correct rendering of covered cells is
                // handled inside the table block as positional padding;
                // the v1 renderer collapses them to empty cells.
                cells.add(emptyList())
                skipSubtree(parser)
              }
              else -> skipSubtree(parser)
            }
          }
          XmlPullParser.END_TAG -> depth--
          XmlPullParser.END_DOCUMENT -> break
        }
      }
      return cells
    }

    /** Default-skip walker — consumes the subtree rooted at the current START_TAG. */
    private fun skipSubtree(parser: XmlPullParser) {
      var depth = 1
      while (depth > 0) {
        when (parser.next()) {
          XmlPullParser.START_TAG -> depth++
          XmlPullParser.END_TAG -> depth--
          XmlPullParser.END_DOCUMENT -> return
        }
      }
    }

    companion object {
      private const val MAX_SPACE_EXPAND = 4096
    }
  }
}
