package com.eight87.pageboy.data.library

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Phase B.6 — walks each [LibraryRoot]'s SAF tree via [CachedDocumentFile]
 * and dispatches by [FolderType] via exhaustive `when`. Returns a
 * [ScanSnapshot] for [LibraryRepository.applyScan] to write into Room in
 * one transaction.
 *
 * Per-document fields filled by the structural walk:
 *  - `documentId` — SHA-256 of `<treeUri>#<relativePath>` for stable
 *    identity across rescans.
 *  - `relativePath` — path inside the picked tree, used as the natural
 *    key the user understands ("Books/Fiction/dune.epub").
 *  - `title` — filename without extension by default. Real per-format
 *    title extraction (EPUB OPF metadata, PDF info dictionary, DOCX
 *    core properties) lives in Phase D+ renderers and writes back via
 *    the repository.
 *  - `format` — classified by [DocumentClassifier].
 *  - `collection` — derived from folder layout per [FolderType] (see
 *    branches below).
 *
 * Stable identifiers: [documentIdFor] hashes `<treeUriString>#<relativePath>`
 * so the same document at the same path keeps its row across rescans.
 *
 * Hidden files (leading `.`) are skipped unless [includeHiddenProvider]
 * returns true — wired to the Library settings toggle (B.15).
 */
class SafLibraryScanner(
  private val context: Context,
  private val classifier: DocumentClassifier = DocumentClassifier,
  private val includeHiddenProvider: () -> Boolean = { false },
) : LibraryScanner {

  override suspend fun scan(
    roots: List<LibraryRoot>,
    onProgress: suspend (documentsFound: Int, currentFolder: String?) -> Unit,
  ): ScanSnapshot = withContext(Dispatchers.IO) {
    val includeHidden = includeHiddenProvider()
    val accumulated = mutableListOf<ScannedDocument>()
    var documentsFound = 0
    suspend fun emit(currentFolder: String?) {
      runCatching { onProgress(documentsFound, currentFolder) }
    }
    for (root in roots) {
      scanRoot(root, includeHidden) { discovered, folder ->
        accumulated += discovered
        documentsFound += discovered.size
        emit(folder)
      }
    }
    ScanSnapshot(accumulated)
  }

  private suspend fun scanRoot(
    root: LibraryRoot,
    includeHidden: Boolean,
    onBatch: suspend (docs: List<ScannedDocument>, currentFolder: String?) -> Unit,
  ) {
    val tree = DocumentFile.fromTreeUri(context, root.treeUri) ?: return
    val cached = CachedDocumentFile(tree)
    when (root.folderType) {
      FolderType.SingleFile -> {
        val docs = scanSingleFile(root, cached)
        if (docs.isNotEmpty()) onBatch(docs, cached.name)
      }
      FolderType.SingleFolder -> {
        val docs = scanFlat(
          root = root,
          folder = cached,
          relativePrefix = "",
          collection = cached.name ?: root.displayName,
          includeHidden = includeHidden,
        )
        if (docs.isNotEmpty()) onBatch(docs, cached.name)
      }
      FolderType.Root -> {
        // Recursive walk; top-level subfolder name is the collection;
        // nested folders nest into the same collection.
        scanRecursive(
          root = root,
          folder = cached,
          relativePrefix = "",
          collection = null,
          includeHidden = includeHidden,
          onBatch = onBatch,
          collectionFromTopLevel = true,
        )
      }
      FolderType.Category -> {
        // Top-level subfolders are categories; documents inside each get
        // tagged with that category. Files at the root level are dropped
        // (they have no category by definition in this mode).
        cached.children
          .filter { it.isDirectory && (includeHidden || it.name?.startsWith('.') != true) }
          .forEach { categoryFolder ->
            val categoryName = categoryFolder.name ?: ""
            scanRecursive(
              root = root,
              folder = categoryFolder,
              relativePrefix = "$categoryName/",
              collection = categoryName,
              includeHidden = includeHidden,
              onBatch = onBatch,
              collectionFromTopLevel = false,
            )
          }
      }
    }
  }

  private fun scanSingleFile(
    root: LibraryRoot,
    file: CachedDocumentFile,
  ): List<ScannedDocument> {
    if (!file.isFile) return emptyList()
    val name = file.name ?: return emptyList()
    return listOf(buildDocument(root, file, name, name, collection = null))
  }

  /** Flat: every supported file directly in [folder], no recursion. */
  private fun scanFlat(
    root: LibraryRoot,
    folder: CachedDocumentFile,
    relativePrefix: String,
    collection: String?,
    includeHidden: Boolean,
  ): List<ScannedDocument> {
    return folder.children
      .filter { it.isFile && (includeHidden || it.name?.startsWith('.') != true) }
      .mapNotNull { file ->
        val name = file.name ?: return@mapNotNull null
        buildDocument(
          root = root,
          file = file,
          fileName = name,
          relativePath = "$relativePrefix$name",
          collection = collection,
        )
      }
  }

  /**
   * Recursive walker. Emits in batches per directory so the progress
   * banner can tick alongside the walk. When [collectionFromTopLevel] is
   * true (Root mode), the first-level subfolder name becomes the
   * collection and propagates to every nested document; otherwise
   * [collection] is used as-is (Category mode passes the category
   * directly).
   */
  private suspend fun scanRecursive(
    root: LibraryRoot,
    folder: CachedDocumentFile,
    relativePrefix: String,
    collection: String?,
    includeHidden: Boolean,
    onBatch: suspend (docs: List<ScannedDocument>, currentFolder: String?) -> Unit,
    collectionFromTopLevel: Boolean,
  ) {
    val directDocs = folder.children
      .filter { it.isFile && (includeHidden || it.name?.startsWith('.') != true) }
      .mapNotNull { file ->
        val name = file.name ?: return@mapNotNull null
        buildDocument(
          root = root,
          file = file,
          fileName = name,
          relativePath = "$relativePrefix$name",
          collection = collection,
        )
      }
    if (directDocs.isNotEmpty()) onBatch(directDocs, folder.name)
    folder.children
      .filter { it.isDirectory && (includeHidden || it.name?.startsWith('.') != true) }
      .forEach { sub ->
        val subName = sub.name ?: return@forEach
        val nextCollection = when {
          collectionFromTopLevel && collection == null -> subName
          else -> collection
        }
        scanRecursive(
          root = root,
          folder = sub,
          relativePrefix = "$relativePrefix$subName/",
          collection = nextCollection,
          includeHidden = includeHidden,
          onBatch = onBatch,
          collectionFromTopLevel = collectionFromTopLevel,
        )
      }
  }

  private fun buildDocument(
    root: LibraryRoot,
    file: CachedDocumentFile,
    fileName: String,
    relativePath: String,
    collection: String?,
  ): ScannedDocument {
    val docId = documentIdFor(root.treeUri.toString(), relativePath)
    val format = classifier.classify(fileName) {
      runCatching { context.contentResolver.openInputStream(file.uri) }.getOrNull()
    }
    return ScannedDocument(
      documentId = docId,
      treeUriString = root.treeUri.toString(),
      relativePath = relativePath,
      documentUriString = file.uri.toString(),
      title = fileName.substringBeforeLast('.'),
      fileName = fileName,
      format = format,
      sizeBytes = file.length.takeIf { it > 0 },
      mtimeMs = file.lastModified,
      collection = collection,
    )
  }

  companion object {
    fun documentIdFor(treeUriString: String, relativePath: String): String =
      sha256("$treeUriString#$relativePath")

    private fun sha256(input: String): String {
      val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
      return digest.joinToString("") { "%02x".format(it) }
    }
  }
}
