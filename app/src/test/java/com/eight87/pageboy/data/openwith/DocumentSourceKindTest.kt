package com.eight87.pageboy.data.openwith

import com.eight87.pageboy.data.library.DocumentSourceCodec
import com.eight87.pageboy.data.library.DocumentSourceKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase N.13 — pure JVM codec tests for [DocumentSourceCodec] /
 * [DocumentSourceKind]. The sealed-hierarchy round-trip is the
 * load-bearing invariant of Phase N's storage model.
 */
class DocumentSourceKindTest {

  @Test
  fun `LibraryRoot round-trip`() {
    val source = DocumentSourceKind.LibraryRoot(rootTreeUriString = "content://tree/abc")
    val json = DocumentSourceCodec.encode(source)
    val decoded = DocumentSourceCodec.decode(json)
    assertEquals(source, decoded)
  }

  @Test
  fun `AdHocOpen round-trip`() {
    val source = DocumentSourceKind.AdHocOpen(uri = "content://x/y", ephemeral = true)
    val json = DocumentSourceCodec.encode(source)
    val decoded = DocumentSourceCodec.decode(json)
    assertEquals(source, decoded)
    assertTrue(decoded is DocumentSourceKind.AdHocOpen)
  }

  @Test
  fun `null encodes to null`() {
    assertNull(DocumentSourceCodec.encode(null))
  }

  @Test
  fun `null and blank decode to null`() {
    assertNull(DocumentSourceCodec.decode(null))
    assertNull(DocumentSourceCodec.decode(""))
    assertNull(DocumentSourceCodec.decode("   "))
  }

  @Test
  fun `bogus json decodes to null`() {
    assertNull(DocumentSourceCodec.decode("{nope}"))
    assertNull(DocumentSourceCodec.decode("[\"AdHocOpen\"]"))
  }

  @Test
  fun `unknown discriminator decodes to null`() {
    val unknown = """{"type":"UnknownVariant","field":"x"}"""
    assertNull(DocumentSourceCodec.decode(unknown))
  }
}
