package com.eight87.pageboy.data.library

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

/**
 * Filesystem-based scanner for `MANAGE_EXTERNAL_STORAGE` mode. Walks
 * standard document directories directly via `java.io.File` instead of
 * SAF tree URIs.
 *
 * Default scan locations (matching sibling app patterns):
 *  - `/storage/emulated/0/Documents/`
 *  - `/storage/emulated/0/Download/`
 *  - `/storage/emulated/0/Books/`
 *
 * Progress is reported every 500 items (matching the sibling app pattern).
 * Each file is classified by [DocumentClassifier] and assigned a stable
 * document ID via SHA-256 of its absolute path.
 */
class FileSystemScanner(
  private val classifier: DocumentClassifier = DocumentClassifier,
  private val includeHiddenProvider: () -> Boolean = { false },
) : LibraryScanner {

  override suspend fun scan(
    roots: List<LibraryRoot>,
    onProgress: suspend (documentsFound: Int, currentFolder: String?) -> Unit,
  ): ScanSnapshot = withContext(Dispatchers.IO) {
    val includeHidden = includeHiddenProvider()
    val accumulated = mutableListOf<ScannedDocument>()
    var count = 0

    for (root in roots) {
      val dir = uriToFile(root.treeUri) ?: continue
      if (!dir.exists() || !dir.isDirectory) continue

      val stack = ArrayDeque<WalkFrame>()
      stack.addLast(WalkFrame(dir, dir, null, root.folderType == FolderType.Root))

      while (stack.isNotEmpty()) {
        val frame = stack.removeLast()
        val children = frame.directory.listFiles() ?: continue

        for (child in children) {
          if (!includeHidden && child.name.startsWith('.')) continue

          if (child.isFile) {
            val format = classifier.classify(child.name) {
              runCatching { FileInputStream(child) }.getOrNull()
            }
            if (format == DocumentFormat.Unknown) continue

            val relativePath = child.relativeTo(frame.baseDir).path
            accumulated += ScannedDocument(
              documentId = documentIdFor(child.absolutePath),
              treeUriString = root.treeUri.toString(),
              relativePath = relativePath,
              documentUriString = Uri.fromFile(child).toString(),
              title = child.nameWithoutExtension,
              fileName = child.name,
              format = format,
              sizeBytes = child.length().takeIf { it > 0 },
              mtimeMs = child.lastModified(),
              collection = frame.collection,
            )
            count++
            if (count % PROGRESS_INTERVAL == 0) {
              runCatching { onProgress(count, frame.directory.name) }
            }
          } else if (child.isDirectory) {
            val nextCollection = when {
              frame.collectionFromTopLevel && frame.collection == null -> child.name
              else -> frame.collection
            }
            stack.addLast(
              WalkFrame(child, frame.baseDir, nextCollection, frame.collectionFromTopLevel),
            )
          }
        }
      }
    }

    // Final progress emission so the banner shows the final count.
    if (count % PROGRESS_INTERVAL != 0) {
      runCatching { onProgress(count, null) }
    }

    ScanSnapshot(accumulated)
  }

  /** Stack frame for the iterative directory walk. */
  private class WalkFrame(
    val directory: File,
    val baseDir: File,
    val collection: String?,
    val collectionFromTopLevel: Boolean,
  )

  companion object {
    private const val PROGRESS_INTERVAL = 500

    /** Default filesystem paths to scan in AllFiles mode. */
    val DEFAULT_SCAN_PATHS: List<File> = listOf(
      File("/storage/emulated/0/Documents"),
      File("/storage/emulated/0/Download"),
      File("/storage/emulated/0/Books"),
    )

    fun documentIdFor(absolutePath: String): String = sha256(absolutePath)

    private fun sha256(input: String): String {
      val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
      return digest.joinToString("") { "%02x".format(it) }
    }

    /**
     * Convert a file:// URI back to a [File]. Returns null for non-file
     * URIs (SAF content:// URIs should go through [SafLibraryScanner]).
     */
    fun uriToFile(uri: Uri): File? {
      if (uri.scheme == "file") return uri.path?.let { File(it) }
      // Also handle bare path URIs (no scheme) for synthetic roots.
      val path = uri.path ?: return null
      return File(path)
    }
  }
}
