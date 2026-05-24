package com.eight87.pageboy.data.library

import androidx.documentfile.provider.DocumentFile

/**
 * Phase B.5 — in-process cache around [DocumentFile]. SAF round-trips
 * are notoriously slow (every metadata call is a content-provider IPC)
 * and the scanner walks deep trees; caching `name` / `length` /
 * `lastModified` / `isDirectory` / `isFile` / `type` / `listFiles()`
 * turns a multi-second walk into a sub-second one for typical document
 * libraries.
 *
 * Direct adaptation of whisperboy's `CachedDocumentFile`. Cache is
 * per-instance — mutate the underlying tree (drop a file in via another
 * app) and a fresh wrapper is required to see the change. The scanner
 * constructs a fresh wrapper per rescan, which is the contract we want.
 *
 * Children are themselves [CachedDocumentFile] instances so the cache
 * extends through the tree.
 */
class CachedDocumentFile(private val raw: DocumentFile) {

  val uri get() = raw.uri

  val name: String? by lazy { raw.name }

  val length: Long by lazy { raw.length() }

  val lastModified: Long by lazy { raw.lastModified() }

  val isDirectory: Boolean by lazy { raw.isDirectory }

  val isFile: Boolean by lazy { raw.isFile }

  val type: String? by lazy { raw.type }

  val children: List<CachedDocumentFile> by lazy {
    raw.listFiles().map(::CachedDocumentFile)
  }
}
