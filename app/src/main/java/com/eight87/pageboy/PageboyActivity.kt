package com.eight87.pageboy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.eight87.pageboy.theme.PageboyTheme
import com.eight87.pageboy.ui.PageboyApp

/**
 * Single-activity entry point. The Compose hierarchy under
 * [PageboyTheme] owns every visible surface; no Android Views, no
 * fragments.
 *
 * Phase N.6 — when launched from [com.eight87.pageboy.openwith.OpenWithActivity]
 * the intent carries [EXTRA_INITIAL_DOCUMENT_ID]; the Compose host
 * seeds the back stack with `ReaderRoute(documentId)` so the user
 * lands directly in the reader. Back-from-reader returns to whoever
 * launched the original `ACTION_VIEW` intent because OpenWithActivity
 * finishes itself before starting this activity (standard Android
 * intent-chain behaviour).
 */
class PageboyActivity : ComponentActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    val initialDocumentId = intent?.getStringExtra(EXTRA_INITIAL_DOCUMENT_ID)
    setContent {
      PageboyTheme {
        Surface(
          modifier = Modifier.fillMaxSize(),
          color = MaterialTheme.colorScheme.background,
        ) {
          PageboyApp(initialDocumentId = initialDocumentId)
        }
      }
    }
  }

  companion object {
    /**
     * Phase N.6 — extra carrying the documentId an "Open with" intent
     * resolved to. When present, [com.eight87.pageboy.ui.PageboyApp]
     * seeds its initial back stack with `ReaderRoute(documentId)`.
     */
    const val EXTRA_INITIAL_DOCUMENT_ID: String = "com.eight87.pageboy.extra.INITIAL_DOCUMENT_ID"
  }
}
