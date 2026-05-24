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
 */
class PageboyActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      PageboyTheme {
        Surface(
          modifier = Modifier.fillMaxSize(),
          color = MaterialTheme.colorScheme.background,
        ) {
          PageboyApp()
        }
      }
    }
  }
}
