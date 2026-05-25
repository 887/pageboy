package com.eight87.pageboy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.eight87.pageboy.data.settings.ThemeMode
import com.eight87.pageboy.theme.PageboyTheme
import com.eight87.pageboy.ui.PageboyApp

/**
 * Single-activity entry point. The Compose hierarchy under
 * [PageboyTheme] owns every visible surface; no Android Views, no
 * fragments.
 *
 * Close-out — reads ThemeSettings from the AppGraph so the user's
 * theme mode / dynamic color / seed color preferences drive the
 * MaterialExpressiveTheme's color scheme. Every surface tints
 * automatically via the M3 surfaceContainer ladder.
 */
class PageboyActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    val appGraph = (applicationContext as? PageboyApplication)?.appGraph

    setContent {
      val themeMode by (appGraph?.themeSettings?.themeMode?.flow
        ?: kotlinx.coroutines.flow.flowOf(ThemeMode.System))
        .collectAsState(initial = ThemeMode.System)
      val dynamicColor by (appGraph?.themeSettings?.dynamicColor?.flow
        ?: kotlinx.coroutines.flow.flowOf(true))
        .collectAsState(initial = true)
      val seedColor by (appGraph?.themeSettings?.seedColor?.flow
        ?: kotlinx.coroutines.flow.flowOf(0L))
        .collectAsState(initial = 0L)

      PageboyTheme(
        themeMode = themeMode,
        dynamicColor = dynamicColor,
        seedColorRgb = seedColor,
      ) {
        Surface(
          modifier = Modifier.fillMaxSize(),
          color = MaterialTheme.colorScheme.background,
        ) {
          PageboyApp()
        }
      }
    }
  }

  companion object {
    /** Phase N — extra key for OpenWithActivity to pass the initial document id. */
    const val EXTRA_INITIAL_DOCUMENT_ID = "com.eight87.pageboy.INITIAL_DOCUMENT_ID"
  }
}
