package com.eight87.pageboy.data.library

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.eight87.pageboy.data.annotation.AnnotationDao
import com.eight87.pageboy.data.annotation.AnnotationEntity

/**
 * Phase B.1 — Room database holding the document library cache.
 *
 * Schema export is on (`app/schemas/com.eight87.pageboy.data.library.LibraryDatabase/<version>.json`)
 * — committed to the repo so future migrations can diff. No
 * `fallbackToDestructiveMigration`; any v1 → v2+ schema change must ship
 * a migration.
 *
 * Built once via [com.eight87.pageboy.AppGraph] using
 * `Room.databaseBuilder(...)`. Robolectric tests build an in-memory
 * variant via `Room.inMemoryDatabaseBuilder(...)`.
 *
 * Phase F.2 — bumped to v2. Adds `scroll_position_json` TEXT column to
 * `documents` for the sealed `ScrollPosition` JSON payload.
 *
 * Phase G.1 — bumped to v3. Adds the `annotations` table for the PDF
 * annotation overlay model.
 *
 * Phase N.5 — bumped to v4. Adds `source_json` TEXT column to
 * `documents` for the sealed `DocumentSourceKind` discriminator.
 */
@Database(
  entities = [
    DocumentEntity::class,
    LibraryFingerprintEntity::class,
    AnnotationEntity::class,
  ],
  version = 4,
  exportSchema = true,
)
abstract class LibraryDatabase : RoomDatabase() {

  abstract fun documentDao(): DocumentDao

  abstract fun libraryFingerprintDao(): LibraryFingerprintDao

  abstract fun annotationDao(): AnnotationDao

  companion object {

    /**
     * Phase F.2 — v1 → v2 migration. Adds one nullable TEXT column to
     * `documents`.
     */
    val MIGRATION_1_2: Migration = object : Migration(1, 2) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE documents ADD COLUMN scroll_position_json TEXT")
      }
    }

    /**
     * Phase G.1 — v2 → v3 migration. Adds the `annotations` table.
     * Additive only; `documents` and `library_fingerprints` untouched.
     */
    val MIGRATION_2_3: Migration = object : Migration(2, 3) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
          """
          CREATE TABLE IF NOT EXISTS `annotations` (
            `id` TEXT NOT NULL,
            `documentId` TEXT NOT NULL,
            `page_index` INTEGER NOT NULL,
            `kind` TEXT NOT NULL,
            `payload_json` TEXT NOT NULL,
            `color_argb` INTEGER NOT NULL,
            `page_width_pt` REAL NOT NULL,
            `page_height_pt` REAL NOT NULL,
            `created_at` INTEGER NOT NULL,
            `modified_at` INTEGER NOT NULL,
            `is_deleted` INTEGER NOT NULL,
            PRIMARY KEY(`id`)
          )
          """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_annotations_documentId` ON `annotations` (`documentId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_annotations_documentId_page_index` ON `annotations` (`documentId`, `page_index`)")
      }
    }

    /**
     * Phase N.5 — v3 → v4 migration. Adds `source_json` TEXT column to
     * `documents` for the [DocumentSourceKind] sealed discriminator.
     * Existing rows leave the column null; [DocumentEntity.toSourceKind]
     * treats null as `LibraryRoot(treeUriString)` for backward
     * compatibility.
     */
    val MIGRATION_3_4: Migration = object : Migration(3, 4) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE documents ADD COLUMN source_json TEXT")
      }
    }
  }
}
