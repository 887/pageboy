package com.eight87.pageboy.ui.reader.control

import android.content.ContentResolver
import android.net.Uri
import com.eight87.pageboy.data.library.DocumentFormat
import com.eight87.pageboy.data.library.DocumentSource
import com.eight87.pageboy.format.api.DocumentBytesSource
import com.eight87.pageboy.format.api.SafDocumentBytesSource
import com.eight87.pageboy.format.registry.FormatRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Phase C.4 / R.C.1 — opens documents on demand and projects the result
 * as a [ReaderState]. Narrow surface (R.X.1): one `open()` + one
 * `close()` + one `StateFlow<ReaderState>`. The reader chrome reads only
 * this; per-format renderers read only their slice.
 *
 * Split off the (still-hypothetical) god-controller per the R.C pattern
 * so the chrome can subscribe to the projection without also pulling in
 * scroll persistence / find-in-doc / share commands.
 */
interface ReaderStateProjector {

  val state: StateFlow<ReaderState>

  /**
   * Open the document identified by [documentId]. Cancels any prior
   * open in flight + closes any prior handle before starting.
   */
  fun open(documentId: String)

  /** Tear down any open handle + cancel any open in flight. */
  fun close()
}

/**
 * Phase C.4 — concrete projector. Looks the document up via the
 * [DocumentSource], builds a [DocumentBytesSource] from the SAF URI,
 * resolves the [com.eight87.pageboy.format.api.DocumentRenderer] via
 * the registry, and projects the lifecycle.
 *
 * `applicationScope` lives off the AppGraph (so a configuration change
 * doesn't cancel an in-flight open); per-open jobs are tracked
 * internally and cancelled on [close] / on a fresh [open].
 *
 * The renderer's [com.eight87.pageboy.format.api.DocumentHandle] is the
 * source of truth for `handle.title`. The chrome's top bar reads
 * `state.handle.title`; renderers that want to honour an entity-supplied
 * title set it inside their own `open()` (Phase C.3's
 * [com.eight87.pageboy.format.placeholder.PlaceholderRenderer] does
 * exactly that via `source.displayName()`). Wrapping the handle here
 * would break the renderer's ability to cast back to its concrete
 * subtype in `Body()`.
 */
class DefaultReaderStateProjector(
  private val applicationScope: CoroutineScope,
  private val documentSource: DocumentSource,
  private val formatRegistry: FormatRegistry,
  private val contentResolver: ContentResolver,
) : ReaderStateProjector {

  private val _state = MutableStateFlow<ReaderState>(ReaderState.Idle)
  override val state: StateFlow<ReaderState> = _state.asStateFlow()

  private var openJob: Job? = null

  override fun open(documentId: String) {
    // Cancel any prior attempt + release any prior handle.
    closeInternal()
    _state.value = ReaderState.Opening
    openJob = applicationScope.launch {
      runCatching {
        val entity = documentSource.findById(documentId)
          ?: return@launch failWith("Document not found: $documentId")
        val format = DocumentFormat.fromId(entity.format)
        val renderer = formatRegistry.rendererFor(format)
        val uri = Uri.parse(entity.documentUriString)
        val source = SafDocumentBytesSource(
          contentResolver = contentResolver,
          documentUri = uri,
        )
        val handle = renderer.open(source)
        _state.value = ReaderState.Open(handle = handle)
      }.onFailure { t ->
        failWith(t.message ?: t::class.simpleName ?: "Unknown error")
      }
    }
  }

  override fun close() {
    closeInternal()
    _state.value = ReaderState.Idle
  }

  private fun closeInternal() {
    openJob?.cancel()
    openJob = null
    (_state.value as? ReaderState.Open)?.handle?.let { handle ->
      runCatching { handle.close() }
    }
  }

  private fun failWith(reason: String) {
    _state.value = ReaderState.Failed(reason)
  }
}
