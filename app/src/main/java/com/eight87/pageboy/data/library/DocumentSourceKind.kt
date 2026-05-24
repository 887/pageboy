package com.eight87.pageboy.data.library

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Phase N.5 — sealed discriminator for where a [DocumentEntity] came from.
 *
 * Persisted as one JSON-encoded TEXT column (`source_json`) — same shape
 * Phase F.2 used for `scroll_position_json`. Storing the sealed-variant
 * tag inside the JSON (kotlinx.serialization's `@SerialName` /
 * polymorphic-by-class-discriminator) lets future variants (e.g. a
 * `SharedAttachment` sub-kind for `ACTION_SEND` ingest) land as one new
 * variant without another schema migration — only the JSON shape
 * changes.
 *
 * **Why a separate sealed type (not just an enum + the URI on the entity).**
 * `AdHocOpen` carries variant-specific fields ([uri], [ephemeral]) that
 * `LibraryRoot` does not, and `LibraryRoot` carries [rootTreeUriString]
 * that the ad-hoc variant does not. Encoding both shapes as a flat set
 * of optional columns on the entity would re-introduce the
 * "primitive-obsession" anti-pattern R.X.2 forbids. The sealed payload
 * lives in one column; the discriminator is the class.
 */
@Serializable
sealed class DocumentSourceKind {

  /**
   * Phase B's library-scanner provenance. The [rootTreeUriString] matches
   * the entity's `treeUriString` column; carrying it here too keeps the
   * sealed type self-describing (downstream consumers don't need the
   * whole [DocumentEntity] to know which root owns the doc).
   */
  @Serializable
  data class LibraryRoot(val rootTreeUriString: String) : DocumentSourceKind()

  /**
   * Phase N — opened via `ACTION_VIEW` from another app. The URI is the
   * raw `content://` the sender handed us. When [ephemeral] is true the
   * URI permission is the system's transient
   * `FLAG_GRANT_READ_URI_PERMISSION` and we have no `takePersistableUriPermission`
   * for it; "Keep this document" tries to upgrade and surfaces a
   * library-root copy fallback on `SecurityException`.
   *
   * Successful "Keep" + persistable grant flips [ephemeral] to false in
   * place. "Keep" + save-to-library-root replaces the row's source with
   * [LibraryRoot] entirely (see `AdHocDocumentStore.saveToLibraryRoot`).
   */
  @Serializable
  data class AdHocOpen(
    val uri: String,
    val ephemeral: Boolean,
  ) : DocumentSourceKind()
}

/**
 * Phase N.5 — JSON codec for [DocumentSourceKind]. Used by the Room
 * `@TypeConverter` below and by ad-hoc call sites that need to read /
 * write the column out of band (workers, migrations).
 *
 * `ignoreUnknownKeys = true` so an older build that doesn't know a new
 * variant degrades to null instead of crashing on read.
 */
object DocumentSourceCodec {

  private val json = Json {
    ignoreUnknownKeys = true
    classDiscriminator = "type"
  }

  fun encode(value: DocumentSourceKind?): String? =
    if (value == null) null else json.encodeToString(DocumentSourceKind.serializer(), value)

  fun decode(text: String?): DocumentSourceKind? {
    if (text.isNullOrBlank()) return null
    return try {
      json.decodeFromString(DocumentSourceKind.serializer(), text)
    } catch (_: SerializationException) {
      null
    } catch (_: IllegalArgumentException) {
      null
    }
  }
}
