package com.eight87.pageboy.data.library

import androidx.room.Database
import androidx.room.RoomDatabase

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
 */
@Database(
  entities = [
    DocumentEntity::class,
    LibraryFingerprintEntity::class,
  ],
  version = 1,
  exportSchema = true,
)
abstract class LibraryDatabase : RoomDatabase() {

  abstract fun documentDao(): DocumentDao

  abstract fun libraryFingerprintDao(): LibraryFingerprintDao
}
