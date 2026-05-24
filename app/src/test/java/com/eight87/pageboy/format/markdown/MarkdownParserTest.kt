package com.eight87.pageboy.format.markdown

import org.commonmark.ext.gfm.strikethrough.Strikethrough
import org.commonmark.ext.gfm.tables.TableBlock
import org.commonmark.ext.task.list.items.TaskListItemMarker
import org.commonmark.node.AbstractVisitor
import org.commonmark.node.CustomBlock
import org.commonmark.node.CustomNode
import org.commonmark.node.Heading
import org.commonmark.node.Link
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase D / D.11 — verifies the parser loads the six GFM extensions
 * shipped in v1: tables, strikethrough, task-list-items, autolink,
 * footnotes, image-attributes. We sample-test the ones that emit a
 * distinguishable node type; the autolink + image-attribute extensions
 * extend existing nodes (Link / Image) and are exercised by the
 * inlines test.
 */
class MarkdownParserTest {

  private val parser = MarkdownParser()

  @Test
  fun `parses headings`() {
    val ast = parser.parse("# Hello\n\nWorld")
    val first = ast.firstChild as Heading
    assertEquals(1, first.level)
  }

  @Test
  fun `tables extension produces a TableBlock`() {
    val ast = parser.parse(
      """
      | A | B |
      | --- | --- |
      | 1 | 2 |
      """.trimIndent(),
    )
    var seen: TableBlock? = null
    ast.accept(object : AbstractVisitor() {
      override fun visit(customBlock: CustomBlock) {
        if (customBlock is TableBlock && seen == null) seen = customBlock
        super.visit(customBlock)
      }
    })
    assertNotNull("Expected TableBlock from the gfm-tables extension", seen)
  }

  @Test
  fun `strikethrough extension produces Strikethrough inlines`() {
    val ast = parser.parse("hello ~~struck~~ world")
    var seen = false
    ast.accept(object : AbstractVisitor() {
      override fun visit(customNode: CustomNode) {
        if (customNode is Strikethrough) seen = true
        super.visit(customNode)
      }
    })
    assertTrue("Expected Strikethrough from the gfm-strikethrough extension", seen)
  }

  @Test
  fun `task list extension marks list items`() {
    val ast = parser.parse(
      """
      - [x] done
      - [ ] open
      """.trimIndent(),
    )
    var checkedCount = 0
    var openCount = 0
    ast.accept(object : AbstractVisitor() {
      override fun visit(customNode: CustomNode) {
        if (customNode is TaskListItemMarker) {
          if (customNode.isChecked) checkedCount += 1 else openCount += 1
        }
        super.visit(customNode)
      }
    })
    assertEquals(1, checkedCount)
    assertEquals(1, openCount)
  }

  @Test
  fun `autolink extension turns bare URLs into Link nodes`() {
    val ast = parser.parse("See https://example.com for details")
    var seenLink: Link? = null
    ast.accept(object : AbstractVisitor() {
      override fun visit(link: Link) {
        seenLink = link
        super.visit(link)
      }
    })
    assertNotNull("Expected the autolink extension to surface a Link node", seenLink)
    assertEquals("https://example.com", seenLink!!.destination)
  }
}
