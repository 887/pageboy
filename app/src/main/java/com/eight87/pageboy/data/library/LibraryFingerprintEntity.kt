package com.eight87.pageboy.data.library

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Phase B.1 — per-root scan fingerprint. Stores the document count + max
 * mtime observed on the most recent scan so the rescan coordinator can
 * cheap-skip an unchanged root.
 *
 * Mirrors whisperboy's `LibraryFingerprintStore` shape (document count +
 * max last-modified), only the cheap skip is wired in B.7. The schema
 * exists from day one so v1 doesn't need a follow-up migration once we
 * activate the optimisation.
 */
@Entity(tableName = "library_fingerprints")
data class LibraryFingerprintEntity(
  @PrimaryKey val treeUriString: String,
  val documentCount: Int,
  val maxMtime: Long,
)
