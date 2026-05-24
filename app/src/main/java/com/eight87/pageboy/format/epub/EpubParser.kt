package com.eight87.pageboy.format.epub

import android.content.ContentResolver
import android.content.Context
import com.eight87.pageboy.format.api.DocumentBytesSource
import com.eight87.pageboy.format.api.SafDocumentBytesSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.AbsoluteUrl
import org.readium.r2.shared.util.Try
import org.readium.r2.shared.util.asset.Asset
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.shared.util.mediatype.MediaType
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser
import java.io.IOException

/**
 * Phase M.3 — thin wrapper around Readium's parser pipeline that owns
 * the [AssetRetriever] + [PublicationOpener] graph and surfaces a
 * single suspending `open(source)` for [EpubRenderer]. One instance
 * per [com.eight87.pageboy.AppGraph]; safe to share across renderer
 * invocations (Readium's components are stateless after construction —
 * each `open()` builds its own per-asset machinery internally).
 *
 * Architecture:
 *
 * 1. The SAF URI rides through [SafDocumentBytesSource.documentUri]
 *    just like the PDF renderer (no bytes round-trip through a parcel).
 * 2. `AssetRetriever.retrieve(url, FormatHints(mediaType = EPUB))`
 *    resolves the URI to a Readium [Asset], sniffing the ZIP +
 *    container.xml internally.
 * 3. `PublicationOpener.open(asset)` builds the [Publication] —
 *    container.xml + OPF + nav doc parsed eagerly, spine items pulled
 *    on-demand by the navigator (the streaming model the seed plan
 *    relies on for 500-page books).
 *
 * Liskov / R.X.5: every error path returns a thrown [IOException] (the
 * chrome's `DefaultReaderStateProjector` projects to `ReaderState.Failed`).
 * No `NotImplementedError`. Encrypted publications (the LCP path the
 * plan deferred) currently fall through to a normal `Reading` error
 * from the streamer; the chrome shows the failure state.
 */
open class EpubParser(
  private val context: Context,
  private val contentResolver: ContentResolver,
) {

  private val httpClient by lazy { DefaultHttpClient() }

  private val assetRetriever by lazy { AssetRetriever(contentResolver, httpClient) }

  /**
   * The default parser pipeline. `pdfDocumentFactory = null` opts pageboy
   * out of Readium's PDF parsing — pageboy handles PDF via androidx.pdf
   * (Phase F) and Readium's PDF parser would otherwise pull a separate
   * dependency that's not on the allowlist.
   *
   * Lazy so test subclasses that override [parse] never construct the
   * heavy upstream graph.
   */
  private val publicationOpener: PublicationOpener by lazy {
    // Positional call to dodge Readium parameter-name drift between
    // minor versions; matches `DefaultPublicationParser(Context,
    // HttpClient, AssetRetriever, PdfDocumentFactory?, List<extras>)`
    // per the upstream signature. Pageboy declines the PDF parser
    // (null) and ships no extra parsers.
    PublicationOpener(
      DefaultPublicationParser(
        context,
        httpClient,
        assetRetriever,
        null,
        emptyList(),
      ),
    )
  }

  /**
   * Parse the SAF-backed EPUB bytes into a Readium [Publication]. Runs
   * on [Dispatchers.IO]. Throws [IOException] on parse / format / asset
   * failures; the chrome's projector converts those to
   * `ReaderState.Failed`.
   */
  open suspend fun parse(source: DocumentBytesSource): Publication = withContext(Dispatchers.IO) {
    val saf = source as? SafDocumentBytesSource
      ?: throw IOException("EpubParser requires a SAF-backed DocumentBytesSource")

    // Readium's AbsoluteUrl.invoke(String) understands every URI scheme
    // SAF emits (`content://...`, `file://...`); returns null only for
    // truly malformed input.
    val url: AbsoluteUrl = AbsoluteUrl.invoke(saf.documentUri.toString())
      ?: throw IOException("Unable to build Readium AbsoluteUrl for ${saf.documentUri}")

    val asset: Asset = when (val res = assetRetriever.retrieve(url, MediaType.EPUB)) {
      is Try.Success -> res.value
      is Try.Failure -> throw IOException("Readium AssetRetriever rejected the EPUB asset: ${res.value}")
    }

    val publication: Publication = when (val res = publicationOpener.open(asset, allowUserInteraction = false)) {
      is Try.Success -> res.value
      is Try.Failure -> {
        // Release the asset's underlying resources on failure — the
        // opener does NOT close the asset on its own failure path.
        runCatching { asset.close() }
        throw IOException("Readium PublicationOpener failed to open EPUB: ${res.value}")
      }
    }

    publication
  }
}
