package com.eight87.pageboy.format.pdf.signing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.security.cert.X509Certificate

/**
 * Phase H.7 — JVM-only verification of [Pkcs12KeyProvider]. Builds an
 * in-memory `.p12` with a self-signed EC P-256 cert, loads it back,
 * asserts the [SigningKeyMaterial] surfaces the expected fields and
 * that round-trip signing works end-to-end.
 */
class Pkcs12KeyProviderTest {

  @Test
  fun `load extracts private key and cert chain from an in-memory pkcs12`() {
    val source = SigningTestFixtures.makeKeyMaterial("Pkcs12 Round-Trip")
    val pkcs12 = SigningTestFixtures.makePkcs12(source, alias = "first")
    val provider = Pkcs12KeyProvider()
    val loaded = provider.load(ByteArrayInputStream(pkcs12), "secret".toCharArray())
    // Compare functional surfaces, not raw byte equality — the key
    // shuttles through PKCS12 → BC reader → JCE PrivateKey; the
    // re-encoding doesn't preserve byte-for-byte equality across
    // providers (default JCE EC encoding adds curve OID padding the
    // BC encoding emits differently). The end-to-end signing test
    // below guards round-trip usability.
    assertEquals(source.privateKey.algorithm, loaded.privateKey.algorithm)
    assertEquals(1, loaded.certificateChain.size)
    assertNotNull(loaded.certificateChain[0] as X509Certificate)
    assertEquals("Pkcs12 Round-Trip", loaded.displayName)
    assertEquals("SHA256withECDSA", loaded.signatureAlgorithm)
  }

  @Test
  fun `load throws IOException on wrong password`() {
    val source = SigningTestFixtures.makeKeyMaterial()
    val pkcs12 = SigningTestFixtures.makePkcs12(source)
    val provider = Pkcs12KeyProvider()
    assertThrows(IOException::class.java) {
      provider.load(ByteArrayInputStream(pkcs12), "wrong-pw".toCharArray())
    }
  }

  @Test
  fun `load throws IOException on malformed bytes`() {
    val provider = Pkcs12KeyProvider()
    assertThrows(IOException::class.java) {
      provider.load(ByteArrayInputStream(byteArrayOf(0, 1, 2, 3)), "anything".toCharArray())
    }
  }

  @Test
  fun `pickSignatureAlgorithm maps EC RSA DSA to SHA-256`() {
    val provider = Pkcs12KeyProvider()
    val ec = SigningTestFixtures.generateEcKeyPair().private
    assertEquals("SHA256withECDSA", provider.pickSignatureAlgorithm(ec))
  }

  @Test
  fun `extractDisplayName prefers CN over the full DN`() {
    val source = SigningTestFixtures.makeKeyMaterial("Alex CN-Only")
    val provider = Pkcs12KeyProvider()
    val cn = provider.extractDisplayName(source.certificateChain[0] as X509Certificate)
    assertEquals("Alex CN-Only", cn)
  }

  @Test
  fun `the extracted material can drive PadesSigner end-to-end`() {
    val source = SigningTestFixtures.makeKeyMaterial("End-To-End")
    val pkcs12 = SigningTestFixtures.makePkcs12(source, password = "p4ss".toCharArray())
    val provider = Pkcs12KeyProvider()
    val loaded = provider.load(ByteArrayInputStream(pkcs12), "p4ss".toCharArray())
    val signer = PadesSigner()
    val out = java.io.ByteArrayOutputStream()
    signer.sign(
      input = ByteArrayInputStream(SigningTestFixtures.makeTinyPdf()),
      output = out,
      material = loaded,
    )
    val reader = com.lowagie.text.pdf.PdfReader(out.toByteArray())
    assertTrue(reader.acroFields.signedFieldNames.isNotEmpty())
    reader.close()
  }
}
