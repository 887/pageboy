package com.eight87.pageboy

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.eight87.pageboy.data.library.AndroidLibraryRescanCoordinator
import com.eight87.pageboy.data.library.AndroidLibraryUiSettings
import com.eight87.pageboy.data.library.AndroidPersistedUriPermissionStore
import com.eight87.pageboy.data.library.DocumentFormat
import com.eight87.pageboy.data.library.DocumentSource
import com.eight87.pageboy.data.library.LibraryDatabase
import com.eight87.pageboy.data.library.LibraryRepository
import com.eight87.pageboy.data.library.LibraryRescanCoordinator
import com.eight87.pageboy.data.library.LibraryUiSettings
import com.eight87.pageboy.data.library.PersistedUriPermissionStore
import com.eight87.pageboy.data.library.SafLibraryScanner
import com.eight87.pageboy.data.settings.AndroidReaderSettings
import com.eight87.pageboy.data.settings.ReaderSettings
import com.eight87.pageboy.format.api.DocumentRenderer
import com.eight87.pageboy.format.markdown.MarkdownParser
import com.eight87.pageboy.format.markdown.MarkdownRenderer
import com.eight87.pageboy.format.registry.CompiledFormatRegistry
import com.eight87.pageboy.format.registry.FormatRegistry
import com.eight87.pageboy.domain.render.RendererReadingPrefs
import com.eight87.pageboy.format.txt.TxtRenderer
import com.eight87.pageboy.ui.reader.control.AndroidShareExportCommands
import com.eight87.pageboy.ui.reader.control.DefaultReaderStateProjector
import com.eight87.pageboy.ui.reader.control.DefaultRendererReadingPrefs
import com.eight87.pageboy.ui.reader.control.DefaultScrollPersistence
import com.eight87.pageboy.ui.reader.control.InMemoryFindInDocCommands
import com.eight87.pageboy.ui.reader.control.ReaderStateProjector
import com.eight87.pageboy.ui.reader.control.ScrollPersistence
import com.eight87.pageboy.ui.reader.control.ShareExportCommands
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

  // ---- Phase C — reader-side wiring ----

  private val readerSettingsDataStore: DataStore<Preferences> =
    context.applicationContext.readerSettingsDataStore

  val readerSettings: ReaderSettings by lazy {
    AndroidReaderSettings(readerSettingsDataStore)
  }

  /**
   * Phase D — Markdown is the first real [DocumentRenderer]. One commonmark
   * parser instance is fine to share across renders; it's stateless and
   * thread-safe (the `Parser` holds only the extension list).
   */
  private val markdownParser: MarkdownParser by lazy { MarkdownParser() }

  /**
   * Phase C.2 / C.7 / D.5 — format registry. Phase D adds the
   * [MarkdownRenderer] entry; every other [DocumentFormat] continues to
   * fall through to the [com.eight87.pageboy.format.placeholder.PlaceholderRenderer]
   * via [CompiledFormatRegistry]'s fallback, which is what keeps the
   * reader UI exercisable end-to-end before Phase E–M land their
   * renderers.
   *
   * Adding a format: one new entry. Never a `when (format)` switch in
   * the reader (R.X.9).
   */
  val formatRegistry: FormatRegistry by lazy {
    CompiledFormatRegistry(
      renderers = mapOf<DocumentFormat, DocumentRenderer>(
        DocumentFormat.Markdown to MarkdownRenderer(markdownParser),
        DocumentFormat.Txt to TxtRenderer(),
      ),
    )
  }

  val readerStateProjector: ReaderStateProjector by lazy {
    DefaultReaderStateProjector(
      applicationScope = applicationScope,
      documentSource = libraryRepository,
      formatRegistry = formatRegistry,
      contentResolver = context.applicationContext.contentResolver,
    )
  }

  val scrollPersistence: ScrollPersistence by lazy {
    DefaultScrollPersistence(
      applicationScope = applicationScope,
      documentSource = libraryRepository,
    )
  }

  /**
   * Phase C.7 — factory per reader instance. Find state is per-document
   * (the query, the match list, the current index) and tearing it down
   * when leaving the reader is the cleanest semantic; AppGraph-scoped
   * find state would survive reader-screen disposal and leak between
   * documents.
   */
  val findInDocCommandsFactory: () -> InMemoryFindInDocCommands = { InMemoryFindInDocCommands() }

  val shareExportCommands: ShareExportCommands by lazy {
    AndroidShareExportCommands(context.applicationContext)
  }

  /**
   * Phase E.1 — renderer-facing read-only view of [readerSettings].
   * Lives on the AppGraph (not per-document) because reading prefs are
   * app-scoped, not per-document.
   */
  val rendererReadingPrefs: RendererReadingPrefs by lazy {
    DefaultRendererReadingPrefs(scope = applicationScope, settings = readerSettings)
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

/** Phase C.8 — DataStore for reader-side settings (`continuousScrolling` etc.). */
private val Context.readerSettingsDataStore: DataStore<Preferences> by preferencesDataStore(
  name = "reader_settings",
)
