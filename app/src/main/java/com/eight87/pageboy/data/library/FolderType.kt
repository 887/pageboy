package com.eight87.pageboy.data.library

/**
 * Phase B.3 — how the user has organised documents within a single
 * picked SAF tree URI.
 *
 * Adapted from whisperboy's `FolderType` (`SingleFile` / `SingleFolder`
 * / `Root` / `Author`). Pageboy substitutes `Category` for whisperboy's
 * `Author` because documents are organised by topic / shelf / project
 * rather than by author the way audiobooks are. The walker logic is
 * structurally identical — `Category` treats the first-level subfolder
 * name as the document's `collection`.
 *
 * Sealed type + exhaustive `when` per the family's open/closed pattern
 * — adding a fifth folder mode is a new variant + a new branch in
 * `SafLibraryScanner.scanRoot`.
 */
sealed interface FolderType {

  /** One file = one document. The picked URI is itself the file (rare, but supported). */
  data object SingleFile : FolderType

  /** Flat: every supported file directly in this folder is a document. No recursion. */
  data object SingleFolder : FolderType

  /** Recursive walk. Top-level subfolder name is the collection; nested folders nest into it. */
  data object Root : FolderType

  /** Top-level subfolders are categories; documents inside get tagged with the category. */
  data object Category : FolderType

  companion object {
    fun id(type: FolderType): String = when (type) {
      SingleFile -> "single_file"
      SingleFolder -> "single_folder"
      Root -> "root"
      Category -> "category"
    }

    fun fromId(id: String): FolderType? = when (id) {
      "single_file" -> SingleFile
      "single_folder" -> SingleFolder
      "root" -> Root
      "category" -> Category
      else -> null
    }

    val allOrdered: List<FolderType> = listOf(SingleFolder, Root, Category, SingleFile)
  }
}
