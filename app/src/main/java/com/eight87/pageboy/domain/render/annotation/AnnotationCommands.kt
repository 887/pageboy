package com.eight87.pageboy.domain.render.annotation

import com.eight87.pageboy.data.annotation.AnnotationEntity
import com.eight87.pageboy.data.annotation.AnnotationKind
import com.eight87.pageboy.data.annotation.AnnotationPayload
import kotlinx.coroutines.flow.StateFlow

/**
 * Phase G.3 — narrow per-reader command surface for annotation
 * authoring. Closes the Phase C deferral (`AnnotationCommands` was
 * named in refactor-solid.md R.C.1 but deliberately not declared
 * until Phase G consumed it, per R.X.5 — no Liskov-stub interfaces).
 *
 * Lives in `domain/render/annotation/` — the neutral package Phase E
 * established for cross-layer contracts. Both `format/pdf/` (overlay)
 * and `ui/reader/control/` (concrete impl) import this without
 * breaking R.X.6.
 *
 * Consumed by `PdfAnnotationOverlay` (toolbar + gesture handling) +
 * the reader's overflow menu (Phase G.4 wires the toolbar + the
 * "Export with annotations…" entry).
 *
 * Lifecycle is per-reader (matches the `InMemoryFindInDocCommands`
 * lifecycle the chrome already uses); not AppGraph-scoped so tool /
 * color state doesn't leak across documents.
 */
interface AnnotationCommands {

  val state: StateFlow<AnnotationToolState>

  /** Persist a freshly-authored annotation. */
  suspend fun add(annotation: AnnotationEntity)

  /** Soft-delete an annotation by id. */
  suspend fun remove(id: String)

  /** Switch the currently-active authoring tool. Null disables authoring. */
  fun setTool(tool: AnnotationTool?)

  /** Update the currently-active color (ARGB-packed Int). */
  fun setColor(colorArgb: Int)

  /** Mark a sticky note's text edit (kind-specific helper for the bottom sheet). */
  suspend fun updateStickyNote(id: String, text: String)
}

/**
 * Phase G.3 — closed authoring-tool enum. One-to-one mapping with
 * [AnnotationKind] in v1; an enum (not a sealed type) because each
 * tool has no per-variant state beyond what already lives on
 * [AnnotationToolState].
 */
enum class AnnotationTool {
  Highlight,
  Underline,
  Strikethrough,
  FreehandInk,
  StickyNote,
  Stamp,
}

/**
 * Phase G.3 — authoring toolbar state. Reads as a single
 * [StateFlow] so the toolbar composable + the overlay composable both
 * see the same instant snapshot.
 */
data class AnnotationToolState(
  val tool: AnnotationTool? = null,
  val colorArgb: Int = DEFAULT_HIGHLIGHT_ARGB,
) {
  companion object {
    /** Translucent yellow — the canonical highlight color most PDF readers ship. */
    const val DEFAULT_HIGHLIGHT_ARGB: Int = 0x66FFEB3B.toInt()
  }
}

/** Map the active [AnnotationTool] to its persisted [AnnotationKind]. */
fun AnnotationTool.toKind(): AnnotationKind = when (this) {
  AnnotationTool.Highlight -> AnnotationKind.Highlight
  AnnotationTool.Underline -> AnnotationKind.Underline
  AnnotationTool.Strikethrough -> AnnotationKind.Strikethrough
  AnnotationTool.FreehandInk -> AnnotationKind.FreehandInk
  AnnotationTool.StickyNote -> AnnotationKind.StickyNote
  AnnotationTool.Stamp -> AnnotationKind.Stamp
}

/** Validate that a payload matches its declared kind (defence in depth). */
fun AnnotationPayload.matches(kind: AnnotationKind): Boolean = when (this) {
  is AnnotationPayload.HighlightPayload -> kind == AnnotationKind.Highlight
  is AnnotationPayload.UnderlinePayload -> kind == AnnotationKind.Underline
  is AnnotationPayload.StrikethroughPayload -> kind == AnnotationKind.Strikethrough
  is AnnotationPayload.FreehandInkPayload -> kind == AnnotationKind.FreehandInk
  is AnnotationPayload.StickyNotePayload -> kind == AnnotationKind.StickyNote
  is AnnotationPayload.StampPayload -> kind == AnnotationKind.Stamp
}
