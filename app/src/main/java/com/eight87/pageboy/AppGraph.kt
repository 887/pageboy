package com.eight87.pageboy

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.eight87.pageboy.data.library.AndroidLibraryRescanCoordinator
import com.eight87.pageboy.data.library.AndroidLibraryUiSettings
import com.eight87.pageboy.data.library.AndroidPersistedUriPermissionStore
import com.eight87.pageboy.data.library.DocumentSource
import com.eight87.pageboy.data.library.LibraryDatabase
import com.eight87.pageboy.data.library.LibraryRepository
import com.eight87.pageboy.data.library.LibraryRescanCoordinator
import com.eight87.pageboy.data.library.LibraryUiSettings
import com.eight87.pageboy.data.library.PersistedUriPermissionStore
import com.eight87.pageboy.data.library.SafLibraryScanner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first

/**
 * Phase B — hand-rolled composition root. Owns the Room database, two
 * DataStore instances (one for library roots, one for UI prefs), the
 * scanner, the rescan coordinator, and the repository. Activities /
 * composables read narrow interfaces off this graph; the only file that
 * sees concrete types is this one + [PageboyActivity].
 *
 * Created lazily off [PageboyApplication] on first request; the
 * application keeps a single instance.
 */
class AppGraph(private val context: Context) {

  /** App-scoped coroutine context for the rescan coordinator's worker loop. */
  val applicationScope: CoroutineScope = CoroutineScope(SupervisorJob())

  val database: LibraryDatabase by lazy {
    Room.databaseBuilder(
      context.applicationContext,
      LibraryDatabase::class.java,
      DB_NAME,
    ).build()
  }

  private val rootsDataStore: DataStore<Preferences> =
    context.applicationContext.libraryRootsDataStore

  private val uiDataStore: DataStore<Preferences> =
    context.applicationContext.libraryUiDataStore

  val persistedUriPermissionStore: PersistedUriPermissionStore by lazy {
    AndroidPersistedUriPermissionStore(context.applicationContext, rootsDataStore)
  }

  val libraryUiSettings: LibraryUiSettings by lazy {
    AndroidLibraryUiSettings(uiDataStore)
  }

  val libraryRepository: LibraryRepository by lazy {
    LibraryRepository(database)
  }

  // Narrow handles the UI sees.
  val documentSource: DocumentSource get() = libraryRepository

  private val scanner: SafLibraryScanner by lazy {
    SafLibraryScanner(
      context = context.applicationContext,
      includeHiddenProvider = {
        // Best-effort snapshot — DataStore reads on a worker thread; the
        // scanner is also IO-bound and tolerates the brief read. Pulling
        // the value out via `runBlocking { first() }` here would deadlock
        // the scan dispatcher; instead we accept a stale value (default
        // false) on the very first scan, and pick up the user's toggle
        // on the next scan. The toggle is rare enough this is fine.
        false
      },
    )
  }

  val libraryRescanCoordinator: LibraryRescanCoordinator by lazy {
    AndroidLibraryRescanCoordinator(
      scanner = scanner,
      rootStore = persistedUriPermissionStore,
      writer = libraryRepository,
      applicationScope = applicationScope,
    ).also { it.start() }
  }

  companion object {
    private const val DB_NAME = "pageboy_library.db"
  }
}

/** DataStore for the library-root URI + folder-type metadata. */
private val Context.libraryRootsDataStore: DataStore<Preferences> by preferencesDataStore(
  name = "library_roots",
)

/** DataStore for library-UI preferences (sort, tab, filters). */
private val Context.libraryUiDataStore: DataStore<Preferences> by preferencesDataStore(
  name = "library_ui",
)
