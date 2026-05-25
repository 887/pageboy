package com.eight87.pageboy.data.annotation

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Phase G.1 — persisted row for a single PDF annotation. One row per
 * highlight / underline / strike / ink-stroke / sticky-note / stamp.
 *
 * Stored in Room (source of truth per `format-pdf.md` decision (C)) —
 * the original PDF bytes are never mutated; the OpenPDF "export with
 * annotations" path (G.6) bakes these rows into a new PDF when the
 * user explicitly chooses to.
 *
 * [documentId] matches `DocumentEntity.documentId` (SHA-256 of tree
 * URI + relative path) so annotations survive rescans + folder
 * remounts as long as the file stays put.
 *
 * Coordinates in [payloadJson] use PDF user-space (bottom-left origin,
 * points). [pageWidthPt] + [pageHeightPt] travel with the row so the
 * overlay can sanity-check it against the page it's rendering against
 * (used by `PdfCoordinates.kt` for the display-rect transform).
 *
 * [isDeleted] is a soft-delete flag. When the user removes an
 * annotation the row stays for one app session so an undo affordance
 * (Phase G+ polish) can restore it; a Phase G+ vacuum sweeps deleted
 * rows older than 7 days. v1 ships without the vacuum — the rows are
 * cheap and the UI hides them via the DAO query.
 */
@Entity(
  tableName = "annotations",
  indices = [
    Index(value = ["documentId"]),
    Index(value = ["documentId", "page_index"]),
  ],
)
data class AnnotationEntity(
  @PrimaryKey val id: String,

  /** Document this annotation belongs to. Matches `DocumentEntity.documentId`. */
  val documentId: String,

  /** 0-based page index inside the PDF. */
  @ColumnInfo(name = "page_index") val pageIndex: Int,

  /** Persisted [AnnotationKind.name]. */
  val kind: String,

  /** kotlinx.serialization-encoded [AnnotationPayload]. */
  @ColumnInfo(name = "payload_json") val payloadJson: String,

  /** ARGB color packed into an int. The high byte is alpha. */
  @ColumnInfo(name = "color_argb") val colorArgb: Int,

  /** Page width in PDF points the annotation was placed against. */
  @ColumnInfo(name = "page_width_pt") val pageWidthPt: Float,

  /** Page height in PDF points the annotation was placed against. */
  @ColumnInfo(name = "page_height_pt") val pageHeightPt: Float,

  /** Epoch ms the annotation was first placed. */
  @ColumnInfo(name = "created_at") val createdAt: Long,

  /** Epoch ms the annotation was last edited (color change / text edit). */
  @ColumnInfo(name = "modified_at") val modifiedAt: Long,

  /** Soft-delete flag — hidden from observe queries but row stays. */
  @ColumnInfo(name = "is_deleted") val isDeleted: Boolean = false,
)
