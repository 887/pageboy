package com.eight87.pageboy.data.library

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface LibraryFingerprintDao {

  @Query("SELECT * FROM library_fingerprints WHERE treeUriString = :treeUriString LIMIT 1")
  suspend fun findFor(treeUriString: String): LibraryFingerprintEntity?

  @Upsert
  suspend fun upsert(entity: LibraryFingerprintEntity)

  @Query("DELETE FROM library_fingerprints WHERE treeUriString = :treeUriString")
  suspend fun deleteFor(treeUriString: String)
}
