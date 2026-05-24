package com.eight87.pageboy.format.odt

import com.eight87.pageboy.format.api.DocumentBytesSource
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Phase K test helpers — build in-memory ODT byte streams without any
 * dependency on LibreOffice or external fixtures. The renderer's
 * spec-gotcha branches all reduce to "see this XML, expect that
 * block model"; an in-memory ZIP is enough to drive them.
 */
internal object OdtTestFixtures {

  /**
   * Build an ODT byte array containing the supplied `content.xml`,
   * optional `styles.xml`, optional `meta.xml`. The `mimetype` entry
   * lands first as the ODF spec requires.
   */
  fun buildOdt(
    content: String,
    styles: String? = null,
    meta: String? = null,
  ): ByteArray {
    val baos = ByteArrayOutputStream()
    ZipOutputStream(baos).use { zip ->
      writeEntry(zip, "mimetype", "application/vnd.oasis.opendocument.text".toByteArray())
      writeEntry(zip, "content.xml", content.toByteArray(Charsets.UTF_8))
      if (styles != null) writeEntry(zip, "styles.xml", styles.toByteArray(Charsets.UTF_8))
      if (meta != null) writeEntry(zip, "meta.xml", meta.toByteArray(Charsets.UTF_8))
    }
    return baos.toByteArray()
  }

  private fun writeEntry(zip: ZipOutputStream, name: String, bytes: ByteArray) {
    val entry = ZipEntry(name)
    zip.putNextEntry(entry)
    zip.write(bytes)
    zip.closeEntry()
  }

  fun source(bytes: ByteArray, name: String? = "test.odt"): DocumentBytesSource =
    object : DocumentBytesSource {
      override suspend fun openStream(): InputStream = ByteArrayInputStream(bytes)
      override suspend fun length(): Long = bytes.size.toLong()
      override suspend fun displayName(): String? = name
    }

  /** Minimal `content.xml` skeleton wrapping the inner-text body fragment. */
  fun contentXml(body: String): String =
    """<?xml version="1.0" encoding="UTF-8"?>
<office:document-content xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0"
  xmlns:text="urn:oasis:names:tc:opendocument:xmlns:text:1.0"
  xmlns:table="urn:oasis:names:tc:opendocument:xmlns:table:1.0"
  xmlns:style="urn:oasis:names:tc:opendocument:xmlns:style:1.0"
  xmlns:xlink="http://www.w3.org/1999/xlink"
  xmlns:draw="urn:oasis:names:tc:opendocument:xmlns:drawing:1.0"
  xmlns:chart="urn:oasis:names:tc:opendocument:xmlns:chart:1.0">
  <office:body>
    <office:text>
$body
    </office:text>
  </office:body>
</office:document-content>
"""

  /** Minimal `styles.xml` with the named styles supplied (each a `<style:style>` element). */
  fun stylesXml(styles: String): String =
    """<?xml version="1.0" encoding="UTF-8"?>
<office:document-styles xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0"
  xmlns:style="urn:oasis:names:tc:opendocument:xmlns:style:1.0"
  xmlns:fo="urn:oasis:names:tc:opendocument:xmlns:xsl-fo-compatible:1.0">
  <office:styles>
$styles
  </office:styles>
</office:document-styles>
"""

  /** Minimal `meta.xml` with a Dublin Core title. */
  fun metaXml(title: String): String =
    """<?xml version="1.0" encoding="UTF-8"?>
<office:document-meta xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0"
  xmlns:dc="http://purl.org/dc/elements/1.1/">
  <office:meta>
    <dc:title>$title</dc:title>
  </office:meta>
</office:document-meta>
"""
}
