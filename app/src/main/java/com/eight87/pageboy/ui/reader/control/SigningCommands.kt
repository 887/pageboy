package com.eight87.pageboy.ui.reader.control

import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Phase H.2 — narrow per-axis controller for the signing UX. Closes the
 * Phase C stub recorded in `refactor-solid.md` R.C.1 (the deferred
 * `SignatureCommands` interface from the R.C controller-split pattern).
 *
 * **Narrow surface (R.X.1 / R.X.7).** The reader chrome and the sign
 * bottom sheet read [state] + call [start] / [commit] / [cancel]. They
 * do NOT see the PadesSigner, the KeystoreKeyProvider, or any
 * cryptographic primitive — those live in `format/pdf/signing/` and are
 * driven by an impl-side adapter that subscribes to the state flow.
 *
 * **Two flows on one entry point.** The reader's "Sign…" overflow item
 * opens a bottom sheet which calls [start] with `visualStamp = true`
 * for the easy path (signature-pad → place-on-PDF → burn-in via
 * OpenPDF) or `visualStamp = false` for the cryptographic path
 * (PAdES-B-B via OpenPDF `PdfSignatureAppearance` + Bouncy Castle
 * CMS). The state flow drives the bottom-sheet sub-screens (key
 * picker, p12 password, signing-progress, success / failure).
 *
 * **Per-reader lifecycle.** One [SigningCommands] per open PDF document
 * (lifecycle matches the reader screen instance, mirroring
 * [FindInDocCommands]'s scoping). Cancelling navigates the sheet back
 * to idle; leaving the reader disposes the instance.
 *
 * Phase H ships PAdES-B-B (basic). PAdES-B-T (timestamp) + PAdES-B-LT
 * (long-term validation) are reserved (greyed-out settings entries)
 * per format-pdf.md.
 */
interface SigningCommands {

  val state: StateFlow<SigningState>

  /**
   * Begin a signing session for the currently-open PDF.
   *
   * @param visualStamp when `true`, opens the freehand signature pad
   *   for a visual stamp (Phase H.4). When `false`, opens the
   *   cryptographic key-source picker (Phase H.5).
   */
  fun start(visualStamp: Boolean)

  /**
   * Commit the in-progress signing operation. The bytes are the
   * format-encoded result the chrome wants to surface — for the
   * visual-stamp path, the PNG of the captured signature; for the
   * cryptographic path, an opaque token (currently unused, reserved
   * for future smart-card-aware flows). The impl writes the
   * stamped / signed PDF to a SAF target the user picks; the
   * post-commit state carries that URI in [SigningState.Success].
   */
  fun commit(signatureBytes: ByteArray)

  /** Abort the session — flips state back to [SigningState.Idle]. */
  fun cancel()
}

/**
 * Sealed UX-driving state. The bottom-sheet renderer dispatches on this;
 * the per-format renderer is otherwise unaware of the signing surface
 * (`format/pdf/signing/` consumers subscribe explicitly).
 */
sealed interface SigningState {
  /** No signing session active. The "Sign…" overflow entry opens the sheet. */
  data object Idle : SigningState

  /**
   * Visual-stamp path — the user is drawing on the signature pad
   * (Phase H.4). On commit pageboy converts to a transparent PNG and
   * advances to [PlacingStamp].
   */
  data object DrawingStamp : SigningState

  /**
   * Visual-stamp path — the user has captured a signature and is
   * tap-to-placing it on a PDF page. The carried PNG is the
   * transparent-background bitmap rendered above the page surface.
   */
  data class PlacingStamp(val pngBytes: ByteArray) : SigningState {
    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (other !is PlacingStamp) return false
      return pngBytes.contentEquals(other.pngBytes)
    }
    override fun hashCode(): Int = pngBytes.contentHashCode()
  }

  /**
   * Cryptographic path — the user is picking a key source
   * (Android Keystore EC P-256 vs imported PKCS#12). The casual /
   * qualified split lives in the sheet, not in the state.
   */
  data object KeySelecting : SigningState

  /**
   * Cryptographic path — pageboy is running the signing pipeline
   * (PAdES-B-B via OpenPDF + Bouncy Castle). Determinate progress
   * 0..1 ; the chrome renders a progress bar.
   */
  data class Signing(val progress: Float) : SigningState

  /** Both paths — operation failed. Reason is a user-facing string. */
  data class Failed(val reason: String) : SigningState

  /**
   * Both paths — signed/stamped PDF written to [savedUri]. The chrome
   * shows a snackbar with an "Open" affordance.
   */
  data class Success(val savedUri: Uri) : SigningState
}

/**
 * Default in-memory [SigningCommands]. Holds only the state flow + the
 * minimal transition rules — actual signing work (PNG render, OpenPDF
 * stamp burn-in, PAdES CMS pipeline) lives in `format/pdf/signing/`
 * and is plumbed in via the AppGraph adapter wiring. This split keeps
 * the chrome free of cryptographic imports (R.X.6).
 */
class InMemorySigningCommands : SigningCommands {

  private val _state = MutableStateFlow<SigningState>(SigningState.Idle)
  override val state: StateFlow<SigningState> = _state.asStateFlow()

  override fun start(visualStamp: Boolean) {
    _state.value = if (visualStamp) SigningState.DrawingStamp
    else SigningState.KeySelecting
  }

  override fun commit(signatureBytes: ByteArray) {
    val current = _state.value
    _state.value = when (current) {
      SigningState.DrawingStamp -> SigningState.PlacingStamp(signatureBytes)
      else -> SigningState.Signing(progress = 0f)
    }
  }

  override fun cancel() {
    _state.value = SigningState.Idle
  }

  /**
   * Impl-side hook for the signing adapter to drive the success /
   * failure / progress edges. Not on the [SigningCommands] interface
   * — only the per-format wiring sees the concrete class.
   */
  fun reportProgress(progress: Float) {
    if (_state.value is SigningState.Signing) {
      _state.value = SigningState.Signing(progress.coerceIn(0f, 1f))
    }
  }

  /** Impl-side success hook. */
  fun reportSuccess(savedUri: Uri) {
    _state.value = SigningState.Success(savedUri)
  }

  /** Impl-side failure hook. */
  fun reportFailure(reason: String) {
    _state.value = SigningState.Failed(reason)
  }

  /** Reset to idle without disposing the instance (e.g. after dismissing a Success snackbar). */
  fun reset() {
    _state.value = SigningState.Idle
  }
}
