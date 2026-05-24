package com.eight87.pageboy.format.pdf.signing

import com.lowagie.text.pdf.AcroFields
import com.lowagie.text.pdf.PdfReader
import org.bouncycastle.cms.CMSSignedData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Calendar
import java.util.TimeZone

/**
 * Phase H.7 — JVM-only verification of [PadesSigner]. Generates an EC
 * P-256 self-signed cert, signs a small in-memory PDF, asserts the
 * output carries a `/Sig` field with `/SubFilter ETSI.CAdES.detached`,
 * and round-trips the CMS blob through Bouncy Castle's
 * [CMSSignedData] parser.
 */
class PadesSignerTest {

  @Test
  fun `signed PDF carries an ETSI CAdES signature field`() {
    val signer = PadesSigner()
    val material = SigningTestFixtures.makeKeyMaterial()
    val signedBytes = ByteArrayOutputStream().use { out ->
      signer.sign(
        input = ByteArrayInputStream(SigningTestFixtures.makeTinyPdf("Hello PAdES")),
        output = out,
        material = material,
        reason = "Phase H test",
        location = "Robolectric",
        signDate = pinnedCalendar(),
      )
      out.toByteArray()
    }
    // PDF survives reload.
    val reader = PdfReader(signedBytes)
    val acro: AcroFields = reader.acroFields
    val sigNames: List<String> = acro.signedFieldNames
    assertTrue("signature field present", sigNames.isNotEmpty())
    val sig = acro.getSignatureDictionary(sigNames.first())
    assertNotNull(sig)
    assertEquals(
      "subfilter is ETSI.CAdES.detached per PAdES-B-B",
      "/${PadesSigner.ETSI_CADES_DETACHED.toString().trimStart('/')}",
      sig.get(com.lowagie.text.pdf.PdfName.SUBFILTER).toString(),
    )
    reader.close()
  }

  @Test
  fun `CMS SignedData round-trips through Bouncy Castle parser`() {
    val signer = PadesSigner()
    val material = SigningTestFixtures.makeKeyMaterial("PAdES Round-Trip")
    val data = "the bytes the signature is over".toByteArray()
    val signed = signer.generateDetachedCms(data, material)
    val parsed = CMSSignedData(
      org.bouncycastle.cms.CMSProcessableByteArray(data),
      signed,
    )
    // Detached signature → no encapsulated content; signers exist.
    val signers = parsed.signerInfos.signers
    assertEquals(1, signers.size)
  }

  @Test
  fun `signer displayName lands in the signature dictionary Name slot`() {
    val signer = PadesSigner()
    val material = SigningTestFixtures.makeKeyMaterial("Alex Pageboy")
    val signedBytes = ByteArrayOutputStream().use { out ->
      signer.sign(
        input = ByteArrayInputStream(SigningTestFixtures.makeTinyPdf()),
        output = out,
        material = material,
        signDate = pinnedCalendar(),
      )
      out.toByteArray()
    }
    val reader = PdfReader(signedBytes)
    val sigDict = reader.acroFields.getSignatureDictionary(reader.acroFields.signedFieldNames.first())
    // Stored as a PDF string surrounded by parens; toString trims the wrapping.
    val name = sigDict.getAsString(com.lowagie.text.pdf.PdfName.NAME)?.toUnicodeString()
    assertEquals("Alex Pageboy", name)
    reader.close()
  }

  @Test
  fun `re-signing in append mode preserves the first signature`() {
    val signer = PadesSigner()
    val matA = SigningTestFixtures.makeKeyMaterial("Signer A")
    val matB = SigningTestFixtures.makeKeyMaterial("Signer B")
    // First signature.
    val firstOut = ByteArrayOutputStream().also { out ->
      signer.sign(
        input = ByteArrayInputStream(SigningTestFixtures.makeTinyPdf("Append-mode test")),
        output = out,
        material = matA,
        signDate = pinnedCalendar(),
      )
    }.toByteArray()
    // Re-sign with B.
    val secondOut = ByteArrayOutputStream().also { out ->
      signer.sign(
        input = ByteArrayInputStream(firstOut),
        output = out,
        material = matB,
        signDate = pinnedCalendar(),
      )
    }.toByteArray()
    val reader = PdfReader(secondOut)
    val names = reader.acroFields.signedFieldNames
    assertEquals("both signatures survive in append mode", 2, names.size)
    reader.close()
  }

  private fun pinnedCalendar(): Calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
    set(2026, Calendar.MAY, 24, 12, 0, 0)
    set(Calendar.MILLISECOND, 0)
  }
}
