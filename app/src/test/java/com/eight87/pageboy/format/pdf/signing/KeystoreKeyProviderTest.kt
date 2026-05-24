package com.eight87.pageboy.format.pdf.signing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.cert.X509Certificate

/**
 * Phase H.7 — JVM-only verification of [KeystoreKeyProvider]'s
 * self-signed-cert builder. The `loadOrGenerate` path lives against
 * the `AndroidKeyStore` provider which is unavailable under
 * Robolectric (the provider is implemented in
 * `frameworks/base/keystore`); we exercise only the X509v3 builder
 * which is pure JVM and end-to-end-testable.
 *
 * The full AndroidKeystore path is exercised in
 * [com.eight87.pageboy.ui.reader.signing.SigningSheetSmokeTest]
 * indirectly via the chrome's bottom-sheet smoke + by hand on the
 * AVD per Phase H.9.
 */
class KeystoreKeyProviderTest {

  @Test
  fun `buildSelfSignedCert produces a verifiable EC cert`() {
    val provider = KeystoreKeyProvider()
    val kp = SigningTestFixtures.generateEcKeyPair()
    val cert = provider.buildSelfSignedCert(kp, "Keystore Self-Signed Test")
    assertNotNull(cert)
    // Self-signed → subject == issuer.
    assertEquals(cert.subjectX500Principal, cert.issuerX500Principal)
    // CN extraction lands the expected display name.
    assertTrue(
      "CN present in subject DN: ${cert.subjectX500Principal.name}",
      cert.subjectX500Principal.name.contains("CN=Keystore Self-Signed Test"),
    )
    // Verifies under its own public key (self-signed by construction).
    cert.verify(kp.public)
  }

  @Test
  fun `buildSelfSignedCert lands in usable SigningKeyMaterial`() {
    val provider = KeystoreKeyProvider()
    val kp = SigningTestFixtures.generateEcKeyPair()
    val cert: X509Certificate = provider.buildSelfSignedCert(kp, "Material Test")
    val material = SigningKeyMaterial(
      privateKey = kp.private,
      certificateChain = arrayOf(cert),
      signatureAlgorithm = KeystoreKeyProvider.SIG_ALG_EC,
      displayName = "Material Test",
    )
    // Drives PadesSigner end-to-end.
    val signer = PadesSigner()
    val out = java.io.ByteArrayOutputStream()
    signer.sign(
      input = java.io.ByteArrayInputStream(SigningTestFixtures.makeTinyPdf()),
      output = out,
      material = material,
    )
    val reader = com.lowagie.text.pdf.PdfReader(out.toByteArray())
    assertTrue(reader.acroFields.signedFieldNames.isNotEmpty())
    reader.close()
  }
}
