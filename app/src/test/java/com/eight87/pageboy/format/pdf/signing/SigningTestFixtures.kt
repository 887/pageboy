package com.eight87.pageboy.format.pdf.signing

import com.lowagie.text.Document
import com.lowagie.text.Paragraph
import com.lowagie.text.pdf.PdfWriter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.cert.Certificate
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec
import java.util.Date
import javax.security.auth.x500.X500Principal

/** Shared JVM-only fixtures for the Phase H signing tests. */
internal object SigningTestFixtures {

  /** Build a tiny one-page PDF in memory. */
  fun makeTinyPdf(text: String = "Sign me"): ByteArray {
    val out = ByteArrayOutputStream()
    val doc = Document()
    PdfWriter.getInstance(doc, out)
    doc.open()
    doc.add(Paragraph(text))
    doc.close()
    return out.toByteArray()
  }

  /** Generate an EC P-256 keypair via the JCE (no AndroidKeyStore — pure JVM). */
  fun generateEcKeyPair(): KeyPair {
    Pkcs12KeyProvider.ensureBouncyCastleRegistered()
    val kpg = KeyPairGenerator.getInstance("EC")
    kpg.initialize(ECGenParameterSpec("secp256r1"))
    return kpg.generateKeyPair()
  }

  /** Build a self-signed X.509 v3 cert via Bouncy Castle. */
  fun selfSignedCert(
    keyPair: KeyPair,
    commonName: String = "Pageboy Test",
  ): X509Certificate {
    Pkcs12KeyProvider.ensureBouncyCastleRegistered()
    val now = System.currentTimeMillis()
    val subject = org.bouncycastle.asn1.x500.X500Name("CN=$commonName")
    val builder = JcaX509v3CertificateBuilder(
      subject,
      BigInteger.valueOf(now),
      Date(now - 60_000L),
      Date(now + 365L * 24 * 3600 * 1000),
      subject,
      keyPair.public,
    )
    val signer = JcaContentSignerBuilder(
      if (keyPair.private.algorithm.equals("EC", ignoreCase = true)) "SHA256withECDSA"
      else "SHA256withRSA"
    ).build(keyPair.private)
    return JcaX509CertificateConverter().getCertificate(builder.build(signer))
  }

  /** Build a [SigningKeyMaterial] with a self-signed EC cert. */
  fun makeKeyMaterial(commonName: String = "Pageboy Test"): SigningKeyMaterial {
    val kp = generateEcKeyPair()
    val cert = selfSignedCert(kp, commonName)
    return SigningKeyMaterial(
      privateKey = kp.private,
      certificateChain = arrayOf<Certificate>(cert),
      signatureAlgorithm = "SHA256withECDSA",
      displayName = commonName,
    )
  }

  /** Build an in-memory PKCS#12 keystore containing [material] under [alias] protected by [password]. */
  fun makePkcs12(
    material: SigningKeyMaterial,
    alias: String = "test",
    password: CharArray = "secret".toCharArray(),
  ): ByteArray {
    val ks = KeyStore.getInstance("PKCS12")
    ks.load(null, password)
    ks.setKeyEntry(alias, material.privateKey, password, material.certificateChain)
    val out = ByteArrayOutputStream()
    ks.store(out, password)
    return out.toByteArray()
  }
}
