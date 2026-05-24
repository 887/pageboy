package com.eight87.pageboy.domain.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase M.8 — round-trip tests for the [ScrollPosition.EpubCfi] sealed
 * variant. Verifies the codec preserves the opaque CFI payload across
 * `encode → decode` and that the other variants continue to round-trip
 * unchanged after the EpubCfi addition.
 */
class EpubCfiSerializationTest {

  @Test
  fun `EpubCfi encodes and decodes through the JSON codec`() {
    val original = ScrollPosition.EpubCfi(
      cfi = """{"href":"OEBPS/chapter1.xhtml","type":"application/xhtml+xml","locations":{"progression":0.42}}""",
    )
    val encoded = ScrollPosition.encode(original)
    assertNotNull("non-null payload expected", encoded)
    val decoded = ScrollPosition.decode(encoded)
    assertTrue("decoded variant must be EpubCfi", decoded is ScrollPosition.EpubCfi)
    assertEquals(original.cfi, (decoded as ScrollPosition.EpubCfi).cfi)
  }

  @Test
  fun `EpubCfi round-trip preserves embedded JSON braces and quotes`() {
    // Readium serialises locator-JSON containing nested objects; the
    // outer encoder treats the inner JSON as an opaque string. This
    // test catches accidental escape-handling regressions in the
    // kotlinx.serialization layer.
    val nested = """{"a":1,"b":{"c":"with \"quotes\" inside"},"arr":[1,2,3]}"""
    val pos = ScrollPosition.EpubCfi(cfi = nested)
    val decoded = ScrollPosition.decode(ScrollPosition.encode(pos)) as ScrollPosition.EpubCfi
    assertEquals(nested, decoded.cfi)
  }

  @Test
  fun `LazyColumn still round-trips after the EpubCfi addition`() {
    val pos = ScrollPosition.LazyColumn(itemIndex = 17, offset = 4321)
    val decoded = ScrollPosition.decode(ScrollPosition.encode(pos)) as? ScrollPosition.LazyColumn
    assertNotNull(decoded)
    assertEquals(17, decoded!!.itemIndex)
    assertEquals(4321, decoded.offset)
  }

  @Test
  fun `PdfPage still round-trips after the EpubCfi addition`() {
    val pos = ScrollPosition.PdfPage(page = 99, ratio = 0.123f)
    val decoded = ScrollPosition.decode(ScrollPosition.encode(pos)) as? ScrollPosition.PdfPage
    assertNotNull(decoded)
    assertEquals(99, decoded!!.page)
    assertEquals(0.123f, decoded.ratio, 0.0001f)
  }

  @Test
  fun `decode returns null for an empty CFI variant payload from a malformed encoding`() {
    assertNull(ScrollPosition.decode(""))
    assertNull(ScrollPosition.decode("   "))
    assertNull(ScrollPosition.decode(null))
    // A discriminator pointing at an unknown variant returns null — the
    // codec swallows the deserialisation failure (per the file
    // doc-comment: we'd rather lose the position than crash).
    assertNull(ScrollPosition.decode("""{"kind":"UnknownFutureVariant","x":1}"""))
  }

  @Test
  fun `encode returns null for a null position`() {
    assertNull(ScrollPosition.encode(null))
  }

  @Test
  fun `EpubCfi handles empty string payload`() {
    // The chrome's scroll-sink never writes an empty CFI in practice
    // (the Readium navigator publishes a fully-formed Locator on every
    // emission), but the codec must still tolerate the corner case.
    val pos = ScrollPosition.EpubCfi(cfi = "")
    val decoded = ScrollPosition.decode(ScrollPosition.encode(pos)) as? ScrollPosition.EpubCfi
    assertNotNull(decoded)
    assertEquals("", decoded!!.cfi)
  }
}
