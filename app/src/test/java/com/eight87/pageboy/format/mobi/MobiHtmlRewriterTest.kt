package com.eight87.pageboy.format.mobi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase Q.7 — pure JVM tests for [MobiHtmlRewriter].
 *
 * The rewriter normalises both the older Mobipocket `recindex="N"`
 * attribute and Amazon's later `src="kindle:embed:0000NN"` convention
 * to the `pageboy://mobi/<recindex>` scheme the WebViewClient resolves.
 */
class MobiHtmlRewriterTest {

  @Test
  fun `recindex attribute rewrites to pageboy mobi src`() {
    val input = """<img recindex="3" alt="figure">"""
    val out = MobiHtmlRewriter.rewrite(input)
    assertTrue("expected pageboy:// src in '$out'", out.contains("src=\"pageboy://mobi/3\""))
  }

  @Test
  fun `kindle embed AZW3 attribute rewrites to pageboy mobi src`() {
    val input = """<img src="kindle:embed:000007">"""
    val out = MobiHtmlRewriter.rewrite(input)
    assertEquals("""<img src="pageboy://mobi/7">""", out)
  }

  @Test
  fun `kindle embed with query parameter rewrites correctly`() {
    val input = """<img src="kindle:embed:0042?type=svg">"""
    val out = MobiHtmlRewriter.rewrite(input)
    assertEquals("""<img src="pageboy://mobi/42">""", out)
  }

  @Test
  fun `empty html passes through unchanged`() {
    assertEquals("", MobiHtmlRewriter.rewrite(""))
  }

  @Test
  fun `html without image references is unchanged`() {
    val input = "<p>Just text</p>"
    assertEquals(input, MobiHtmlRewriter.rewrite(input))
  }
}
