package com.eight87.pageboy.data.openwith

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.eight87.pageboy.data.library.DocumentDao
import com.eight87.pageboy.data.library.DocumentSourceKind

/**
 * Phase N.10 — daily WorkManager job that removes stale ephemeral
 * ad-hoc rows. A row is "stale" when:
 *  - its source is `AdHocOpen` with `ephemeral = true`, AND
 *  - its `lastOpenedAt` is more than [retentionDays] in the past (or
 *    null and the row's `addedAt` is older than that — the user opened
 *    it once, never returned, never tapped Keep).
 *
 * The retention window defaults to 7 days (see [OpenWithSettings]); the
 * worker reads the current value from the [retentionDaysProvider] thunk
 * so the user's most recent slider position takes effect on the next
 * scheduled run without re-scheduling the worker.
 *
 * NOT a Hilt worker — pageboy is no-DI; the
 * [com.eight87.pageboy.PageboyApplication]'s on-create wiring schedules
 * the worker with a custom `WorkerFactory` that hands in the
 * [DocumentDao] from the AppGraph.
 */
class OpenWithEphemeralCleanupWorker(
  context: Context,
  params: WorkerParameters,
  private val documentDao: DocumentDao,
  private val retentionDaysProvider: suspend () -> Int,
  private val now: () -> Long = System::currentTimeMillis,
) : CoroutineWorker(context, params) {

  override suspend fun doWork(): Result {
    return try {
      val retention = retentionDaysProvider().coerceAtLeast(OpenWithSettings.MIN_RETENTION_DAYS)
      val cutoff = now() - (retention.toLong() * MILLIS_PER_DAY)
      val candidates = documentDao.allAdHocDocuments()
      val toDelete = candidates.filter { entity ->
        val source = entity.toSourceKind() as? DocumentSourceKind.AdHocOpen ?: return@filter false
        if (!source.ephemeral) return@filter false
        // Skip rows the user pinned — pinning is an explicit retention signal.
        if (entity.pinned) return@filter false
        val touched = entity.lastOpenedAt ?: entity.addedAt
        touched < cutoff
      }
      toDelete.forEach { documentDao.deleteById(it.documentId) }
      Result.success()
    } catch (t: Throwable) {
      // WorkManager will retry on the next periodic tick anyway; surface
      // the failure so the runtime backs off without permanently
      // disabling the worker.
      Result.retry()
    }
  }

  companion object {
    /** Stable worker tag used by the application to schedule + cancel. */
    const val UNIQUE_NAME: String = "open_with_ephemeral_cleanup"

    private const val MILLIS_PER_DAY: Long = 24L * 60L * 60L * 1000L
  }
}
