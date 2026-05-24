package com.eight87.pageboy.data.library

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

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

  /** Where the user left off reading. 0 = un-started. Renderer-specific semantics in Phase C+. */
  @ColumnInfo(name = "last_read_position_ms") val lastReadPositionMs: Long = 0L,

  /**
   * Fraction-complete estimate, 0.0-1.0. Renderer-specific (PDF page count,
   * EPUB spine position, text scroll fraction). 0.0 = un-started.
   */
  @ColumnInfo(name = "read_fraction") val readFraction: Float = 0f,

  /** User explicitly pinned this document via the overflow menu. */
  val pinned: Boolean = false,

  /**
   * Soft-delete flag. Set to `true` when the document disappears from the
   * source on rescan; preserves per-document state so a re-add of the same
   * folder restores everything. Hard-delete only happens when the user
   * explicitly removes a root, which is owned by the repository.
   */
  @ColumnInfo(name = "is_missing") val isMissing: Boolean = false,
)
