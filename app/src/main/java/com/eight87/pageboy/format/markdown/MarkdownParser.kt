package com.eight87.pageboy.format.markdown

import org.commonmark.Extension
import org.commonmark.ext.autolink.AutolinkExtension
import org.commonmark.ext.footnotes.FootnotesExtension
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.ext.image.attributes.ImageAttributesExtension
import org.commonmark.ext.task.list.items.TaskListItemsExtension
import org.commonmark.node.Node
import org.commonmark.parser.Parser

/**
 * Phase D — single-responsibility commonmark wrapper. Owns the parser
 * construction + the extension list; everything downstream (renderer,
 * inline folder, title extractor) takes the produced [Node] AST.
 *
 * The extension list is the six GFM-shaped extensions Phase D ships per
 * `docs/plans/format-markdown.md`:
 *
 *   1. tables           — GFM pipe tables
 *   2. strikethrough    — `~~struck~~`
 *   3. task-list-items  — `[ ]` / `[x]` checkboxes
 *   4. autolink         — bare URLs / email addresses
 *   5. footnotes        — `[^1]` references + definitions
 *   6. image-attributes — `![alt](url){width=200 height=100}`
 *
 * YAML front-matter is sniffed by [MarkdownFrontMatter] before the body
 * reaches the parser — we ship our own 40-LOC sniffer instead of
 * pulling `commonmark-ext-yaml-front-matter` because that extension
 * brings snakeyaml-engine (~150 KB minified) for parsing we don't
 * actually need. The Phase D rendering surface doesn't consume YAML
 * keys; future Obsidian-metadata work can re-parse the lines with a
 * real YAML library at the point of need.
 */
class MarkdownParser {

  private val extensions: List<Extension> = listOf(
    TablesExtension.create(),
    StrikethroughExtension.create(),
    TaskListItemsExtension.create(),
    AutolinkExtension.create(),
    FootnotesExtension.create(),
    ImageAttributesExtension.create(),
  )

  private val parser: Parser = Parser.builder()
    .extensions(extensions)
    .build()

  /** Parse the raw markdown source (front-matter already stripped). */
  fun parse(source: String): Node = parser.parse(source)
}
