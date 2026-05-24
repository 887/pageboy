package com.eight87.pageboy.data.library

/**
 * Phase B.6 — narrow data interface for the SAF tree walker.
 *
 * [SafLibraryScanner] is the only implementation in pageboy v1; tests
 * substitute fakes. The coordinator calls [scan] on `Dispatchers.IO`
 * with the user's current set of [LibraryRoot]s.
 */
interface LibraryScanner {

  /**
   * Walk [roots] and return a [ScanSnapshot]. Implementations should
   * invoke [onProgress] as documents are discovered so the in-library
   * progress banner can tick continuously during the (potentially slow)
   * SAF traversal — without this, the count stays frozen at 0 for the
   * whole walk while the user waits.
   *
   * Errors raised by [onProgress] must not abort the scan; the callback
   * is for UX surfaces, not control flow. Default no-op keeps test paths
   * trivial.
   */
  suspend fun scan(
    roots: List<LibraryRoot>,
    onProgress: suspend (documentsFound: Int, currentFolder: String?) -> Unit = { _, _ -> },
  ): ScanSnapshot
}
