package com.eight87.pageboy

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.eight87.pageboy.data.annotation.AnnotationRepository
import com.eight87.pageboy.data.annotation.AnnotationSource
import com.eight87.pageboy.data.annotation.AnnotationStore
import com.eight87.pageboy.data.library.AndroidLibraryRescanCoordinator
import com.eight87.pageboy.data.library.AndroidLibraryUiSettings
import com.eight87.pageboy.data.library.AndroidPersistedUriPermissionStore
import com.eight87.pageboy.data.library.AndroidSetupSettings
import com.eight87.pageboy.data.library.DocumentFormat
import com.eight87.pageboy.data.library.DocumentSource
import com.eight87.pageboy.data.library.DocumentSourceMode
import com.eight87.pageboy.data.library.FileSystemScanner
import com.eight87.pageboy.data.library.FolderType
import com.eight87.pageboy.data.library.LibraryDatabase
import com.eight87.pageboy.data.library.LibraryRepository
import com.eight87.pageboy.data.library.LibraryRescanCoordinator
import com.eight87.pageboy.data.library.LibraryRoot
import com.eight87.pageboy.data.library.LibraryScanner
import com.eight87.pageboy.data.library.LibraryUiSettings
import com.eight87.pageboy.data.library.ScanSnapshot
import com.eight87.pageboy.data.library.PersistedUriPermissionStore
import com.eight87.pageboy.data.library.SafLibraryScanner
import com.eight87.pageboy.data.library.SetupSettings
import com.eight87.pageboy.data.openwith.AdHocDocumentStore
import com.eight87.pageboy.data.openwith.AndroidOpenWithResolver
import com.eight87.pageboy.data.openwith.AndroidOpenWithSettings
import com.eight87.pageboy.data.openwith.OpenWithResolver
import com.eight87.pageboy.data.openwith.OpenWithSettings
import com.eight87.pageboy.data.openwith.RoomAdHocDocumentStore
import com.eight87.pageboy.data.settings.AndroidReaderSettings
import com.eight87.pageboy.data.settings.AndroidThemeSettings
import com.eight87.pageboy.data.settings.ReaderSettings
import com.eight87.pageboy.data.settings.ThemeSettings
import com.eight87.pageboy.data.signing.AndroidSigningSettings
import com.eight87.pageboy.data.signing.SigningSettings
import com.eight87.pageboy.domain.render.annotation.AnnotationCommands
import com.eight87.pageboy.format.pdf.signing.KeystoreKeyProvider
import com.eight87.pageboy.format.pdf.signing.PadesSigner
import com.eight87.pageboy.format.pdf.signing.PdfStampBurnIn
import com.eight87.pageboy.format.pdf.signing.Pkcs12KeyProvider
import com.eight87.pageboy.ui.reader.control.AdHocReaderActions
import com.eight87.pageboy.ui.reader.control.DefaultAdHocReaderActions
import com.eight87.pageboy.ui.reader.control.InMemorySigningCommands
import com.eight87.pageboy.ui.reader.control.annotation.AndroidAnnotationCommands
import com.eight87.pageboy.format.api.DocumentRenderer
import com.eight87.pageboy.format.docx.DocxRenderer
import com.eight87.pageboy.format.epub.EpubParser
import com.eight87.pageboy.format.epub.EpubRenderer
import com.eight87.pageboy.format.markdown.MarkdownParser
import com.eight87.pageboy.format.markdown.MarkdownRenderer
import com.eight87.pageboy.format.mobi.MobiRenderer
import com.eight87.pageboy.format.pdf.PdfRenderer
import com.eight87.pageboy.format.odt.OdtRenderer
import com.eight87.pageboy.format.ods.OdsRenderer
import com.eight87.pageboy.format.registry.CompiledFormatRegistry
import com.eight87.pageboy.format.registry.FormatRegistry
import com.eight87.pageboy.domain.render.RendererReadingPrefs
import com.eight87.pageboy.format.txt.TxtRenderer
import com.eight87.pageboy.format.xlsx.XlsxRenderer
import com.eight87.pageboy.ui.reader.control.AndroidShareExportCommands
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
      .addMigrations(
        LibraryDatabase.MIGRATION_1_2,
        LibraryDatabase.MIGRATION_2_3,
        LibraryDatabase.MIGRATION_3_4,
      )
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

  // ---- Setup wizard settings ----

  private val setupDataStore: DataStore<Preferences> =
    context.applicationContext.setupSettingsDataStore

  val setupSettings: SetupSettings by lazy {
    AndroidSetupSettings(setupDataStore)
  }

  val libraryRepository: LibraryRepository by lazy {
    LibraryRepository(database)
  }

  // Narrow handles the UI sees.
  val documentSource: DocumentSource get() = libraryRepository

  private val safScanner: SafLibraryScanner by lazy {
    SafLibraryScanner(
      context = context.applicationContext,
      includeHiddenProvider = { false },
    )
  }

  private val fileSystemScanner: FileSystemScanner by lazy {
    FileSystemScanner(
      includeHiddenProvider = { false },
    )
  }

  /**
   * Mode-aware scanner: reads the document source mode and dispatches to
   * the appropriate scanner implementation.
   */
  private val scanner: LibraryScanner by lazy {
    ModeAwareScanner(
      setupSettings = setupSettings,
      safScanner = safScanner,
      fileSystemScanner = fileSystemScanner,
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

  // ---- Close-out — theme settings ----

  private val themeSettingsDataStore: DataStore<Preferences> =
    context.applicationContext.themeSettingsDataStore

  val themeSettings: ThemeSettings by lazy {
    AndroidThemeSettings(themeSettingsDataStore)
  }

  // ---- Phase H — signing-side wiring ----

  private val signingSettingsDataStore: DataStore<Preferences> =
    context.applicationContext.signingSettingsDataStore

  val signingSettings: SigningSettings by lazy {
    AndroidSigningSettings(signingSettingsDataStore)
  }

  val keystoreKeyProvider: KeystoreKeyProvider by lazy { KeystoreKeyProvider() }
  val pkcs12KeyProvider: Pkcs12KeyProvider by lazy { Pkcs12KeyProvider() }
  val padesSigner: PadesSigner by lazy { PadesSigner() }
  val pdfStampBurnIn: PdfStampBurnIn by lazy { PdfStampBurnIn() }
  val signingCommandsFactory: () -> InMemorySigningCommands = { InMemorySigningCommands() }

  // ---- Phase G — annotation wiring ----

  private val annotationRepository: AnnotationRepository by lazy {
    AnnotationRepository(database.annotationDao())
  }

  val annotationSource: AnnotationSource get() = annotationRepository
  val annotationStore: AnnotationStore get() = annotationRepository
  val annotationCommandsFactory: () -> AnnotationCommands = {
    AndroidAnnotationCommands(
      store = annotationStore,
    )
  }

  // ---- Phase N — open-with wiring ----

  private val openWithSettingsDataStore: DataStore<Preferences> =
    context.applicationContext.openWithSettingsDataStore

  val openWithSettings: OpenWithSettings by lazy {
    AndroidOpenWithSettings(openWithSettingsDataStore)
  }

  val adHocDocumentStore: AdHocDocumentStore by lazy {
    RoomAdHocDocumentStore(
      documentDao = database.documentDao(),
      contentResolver = context.applicationContext.contentResolver,
    )
  }

  val openWithResolver: OpenWithResolver by lazy {
    AndroidOpenWithResolver(
      contentResolver = context.applicationContext.contentResolver,
      adHocDocumentStore = adHocDocumentStore,
      autoClassifyUnknownMime = {
        openWithSettings.autoClassifyUnknownMime.flow.first()
      },
    )
  }

  val adHocReaderActions: AdHocReaderActions by lazy {
    DefaultAdHocReaderActions(
      documentSource = libraryRepository,
      adHocDocumentStore = adHocDocumentStore,
    )
  }

  // ---- Format registry ----

  private val markdownParser: MarkdownParser by lazy { MarkdownParser() }

  private val epubParser: EpubParser by lazy {
    EpubParser(
      context = context.applicationContext,
      contentResolver = context.applicationContext.contentResolver,
    )
  }

  val formatRegistry: FormatRegistry by lazy {
    CompiledFormatRegistry(
      renderers = mapOf<DocumentFormat, DocumentRenderer>(
        DocumentFormat.Markdown to MarkdownRenderer(markdownParser),
        DocumentFormat.Txt to TxtRenderer(),
        DocumentFormat.Pdf to PdfRenderer(context.applicationContext.contentResolver),
        DocumentFormat.Docx to DocxRenderer(),
        DocumentFormat.Xlsx to XlsxRenderer(),
        DocumentFormat.Odt to OdtRenderer(),
        DocumentFormat.Ods to OdsRenderer(),
        DocumentFormat.Epub to EpubRenderer(epubParser),
        DocumentFormat.Mobi to MobiRenderer(),
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

  val findInDocCommandsFactory: () -> InMemoryFindInDocCommands = { InMemoryFindInDocCommands() }

  val shareExportCommands: ShareExportCommands by lazy {
    AndroidShareExportCommands(context.applicationContext)
  }

  val rendererReadingPrefs: RendererReadingPrefs by lazy {
    DefaultRendererReadingPrefs(scope = applicationScope, settings = readerSettings)
  }

  val imageLoader: ImageLoader by lazy {
    val loader = ImageLoader.Builder(context.applicationContext as PlatformContext)
      .crossfade(true)
      .memoryCache {
        MemoryCache.Builder()
          .maxSizePercent(context.applicationContext, percent = 0.10)
          .build()
      }
      .build()
    SingletonImageLoader.setSafe { loader }
    loader
  }

  companion object {
    private const val DB_NAME = "pageboy_library.db"
  }
}

private val Context.libraryRootsDataStore: DataStore<Preferences> by preferencesDataStore(
  name = "library_roots",
)

private val Context.libraryUiDataStore: DataStore<Preferences> by preferencesDataStore(
  name = "library_ui",
)

private val Context.readerSettingsDataStore: DataStore<Preferences> by preferencesDataStore(
  name = "reader_settings",
)

private val Context.themeSettingsDataStore: DataStore<Preferences> by preferencesDataStore(
  name = "theme_settings",
)

private val Context.signingSettingsDataStore: DataStore<Preferences> by preferencesDataStore(
  name = "signing_settings",
)

private val Context.openWithSettingsDataStore: DataStore<Preferences> by preferencesDataStore(
  name = "open_with_settings",
)

private val Context.setupSettingsDataStore: DataStore<Preferences> by preferencesDataStore(
  name = "setup_settings",
)

/**
 * Scanner that delegates to [SafLibraryScanner] or [FileSystemScanner]
 * based on the persisted [DocumentSourceMode]. In AllFiles mode, the
 * scanner ignores the caller-supplied roots and walks the standard
 * filesystem paths; in FolderPicker mode, it delegates to the SAF scanner
 * with the caller-supplied roots.
 */
private class ModeAwareScanner(
  private val setupSettings: SetupSettings,
  private val safScanner: SafLibraryScanner,
  private val fileSystemScanner: FileSystemScanner,
) : LibraryScanner {

  override suspend fun scan(
    roots: List<LibraryRoot>,
    onProgress: suspend (documentsFound: Int, currentFolder: String?) -> Unit,
  ): ScanSnapshot {
    val mode = setupSettings.documentSourceMode.flow.first()

    return when (mode) {
      DocumentSourceMode.FolderPicker -> safScanner.scan(roots, onProgress)
      DocumentSourceMode.AllFiles -> {
        // In AllFiles mode, build synthetic roots from default paths.
        val allFilesRoots = FileSystemScanner.DEFAULT_SCAN_PATHS.map { dir ->
          LibraryRoot(
            treeUri = android.net.Uri.fromFile(dir),
            folderType = FolderType.Root,
            displayName = dir.name,
          )
        }
        fileSystemScanner.scan(allFilesRoots, onProgress)
      }
    }
  }
}
