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
import com.eight87.pageboy.data.openwith.AdHocDocumentStore
import com.eight87.pageboy.data.openwith.AndroidOpenWithResolver
import com.eight87.pageboy.data.openwith.AndroidOpenWithSettings
import com.eight87.pageboy.data.openwith.OpenWithResolver
import com.eight87.pageboy.data.openwith.OpenWithSettings
import com.eight87.pageboy.data.openwith.RoomAdHocDocumentStore
import com.eight87.pageboy.data.settings.AndroidReaderSettings
import com.eight87.pageboy.data.settings.ReaderSettings
import com.eight87.pageboy.format.api.DocumentRenderer
import com.eight87.pageboy.format.docx.DocxRenderer
import com.eight87.pageboy.format.markdown.MarkdownParser
import com.eight87.pageboy.format.markdown.MarkdownRenderer
import com.eight87.pageboy.format.pdf.PdfRenderer
import com.eight87.pageboy.format.odt.OdtRenderer
import com.eight87.pageboy.format.ods.OdsRenderer
import com.eight87.pageboy.format.registry.CompiledFormatRegistry
import com.eight87.pageboy.format.registry.FormatRegistry
import com.eight87.pageboy.domain.render.RendererReadingPrefs
import com.eight87.pageboy.format.txt.TxtRenderer
import com.eight87.pageboy.format.xlsx.XlsxRenderer
import com.eight87.pageboy.ui.reader.control.AdHocReaderActions
import com.eight87.pageboy.ui.reader.control.AndroidShareExportCommands
import com.eight87.pageboy.ui.reader.control.DefaultAdHocReaderActions
import com.eight87.pageboy.ui.reader.control.DefaultReaderStateProjector
import com.eight87.pageboy.ui.reader.control.DefaultRendererReadingPrefs
import com.eight87.pageboy.ui.reader.control.DefaultScrollPersistence
import com.eight87.pageboy.ui.reader.control.InMemoryFindInDocCommands
import com.eight87.pageboy.ui.reader.control.ReaderStateProjector
import com.eight87.pageboy.ui.reader.control.ScrollPersistence
import com.eight87.pageboy.ui.reader.control.ShareExportCommands
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.request.crossfade
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
    )
      // Phase F.2 — v1 → v2 adds scroll_position_json TEXT column. The
      // migration is additive (ALTER TABLE ADD COLUMN with no default,
      // null-tolerant) so old rows preserve their per-document state.
      // Phase N.5 — v2 → v3 adds source_json TEXT column for the sealed
      // DocumentSourceKind (LibraryRoot / AdHocOpen). Existing rows
      // default to NULL which DocumentEntity.toSourceKind treats as
      // LibraryRoot(treeUriString).
      .addMigrations(LibraryDatabase.MIGRATION_1_2, LibraryDatabase.MIGRATION_2_3)
      .build()
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
        // Phase F.5 — view-only PDF rendering via androidx.pdf's
        // PdfViewerFragment. ContentResolver-only constructor; the
        // SAF URI rides through SafDocumentBytesSource. Annotation
        // editing arrives in Phase G; cryptographic signing in
        // Phase H.
        DocumentFormat.Pdf to PdfRenderer(context.applicationContext.contentResolver),
        // Phase I — DOCX via Apache POI's XWPF. Three StAX system
        // properties were installed in `PageboyApplication.onCreate()`
        // so POI's XMLInputFactory lookup lands on Aalto rather than
        // failing on Android's missing javax.xml.stream factory.
        DocumentFormat.Docx to DocxRenderer(),
        // Phase J — XLSX via Apache POI's XSSF.
        DocumentFormat.Xlsx to XlsxRenderer(),
        // Phase K + L — ODF pair. Hand-rolled XmlPullParser +
        // ZipInputStream; no third-party dep (Apache ODF Toolkit
        // rejected on APK budget, see docs/plans/format-odt.md +
        // format-ods.md). Each renderer owns its own internal block /
        // cell model — no shared `RichTextDocument` / `SpreadsheetModel`
        // cross-package (cross-format unification deferred to Phase 1.x).
        DocumentFormat.Odt to OdtRenderer(),
        DocumentFormat.Ods to OdsRenderer(),
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

  /**
   * Phase F.4 — Coil 3 [ImageLoader] used by the markdown renderer's
   * image inlines + standalone image blocks. Closes Phase D's image
   * deferral (was a placeholder card).
   *
   * v1 deliberately disables the disk cache to keep image bytes from
   * persisting across reader sessions (the user may open a document
   * they don't want cached). Memory cache stays on so scrolling a
   * markdown doc with reused images doesn't refetch on every scroll.
   * Revisit in v1.x once an explicit opt-in toggle ships.
   *
   * Singleton install — the [SingletonImageLoader.setSafe] hook makes
   * `AsyncImage(...)` resolve our configured loader without each
   * call-site passing it explicitly. Set lazily here (vs. eagerly in
   * `PageboyApplication`) so Robolectric tests that build a partial
   * AppGraph don't force the install.
   */
  val imageLoader: ImageLoader by lazy {
    val loader = ImageLoader.Builder(context.applicationContext as PlatformContext)
      .crossfade(true)
      .memoryCache {
        MemoryCache.Builder()
          .maxSizePercent(context.applicationContext, percent = 0.10)
          .build()
      }
      // No diskCache(...) call — disk cache disabled by default in v1.
      .build()
    SingletonImageLoader.setSafe { loader }
    loader
  }

  /**
   * Phase N.8 — narrow surface the reader chrome takes for the
   * "Keep this document" overflow entry. Stays AppGraph-scoped
   * because the underlying [DocumentSource] + [AdHocDocumentStore]
   * are; per-reader allocation would just create + tear down a
   * lookup-only wrapper.
   */
  val adHocReaderActions: AdHocReaderActions by lazy {
    DefaultAdHocReaderActions(
      documentSource = libraryRepository,
      adHocDocumentStore = adHocDocumentStore,
    )
  }

  // ---- Phase N — Open with / ad-hoc ingest ----

  private val openWithDataStore: DataStore<Preferences> =
    context.applicationContext.openWithDataStore

  val openWithSettings: OpenWithSettings by lazy {
    AndroidOpenWithSettings(openWithDataStore)
  }

  /**
   * Phase N — narrow store for ad-hoc rows. The resolver inserts +
   * the reader-overflow `Keep this document` action upgrades.
   * Concrete type wired here only; consumers take [AdHocDocumentStore].
   */
  val adHocDocumentStore: AdHocDocumentStore by lazy {
    RoomAdHocDocumentStore(
      documentDao = database.documentDao(),
      contentResolver = context.applicationContext.contentResolver,
    )
  }

  /**
   * Phase N — `OpenWithActivity` takes this. The `autoClassifyUnknownMime`
   * thunk reads the current setting lazily so the user's toggle takes
   * effect on the next resolve without rebuilding the resolver.
   */
  val openWithResolver: OpenWithResolver by lazy {
    AndroidOpenWithResolver(
      contentResolver = context.applicationContext.contentResolver,
      adHocDocumentStore = adHocDocumentStore,
      autoClassifyUnknownMime = { openWithSettings.autoClassifyUnknownMime.flow.first() },
    )
  }

  /**
   * Phase N.10 — read-only DocumentDao handle the
   * `OpenWithEphemeralCleanupWorker`'s WorkerFactory needs. Surfaced
   * here instead of going through the repository because the worker
   * touches the `allAdHocDocuments` / `deleteById` queries the
   * narrow `DocumentSource` interface intentionally does not expose.
   */
  val documentDao get() = database.documentDao()

  companion object {
    private const val DB_NAME = "pageboy_library.db"
  }
}

/** Phase N — DataStore for the "Open with" settings facet (retention
 *  days + save-to-library default + auto-classify toggle). */
private val Context.openWithDataStore: DataStore<Preferences> by preferencesDataStore(
  name = "open_with_settings",
)

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
