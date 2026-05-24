package com.eight87.pageboy.openwith

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.eight87.pageboy.PageboyActivity
import com.eight87.pageboy.PageboyApplication
import com.eight87.pageboy.R
import com.eight87.pageboy.data.openwith.OpenWithResolver
import com.eight87.pageboy.data.openwith.OpenWithResult
import kotlinx.coroutines.launch

/**
 * Phase N.2 — translucent "Open with" entry point.
 *
 * The system PackageManager routes any `ACTION_VIEW` intent matching
 * one of the manifest filters here (no launcher entry — `exported=true`
 * + intent-filter-only). The activity resolves the intent via
 * [OpenWithResolver] off the AppGraph, dispatches on [OpenWithResult],
 * and either launches [PageboyActivity] with `EXTRA_INITIAL_DOCUMENT_ID`
 * + finishes, or toasts an error + finishes.
 *
 * No Compose surface here — the activity's job is purely to resolve +
 * dispatch. The translucent splash theme keeps the surface invisible
 * so the user sees the reader launch directly out of the chooser
 * without a flash of chrome.
 *
 * Size: ~70 LOC of executable Kotlin (R.X.4 — comfortably under the
 * 250-LOC red flag).
 */
class OpenWithActivity : ComponentActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val intent = intent ?: run {
      finishWith("No intent.")
      return
    }
    val appGraph = (applicationContext as? PageboyApplication)?.appGraph ?: run {
      finishWith(getString(R.string.open_with_failure))
      return
    }
    val resolver: OpenWithResolver = appGraph.openWithResolver
    lifecycleScope.launch {
      val result = resolver.resolve(intent)
      // Phase N.7 — every Ready intent feeds the Recents tab so the
      // ad-hoc open shows up on the user's next cold start (locked
      // decision #5). recordOpen is idempotent.
      if (result is OpenWithResult.Ready) {
        runCatching { appGraph.libraryRepository.recordOpen(result.documentId) }
      }
      dispatch(result)
    }
  }

  private fun dispatch(result: OpenWithResult) {
    when (result) {
      is OpenWithResult.Ready -> launchReaderAndFinish(result.documentId)
      is OpenWithResult.UnknownFormat -> finishWith(
        getString(R.string.open_with_unknown_format, result.displayName ?: ""),
      )
      is OpenWithResult.PermissionRefused -> finishWith(
        getString(R.string.open_with_permission_refused),
      )
      is OpenWithResult.Failure -> finishWith(
        getString(R.string.open_with_failure),
      )
    }
  }

  private fun launchReaderAndFinish(documentId: String) {
    val launch = Intent(this, PageboyActivity::class.java).apply {
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
      putExtra(PageboyActivity.EXTRA_INITIAL_DOCUMENT_ID, documentId)
    }
    startActivity(launch)
    finish()
  }

  private fun finishWith(message: String) {
    if (message.isNotBlank()) {
      Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
    finish()
  }
}
