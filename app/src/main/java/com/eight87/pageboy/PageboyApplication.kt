package com.eight87.pageboy

import android.app.Application
import android.content.Context
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.eight87.pageboy.data.openwith.OpenWithEphemeralCleanupWorker
import com.eight87.pageboy.data.openwith.OpenWithSettings
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Application subclass. Owns the hand-rolled [AppGraph] (Room database,
 * DataStore handles, SAF library scanner, library repository) — the
 * family's composition-root pattern. Activities reach in via
 * `(applicationContext as PageboyApplication).appGraph`.
 *
 * Phase N — also bootstraps WorkManager with a custom
 * [WorkerFactory] that hands the AppGraph's `DocumentDao` +
 * `OpenWithSettings` into the [OpenWithEphemeralCleanupWorker]
 * constructor. Custom factory because pageboy ships zero DI; the
 * default WorkerFactory only resolves Workers with the
 * `(Context, WorkerParameters)` constructor.
 *
 * Tests use `TestApplication` instead to keep Robolectric runs zero-cost
 * (no Room DB open per test).
 */
class PageboyApplication : Application(), Configuration.Provider {

  override fun onCreate() {
    // Phase I.2 / J.1 — Apache POI's StAX bootstrap MUST land before any
    // POI class loads. Android does not ship `javax.xml.stream.*`
    // factories; POI 5.x respects these three `org.apache.poi.*`
    // overrides explicitly so the stripped Android runtime never gets
    // asked for the missing factories. Aalto is the StAX impl we ship
    // (com.fasterxml:aalto-xml). See docs/plans/format-docx.md §Android.
    PoiStaxBootstrap.installAaltoOnce()
    super.onCreate()
    // Phase N.10 — schedule the daily ephemeral cleanup.
    // ExistingPeriodicWorkPolicy.KEEP — idempotent across app restarts;
    // the worker reads the user's current retention setting on each
    // run so the slider in Settings takes effect on the next tick.
    //
    // Wrapped in runCatching so Robolectric / instrumented tests that
    // skip WorkManager initialization don't fail in onCreate; the
    // production app uses the auto-initializer (Configuration.Provider
    // contract) so getInstance() always resolves.
    runCatching {
      val request = PeriodicWorkRequestBuilder<OpenWithEphemeralCleanupWorker>(
        1, TimeUnit.DAYS,
      ).build()
      WorkManager.getInstance(this).enqueueUniquePeriodicWork(
        OpenWithEphemeralCleanupWorker.UNIQUE_NAME,
        ExistingPeriodicWorkPolicy.KEEP,
        request,
      )
    }
  }

  /**
   * Lazily-built composition root. Constructed on first access so unit
   * tests that don't reach into the graph never spin up Room. The
   * lifetime matches the process — fine for our usage (no
   * application-multi-process configuration in the manifest).
   */
  val appGraph: AppGraph by lazy { AppGraph(this) }

  /**
   * Phase N — WorkManager bootstraps via [Configuration.Provider] so we
   * hand it our [AppGraphWorkerFactory] without needing a manifest
   * `androidx.startup` entry. The factory resolves the
   * [OpenWithEphemeralCleanupWorker] (which takes constructor args
   * beyond `Context` + `WorkerParameters`) by reaching into the
   * AppGraph for the [com.eight87.pageboy.data.library.DocumentDao]
   * and the [OpenWithSettings].
   */
  override val workManagerConfiguration: Configuration
    get() = Configuration.Builder()
      .setWorkerFactory(AppGraphWorkerFactory(this))
      .build()
}

/**
 * Phase N.10 — minimal [WorkerFactory] that knows how to construct
 * pageboy's workers. Adding a new worker is one branch here + the
 * Worker class itself; the default factory cannot handle workers with
 * non-trivial constructors.
 */
internal class AppGraphWorkerFactory(
  private val application: PageboyApplication,
) : WorkerFactory() {

  override fun createWorker(
    appContext: Context,
    workerClassName: String,
    workerParameters: WorkerParameters,
  ) = when (workerClassName) {
    OpenWithEphemeralCleanupWorker::class.java.name -> {
      val graph = application.appGraph
      OpenWithEphemeralCleanupWorker(
        context = appContext,
        params = workerParameters,
        documentDao = graph.documentDao,
        retentionDaysProvider = { graph.openWithSettings.ephemeralRetentionDays.flow.first() },
      )
    }
    else -> null // Default factory takes over for the unknown case.
  }
}
