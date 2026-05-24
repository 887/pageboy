package com.eight87.pageboy.format.pdf.signing

import java.security.PrivateKey
import java.security.cert.Certificate

/**
 * Phase H.5 — value type carrying everything the PAdES signer needs
 * (private key + cert chain). Both [KeystoreKeyProvider] and
 * [Pkcs12KeyProvider] return one of these; [PadesSigner] consumes
 * whichever the user picked.
 *
 * Living in its own file so the two providers + the signer all share
 * one type without circular imports (R.X.6).
 */
data class SigningKeyMaterial(
  val privateKey: PrivateKey,
  /** End-entity certificate first; intermediates / root after. */
  val certificateChain: Array<Certificate>,
  /** Signature algorithm to use with [privateKey]. e.g. "SHA256withECDSA", "SHA256withRSA". */
  val signatureAlgorithm: String,
  /**
   * Display label for the chrome's "Signed by …" surface. Pulled from
   * the cert's CN (Common Name) where available. Falls back to the
   * Subject DN's string form when CN extraction fails.
   */
  val displayName: String,
) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is SigningKeyMaterial) return false
    if (privateKey != other.privateKey) return false
    if (!certificateChain.contentEquals(other.certificateChain)) return false
    if (signatureAlgorithm != other.signatureAlgorithm) return false
    if (displayName != other.displayName) return false
    return true
  }

  override fun hashCode(): Int {
    var result = privateKey.hashCode()
    result = 31 * result + certificateChain.contentHashCode()
    result = 31 * result + signatureAlgorithm.hashCode()
    result = 31 * result + displayName.hashCode()
    return result
  }
}
