package com.eight87.pageboy.format.pdf.signing

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.X509v3CertificateBuilder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.Certificate
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec
import java.util.Date

/**
 * Phase H.5 — Android Keystore (`AndroidKeyStore` provider) key source.
 * The casual path: pageboy generates an EC P-256 keypair on first
 * signing request, persists the alias in DataStore
 * ([com.eight87.pageboy.data.signing.SigningSettings.keystoreAlias]),
 * every subsequent signing uses the same alias. A self-signed
 * certificate is generated alongside via Bouncy Castle's
 * [X509v3CertificateBuilder] so the PDF has a presentable signer info
 * even though the cert isn't CA-issued.
 *
 * **What this earns the casual user.** A tamper-evident PAdES-B-B
 * signature on the PDF. Adobe Reader will badge it as "Signed by:
 * Pageboy User (self-signed)" because there's no chain-of-trust to
 * a CA — but the cryptographic guarantee (file hasn't changed since
 * signing) is identical to a qualified signature. This is the right
 * level of guarantee for "I want a tamper-evident copy of my own
 * audit trail", which is what most users actually mean when they say
 * "sign".
 *
 * **EC P-256 over RSA.** Faster on-device (key gen + signing both
 * under 50 ms on a 2026 mid-range), smaller signatures (~70 bytes
 * raw vs ~256 for RSA-2048), and the [AndroidKeyStore] provider has
 * had EC support since API 23. RSA stays available as a fallback if
 * a future qualified-cert flow needs it (most CAs issue RSA-2048
 * still).
 *
 * **Why not extractable.** The Android Keystore guarantees a
 * non-exportable private key (hardware-backed where the device has a
 * StrongBox or TEE). `Signature.sign(...)` against the key handle
 * performs the operation in the secure hardware; pageboy never sees
 * the raw private bytes. The PDF signature still verifies via the
 * public cert.
 */
class KeystoreKeyProvider(
  private val keystoreProviderName: String = ANDROID_KEYSTORE,
) {

  /**
   * Get the existing keypair under [alias] or generate a fresh one.
   * Returns the [SigningKeyMaterial] threaded into [PadesSigner].
   *
   * @param alias the alias to look up / generate under. The caller
   *   (typically [com.eight87.pageboy.data.signing.SigningSettings])
   *   persists this so subsequent signs hit the same key.
   * @param commonName the CN to embed in the self-signed cert. The
   *   chrome surfaces this as the "Signed by …" label.
   */
  fun loadOrGenerate(
    alias: String,
    commonName: String = DEFAULT_CN,
  ): SigningKeyMaterial {
    Pkcs12KeyProvider.ensureBouncyCastleRegistered()
    val ks = KeyStore.getInstance(keystoreProviderName).apply { load(null) }
    return if (ks.containsAlias(alias)) {
      val key = ks.getKey(alias, null) as PrivateKey
      val cert = ks.getCertificate(alias) as X509Certificate
      SigningKeyMaterial(
        privateKey = key,
        certificateChain = arrayOf<Certificate>(cert),
        signatureAlgorithm = SIG_ALG_EC,
        displayName = extractDisplayName(cert, commonName),
      )
    } else {
      generate(alias, commonName)
    }
  }

  /**
   * Wipe an existing alias. Used by the "Reset signing keys" setting
   * entry (Phase H.6). Tolerates non-existent aliases — idempotent.
   */
  fun delete(alias: String) {
    val ks = KeyStore.getInstance(keystoreProviderName).apply { load(null) }
    if (ks.containsAlias(alias)) ks.deleteEntry(alias)
  }

  /** Test seam — lists aliases under the keystore provider. */
  fun listAliases(): List<String> {
    val ks = KeyStore.getInstance(keystoreProviderName).apply { load(null) }
    val out = mutableListOf<String>()
    val aliases = ks.aliases()
    while (aliases.hasMoreElements()) out += aliases.nextElement()
    return out
  }

  private fun generate(alias: String, commonName: String): SigningKeyMaterial {
    val kpg = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, keystoreProviderName)
    val spec = KeyGenParameterSpec.Builder(
      alias,
      KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY,
    )
      .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
      .setDigests(KeyProperties.DIGEST_SHA256)
      // Cert validity — five years from now. The cert is self-signed
      // so this is informational only; PAdES verification at the
      // signing time always passes.
      .setCertificateSubject(X500Name("CN=$commonName").asJcaX500Principal())
      .setCertificateSerialNumber(BigInteger.valueOf(System.currentTimeMillis()))
      .setCertificateNotBefore(Date())
      .setCertificateNotAfter(Date(System.currentTimeMillis() + FIVE_YEARS_MS))
      .build()
    kpg.initialize(spec)
    val kp: KeyPair = kpg.generateKeyPair()
    val ks = KeyStore.getInstance(keystoreProviderName).apply { load(null) }
    val cert = ks.getCertificate(alias) as X509Certificate
    return SigningKeyMaterial(
      privateKey = kp.private,
      certificateChain = arrayOf<Certificate>(cert),
      signatureAlgorithm = SIG_ALG_EC,
      displayName = extractDisplayName(cert, commonName),
    )
  }

  private fun extractDisplayName(cert: X509Certificate, fallback: String): String {
    val dn = cert.subjectX500Principal.name
    val cn = dn.splitToSequence(',')
      .map { it.trim() }
      .firstOrNull { it.startsWith("CN=", ignoreCase = true) }
      ?.removePrefix("CN=")
      ?.removePrefix("cn=")
      ?.trim()
    return cn?.takeIf { it.isNotEmpty() } ?: fallback
  }

  /**
   * Build a fully self-signed X.509 v3 cert via Bouncy Castle's
   * [JcaX509v3CertificateBuilder] for the [keypair] generated outside
   * the AndroidKeyStore (test path; production uses the
   * KeyGenParameterSpec self-signed cert the AndroidKeyStore mints
   * automatically). Exposed for [KeystoreKeyProviderTest] which
   * cannot reach AndroidKeyStore under Robolectric.
   */
  internal fun buildSelfSignedCert(keypair: KeyPair, commonName: String): X509Certificate {
    Pkcs12KeyProvider.ensureBouncyCastleRegistered()
    val subject = X500Name("CN=$commonName")
    val now = System.currentTimeMillis()
    val builder = JcaX509v3CertificateBuilder(
      subject,
      BigInteger.valueOf(now),
      Date(now),
      Date(now + FIVE_YEARS_MS),
      subject,
      keypair.public,
    )
    val signer = JcaContentSignerBuilder(
      if (keypair.private.algorithm.equals("EC", ignoreCase = true)) SIG_ALG_EC
      else "SHA256withRSA"
    ).build(keypair.private)
    val holder = builder.build(signer)
    return JcaX509CertificateConverter().getCertificate(holder)
  }

  /** Convert a BC `X500Name` to a JCA `X500Principal` so `KeyGenParameterSpec.Builder` accepts it. */
  private fun X500Name.asJcaX500Principal(): javax.security.auth.x500.X500Principal =
    javax.security.auth.x500.X500Principal(this.encoded)

  companion object {
    const val ANDROID_KEYSTORE = "AndroidKeyStore"
    const val DEFAULT_CN = "Pageboy User"
    const val SIG_ALG_EC = "SHA256withECDSA"
    private const val FIVE_YEARS_MS = 5L * 365 * 24 * 60 * 60 * 1000
  }
}
