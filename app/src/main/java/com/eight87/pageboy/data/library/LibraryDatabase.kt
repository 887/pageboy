package com.eight87.pageboy.data.library

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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
 * `documents` for the sealed `ScrollPosition` JSON payload. v1 rows
 * migrate cleanly: new column defaults null, legacy
 * `last_read_position_ms` + `read_fraction` columns stay untouched, the
 * chrome's `ScrollPersistence` decode path falls back to the legacy
 * bit-packed encoding when the JSON column is null.
 */
@Database(
  entities = [
    DocumentEntity::class,
    LibraryFingerprintEntity::class,
  ],
  version = 3,
  exportSchema = true,
)
abstract class LibraryDatabase : RoomDatabase() {

  abstract fun documentDao(): DocumentDao

  abstract fun libraryFingerprintDao(): LibraryFingerprintDao

  companion object {

    /**
     * Phase F.2 — v1 → v2 migration. Adds one nullable TEXT column to
     * `documents`. SQLite ALTER TABLE ADD COLUMN with no DEFAULT is
     * non-destructive and instant; existing rows get NULL for the new
     * column, which the chrome treats as "fall back to the legacy
     * lastReadPositionMs encoding" so no per-document state is lost.
     */
    val MIGRATION_1_2: Migration = object : Migration(1, 2) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE documents ADD COLUMN scroll_position_json TEXT")
      }
    }

    /**
     * Phase N.5 — v2 → v3 migration. Adds the JSON-encoded
     * [DocumentSourceKind] column (`source_json`). Existing rows leave
     * the column NULL; [DocumentEntity.toSourceKind] treats null as
     * `LibraryRoot(treeUriString)` so the pre-Phase-N library rows
     * continue to surface in the All / Recents tabs without a one-shot
     * backfill.
     *
     * Ad-hoc documents created from Phase N forward write the column
     * explicitly (an `AdHocOpen(uri, ephemeral)` JSON blob).
     */
    val MIGRATION_2_3: Migration = object : Migration(2, 3) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE documents ADD COLUMN source_json TEXT")
      }
    }
  }
}
