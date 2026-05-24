package com.eight87.pageboy.format.pdf.signing

import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.io.IOException
import java.io.InputStream
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Security
import java.security.cert.Certificate
import java.security.cert.X509Certificate

/**
 * Phase H.5 — PKCS#12 (`.p12` / `.pfx`) key source. The qualified
 * signing path: user imports a `.p12` they got from a Trust Service
 * Provider (national identity card software, employer PKI, ACME-issued
 * cert), pageboy parses it via Bouncy Castle's `KeyStore.getInstance("PKCS12", "BC")`,
 * extracts the first private-key alias + its cert chain.
 *
 * **Per-session password.** The password is taken as a `CharArray`
 * (zeroed on the caller's side after the call), held only for the
 * duration of [load], and never persisted. Re-signing in the same
 * session prompts again.
 *
 * **Why Bouncy Castle's PKCS#12 (not the JCE default).** Android's
 * built-in `KeyStore.getInstance("PKCS12")` works for vanilla `.p12`
 * files but stumbles on the more permissive shape some CAs ship
 * (RC2-CBC inner encryption, missing friendlyName, mac-less files).
 * Bouncy Castle's reader is the lowest-friction route — same one
 * iText / OpenPDF / PdfBox use.
 *
 * **Hardware-backed key detection.** Some `.p12` files wrap a
 * non-exportable key reference (the actual private operation happens
 * in a smart card / TPM). Bouncy Castle returns a placeholder
 * `PrivateKey` that throws on `Signature.sign()` — we don't try to
 * detect this up front (no portable way) but [PadesSigner] catches
 * the runtime failure and surfaces a friendly error per the
 * format-pdf.md §3.2 crash-surface analysis.
 */
class Pkcs12KeyProvider {

  /**
   * Load a PKCS#12 keystore from [stream], extract the first
   * private-key alias, return its [SigningKeyMaterial].
   *
   * @throws IOException on malformed bytes / wrong password / no
   *   private-key alias present.
   */
  fun load(stream: InputStream, password: CharArray): SigningKeyMaterial {
    ensureBouncyCastleRegistered()
    // Bouncy Castle's PKCS12 provider tolerates the dialects iText /
    // OpenPDF historically chose to ship across. The JCE default's
    // PKCS12KeyStoreSpi is fine for vanilla files but throws on
    // RC2-CBC inner encryption (common in older smart-card `.p12`s).
    val ks = KeyStore.getInstance("PKCS12", BouncyCastleProvider.PROVIDER_NAME)
    try {
      ks.load(stream, password)
    } catch (t: Throwable) {
      throw IOException("Could not open PKCS#12 keystore: ${t.message}", t)
    }
    val alias = pickFirstPrivateKeyAlias(ks)
      ?: throw IOException("PKCS#12 keystore has no private-key alias")
    val key: PrivateKey = (ks.getKey(alias, password) as? PrivateKey)
      ?: throw IOException("Alias '$alias' is not a private key")
    val chainRaw: Array<Certificate>? = ks.getCertificateChain(alias)
    val chain: Array<Certificate> = chainRaw
      ?.takeIf { it.isNotEmpty() }
      ?: throw IOException("PKCS#12 keystore is missing the certificate chain")
    val end = chain[0] as? X509Certificate
      ?: throw IOException("End-entity certificate is not X.509")
    val display = extractDisplayName(end)
    val sigAlg = pickSignatureAlgorithm(key)
    return SigningKeyMaterial(
      privateKey = key,
      certificateChain = chain,
      signatureAlgorithm = sigAlg,
      displayName = display,
    )
  }

  internal fun pickFirstPrivateKeyAlias(ks: KeyStore): String? {
    val aliases = ks.aliases()
    while (aliases.hasMoreElements()) {
      val alias = aliases.nextElement()
      if (ks.isKeyEntry(alias)) return alias
    }
    return null
  }

  /** First-fit signature alg by private key type. RSA → SHA-256, EC → SHA-256, DSA → SHA-256. */
  internal fun pickSignatureAlgorithm(key: PrivateKey): String = when (key.algorithm.uppercase()) {
    "EC", "ECDSA" -> "SHA256withECDSA"
    "RSA" -> "SHA256withRSA"
    "DSA" -> "SHA256withDSA"
    else -> "SHA256with${key.algorithm}"
  }

  /**
   * Pull a presentable display name out of the cert subject DN.
   * Prefer CN; fall back to the whole DN. Mirrors what Adobe Reader
   * surfaces for "Signed by: …" on a PAdES-signed PDF.
   */
  internal fun extractDisplayName(cert: X509Certificate): String {
    val dn = cert.subjectX500Principal.name
    val cn = dn.splitToSequence(',')
      .map { it.trim() }
      .firstOrNull { it.startsWith("CN=", ignoreCase = true) }
      ?.removePrefix("CN=")
      ?.removePrefix("cn=")
      ?.trim()
    return cn?.takeIf { it.isNotEmpty() } ?: dn
  }

  companion object {
    /**
     * Idempotent — pageboy may call this from multiple entry points
     * (AppGraph init, Pkcs12KeyProvider.load, KeystoreKeyProvider's
     * self-signed cert builder). `Security.addProvider` returns -1
     * when the provider is already registered; we tolerate that.
     */
    fun ensureBouncyCastleRegistered() {
      if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
        Security.addProvider(BouncyCastleProvider())
      }
    }
  }
}
