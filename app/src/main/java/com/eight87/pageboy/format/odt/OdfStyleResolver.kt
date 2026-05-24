package com.eight87.pageboy.format.odt

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream

/**
 * Phase K — resolves `text:style-name` attribute values against the
 * `style:style` definitions found in `styles.xml` (and the
 * `<office:automatic-styles>` block of `content.xml`). Maps each
 * named style to its character properties (bold / italic / underline /
 * strike) and paragraph properties (alignment).
 *
 * **Style cascade.** ODF styles can reference a parent style via
 * `style:parent-style-name`. The resolver builds the raw per-style
 * properties first, then resolves each style by walking up the parent
 * chain (cycle-safe via a visited set; depth is small in practice but
 * the safety belt costs nothing).
 *
 * **Open/closed:** unknown style names fall back to defaults; unknown
 * style property attributes are silently ignored. Robust against
 * malformed input — never throws on a missing attribute.
 *
 * Scope: this is the read-only subset that the v1 ODT viewer renders.
 * Font size, font family, colour, paragraph indent, line height are
 * intentionally out of scope per `format-odt.md` (v1.1 polish surface).
 */
internal class OdfStyleResolver private constructor(
  private val charByStyle: Map<String, OdfCharStyle>,
  private val paragraphByStyle: Map<String, OdfParagraphStyle>,
) {

  /** Lookup the resolved [OdfCharStyle] for a `text:style-name` value, or default. */
  fun charStyleFor(styleName: String?): OdfCharStyle {
    if (styleName.isNullOrEmpty()) return OdfCharStyle.Default
    return charByStyle[styleName] ?: OdfCharStyle.Default
  }

  /** Lookup the resolved [OdfParagraphStyle] for a `text:style-name` value, or default. */
  fun paragraphStyleFor(styleName: String?): OdfParagraphStyle {
    if (styleName.isNullOrEmpty()) return OdfParagraphStyle.Default
    return paragraphByStyle[styleName] ?: OdfParagraphStyle.Default
  }

  companion object {

    /** Empty resolver — every lookup returns defaults. */
    fun empty(): OdfStyleResolver = OdfStyleResolver(emptyMap(), emptyMap())

    /**
     * Build a resolver from one or more XML streams. Pass `styles.xml`
     * and `content.xml` (the latter for its `<office:automatic-styles>`
     * block); later streams' style names shadow earlier ones for the
     * same name, which mirrors the ODF specification's "automatic
     * styles take precedence over named styles" rule.
     *
     * Each stream is fully consumed; the caller closes them.
     */
    fun build(vararg streams: InputStream): OdfStyleResolver {
      val raw = LinkedHashMap<String, RawStyle>()
      for (stream in streams) {
        if (stream === EMPTY_STREAM) continue
        parseInto(stream, raw)
      }
      return resolveCascade(raw)
    }

    private val EMPTY_STREAM = java.io.ByteArrayInputStream(ByteArray(0))

    private fun parseInto(stream: InputStream, into: MutableMap<String, RawStyle>) {
      val parser = newPullParser()
      parser.setInput(stream, null)
      var event = parser.eventType
      var current: RawStyle? = null
      while (event != XmlPullParser.END_DOCUMENT) {
        when (event) {
          XmlPullParser.START_TAG -> {
            val local = parser.name
            when {
              // `style:style` — open a new raw record. Two attrs we care about.
              local == "style" -> {
                val name = parser.getAttributeValueLocal("name")
                val parent = parser.getAttributeValueLocal("parent-style-name")
                if (name != null) {
                  val fresh = RawStyle(name = name, parent = parent)
                  current = fresh
                  into[name] = fresh
                }
              }
              // Inside a `style:style`, the `style:text-properties` carries
              // the char-level flags.
              local == "text-properties" && current != null -> {
                val weight = parser.getAttributeValueLocal("font-weight")
                val style = parser.getAttributeValueLocal("font-style")
                val underline = parser.getAttributeValueLocal("text-underline-style")
                val strike = parser.getAttributeValueLocal("text-line-through-style")
                val c = current
                if (weight != null) c.bold = weight == "bold" || weight.toIntOrNull()?.let { it >= 600 } == true
                if (style != null) c.italic = style == "italic" || style == "oblique"
                if (underline != null && underline != "none") c.underline = true
                if (strike != null && strike != "none") c.strike = true
              }
              // `style:paragraph-properties` carries alignment.
              local == "paragraph-properties" && current != null -> {
                val align = parser.getAttributeValueLocal("text-align")
                val c = current
                if (align != null) {
                  c.align = when (align) {
                    "center" -> OdfAlign.Center
                    "end", "right" -> OdfAlign.End
                    "justify" -> OdfAlign.Justify
                    else -> OdfAlign.Start
                  }
                }
              }
            }
          }
          XmlPullParser.END_TAG -> {
            if (parser.name == "style") current = null
          }
        }
        event = parser.next()
      }
    }

    /**
     * Walk the parent chain for each raw style to produce flat
     * [OdfCharStyle] + [OdfParagraphStyle] maps. Cycle-safe.
     */
    private fun resolveCascade(raw: Map<String, RawStyle>): OdfStyleResolver {
      val char = HashMap<String, OdfCharStyle>(raw.size)
      val para = HashMap<String, OdfParagraphStyle>(raw.size)
      for (name in raw.keys) {
        val chain = ArrayList<RawStyle>()
        val visited = HashSet<String>()
        var cursor: RawStyle? = raw[name]
        while (cursor != null && visited.add(cursor.name)) {
          chain.add(cursor)
          cursor = cursor.parent?.let { raw[it] }
        }
        // Effective style = root parent overlaid with descendants (descendant wins).
        var bold = false
        var italic = false
        var underline = false
        var strike = false
        var align: OdfAlign? = null
        for (i in chain.indices.reversed()) {
          val r = chain[i]
          if (r.bold != null) bold = r.bold!!
          if (r.italic != null) italic = r.italic!!
          if (r.underline != null) underline = r.underline!!
          if (r.strike != null) strike = r.strike!!
          if (r.align != null) align = r.align
        }
        char[name] = OdfCharStyle(bold = bold, italic = italic, underline = underline, strike = strike)
        para[name] = OdfParagraphStyle(align = align ?: OdfAlign.Start)
      }
      return OdfStyleResolver(char, para)
    }

    private fun newPullParser(): XmlPullParser {
      val factory = XmlPullParserFactory.newInstance()
      factory.isNamespaceAware = true
      return factory.newPullParser()
    }
  }

  /**
   * Mutable accumulator for the per-style raw properties as the parser
   * walks. Nullable flags so "absent" distinguishes from "explicit
   * false" (the cascade needs that signal — descendant `null` means
   * "inherit parent", not "reset to false").
   */
  private class RawStyle(
    val name: String,
    val parent: String?,
    var bold: Boolean? = null,
    var italic: Boolean? = null,
    var underline: Boolean? = null,
    var strike: Boolean? = null,
    var align: OdfAlign? = null,
  )
}

/**
 * Convenience — fetch an attribute by local name regardless of which
 * prefix the document declares it under. ODF documents in the wild may
 * use `style:name` or just `name`; `setNamespaceAware(true)` means
 * `XmlPullParser` indexes by `(namespace, localPart)`. We don't care
 * about the namespace here — any prefix that lands on the attribute
 * with the expected local name is fine.
 */
internal fun XmlPullParser.getAttributeValueLocal(localName: String): String? {
  for (i in 0 until attributeCount) {
    if (getAttributeName(i) == localName) return getAttributeValue(i)
  }
  return null
}
