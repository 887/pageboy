package com.eight87.pageboy.data.library

/**
 * Phase B.6 — value object the scanner emits per file. The repository
 * turns these into [DocumentEntity] rows on insert; existing rows pick
 * up structural updates without losing per-document state.
 */
data class ScannedDocument(
  val documentId: String,
  val treeUriString: String,
  val relativePath: String,
  val documentUriString: String,
  val title: String,
  val fileName: String,
  val format: DocumentFormat,
  val sizeBytes: Long?,
  val mtimeMs: Long,
  val collection: String?,
)

/**
 * Output of one scan pass — everything the scanner observed across every
 * root, before the repository writes to Room.
 */
data class ScanSnapshot(
  val documents: List<ScannedDocument>,
)
