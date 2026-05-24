package com.eight87.pageboy.format.registry

import com.eight87.pageboy.data.library.DocumentFormat
import com.eight87.pageboy.format.api.DocumentRenderer
import com.eight87.pageboy.format.placeholder.PlaceholderRenderer

/**
 * Phase C.2 / R.X.9 — open/closed dispatch from [DocumentFormat] to the
 * concrete [DocumentRenderer] that handles it.
 *
 * **The reader never branches on format.** It asks the registry "give me
 * the renderer for `Pdf`" and gets back whatever the [AppGraph] wired —
 * the real PDF renderer once Phase F ships, the [PlaceholderRenderer]
 * otherwise. Adding a new format = wire a new entry; the reader is
 * untouched.
 */
interface FormatRegistry {

  /**
   * Renderer for the given format. Implementations MUST always return a
   * non-null renderer — the [PlaceholderRenderer] fallback in
   * [CompiledFormatRegistry] guarantees this contract holds for formats
   * that don't yet have a real impl.
   */
  fun rendererFor(format: DocumentFormat): DocumentRenderer
}

/**
 * Phase C.2 — compile-time registry. Built once from the AppGraph with
 * whatever per-format renderers ship; every unmapped format dispatches
 * through a per-format [PlaceholderRenderer] so the reader UI stays
 * exercisable end-to-end even before Phase D–M land real renderers.
 *
 * The fallback caches per-format placeholder instances so requests for
 * the same format return the same renderer (cheap micro-optimisation;
 * mostly relevant for Compose recomposition stability).
 */
class CompiledFormatRegistry(
  private val renderers: Map<DocumentFormat, DocumentRenderer>,
) : FormatRegistry {

  private val placeholders: MutableMap<DocumentFormat, PlaceholderRenderer> = HashMap()

  override fun rendererFor(format: DocumentFormat): DocumentRenderer {
    renderers[format]?.let { return it }
    return synchronized(placeholders) {
      placeholders.getOrPut(format) { PlaceholderRenderer(format) }
    }
  }
}
