package com.eight87.pageboy.data.library

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.eight87.pageboy.data.library.DocumentSourceCodec
import com.eight87.pageboy.data.library.DocumentSourceKind

/**
 * Phase B.1 — persisted row for a single document found in one of the
 * user's [LibraryRoot]s.
 *
 * The [documentId] is a stable SHA-256 of `<treeUri>#<relativePath>` so
 * the same document at the same path keeps its pin / read-position /
 * last-opened state across rescans.
 *
 * Per-document state (last-read position, pinned, lastOpenedAt) lives
 * directly on this row — single source of truth, same pattern whisperboy
 * uses for per-book state. There is no parallel `recents` or `pinned`
 * table; those tabs in the UI are projections over this entity.
 */
@Entity(
  tableName = "documents",
  indices = [
    Index(value = ["treeUriString"]),
    Index(value = ["last_opened_at"]),
    Index(value = ["format"]),
    Index(value = ["collection"]),
  ],
)
data class DocumentEntity(
  @PrimaryKey val documentId: String,

  /** The [LibraryRoot] this document was scanned from (the canonical persistable URI). */
  val treeUriString: String,

  /** Path within the root tree, identifying the file or sub-folder. */
  val relativePath: String,

  /** SAF document URI used to open the file. */
  val documentUriString: String,

  /** Display title (filename without extension, by default). */
  val title: String,

  /** Original filename including extension. */
  val fileName: String,

  /** Canonical [DocumentFormat] name. Persisted as a String via the TypeConverter. */
  val format: String,

  /** File size in bytes; null when unknown. */
  @ColumnInfo(name = "size_bytes") val sizeBytes: Long? = null,

  /** Last-modified epoch ms reported by SAF. */
  @ColumnInfo(name = "mtime_ms") val mtimeMs: Long = 0L,

  /** Optional collection (subfolder name in Root mode, category name in Category mode, root display name in SingleFolder). */
  val collection: String? = null,

  /** Epoch ms the row was first persisted. */
  @ColumnInfo(name = "added_at") val addedAt: Long = 0L,

  /** Epoch ms the user last opened the document. Null means never opened. */
  @ColumnInfo(name = "last_opened_at") val lastOpenedAt: Long? = null,

  /**
   * Where the user left off reading. 0 = un-started. Renderer-specific
   * semantics in Phase C+. Phase F migration v1→v2 introduces
   * [scrollPositionJson] as the new source of truth; this column stays
   * for the legacy bit-packed encoding so v1 rows continue to decode
   * cleanly when the JSON column is null.
   */
  @ColumnInfo(name = "last_read_position_ms") val lastReadPositionMs: Long = 0L,

  /**
   * Fraction-complete estimate, 0.0-1.0. Renderer-specific (PDF page count,
   * EPUB spine position, text scroll fraction). 0.0 = un-started.
   */
  @ColumnInfo(name = "read_fraction") val readFraction: Float = 0f,

  /**
   * Phase F.2 — JSON-encoded `ScrollPosition` sealed variant. Null for
   * documents the user hasn't scrolled yet, AND for v1 rows that still
   * carry their position in the legacy [lastReadPositionMs] +
   * [readFraction] columns (the chrome's `ScrollPersistence` decode
   * path falls back to the legacy encoding when this column is null).
   *
   * One TEXT column rather than two (`kind` + `payload`) so future
   * variants (EPUB CFI at Phase M) don't require another schema
   * migration — only the JSON shape changes.
   */
  @ColumnInfo(name = "scroll_position_json") val scrollPositionJson: String? = null,

  /** User explicitly pinned this document via the overflow menu. */
  val pinned: Boolean = false,

  /**
   * Soft-delete flag. Set to `true` when the document disappears from the
   * source on rescan; preserves per-document state so a re-add of the same
   * folder restores everything. Hard-delete only happens when the user
   * explicitly removes a root, which is owned by the repository.
   */
  @ColumnInfo(name = "is_missing") val isMissing: Boolean = false,

  /**
   * Phase N.5 — JSON-encoded [DocumentSourceKind] sealed variant. Null
   * for rows scanned by Phase B before Phase N landed; the migration
   * (v3 → v4) leaves the column nullable + treats null as
   * `LibraryRoot(treeUriString)` for backward-compatible reads
   * ([toSourceKind] below). New rows ALWAYS write a value: either
   * `LibraryRoot(rootTreeUriString)` for scanned docs or
   * `AdHocOpen(uri, ephemeral)` for documents created by
   * `OpenWithActivity` ingest.
   */
  @ColumnInfo(name = "source_json") val sourceJson: String? = null,
) {
  /**
   * Phase N — decode the JSON-encoded source kind for this row. Treats a
   * null/empty column as the implicit `LibraryRoot(treeUriString)`
   * fallback so v1/v2/v3 rows continue to read cleanly without a
   * one-shot backfill.
   */
  fun toSourceKind(): DocumentSourceKind =
    DocumentSourceCodec.decode(sourceJson)
      ?: DocumentSourceKind.LibraryRoot(rootTreeUriString = treeUriString)
}
