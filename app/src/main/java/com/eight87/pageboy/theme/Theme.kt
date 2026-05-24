@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.eight87.pageboy.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.expressiveLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

// m3-expressive.md gotcha #1 — `material3:1.4.0` keeps the expressive
// factories `internal`; we override to `1.5.0-alpha18` in
// `gradle/libs.versions.toml` so `expressiveLightColorScheme()` /
// `MaterialExpressiveTheme(...)` are callable. `expressiveDarkColorScheme()`
// does NOT exist in alpha18 — dark mode stays on `darkColorScheme(...)` and
// inherits the surface-tier ladder.
internal val DarkColorScheme = darkColorScheme(
  primary = Slate80,
  secondary = SlateGrey80,
  tertiary = Teal80,
)

internal val LightColorScheme = expressiveLightColorScheme()

/**
 * Pageboy's theme entry point. Wraps content in
 * [MaterialExpressiveTheme] (the M3E wrapper introduced by Material 3 1.5.0,
 * pulling in the new motion / typography / shape defaults — rounded
 * extra-large group shapes, faster spring-based motion) instead of the bare
 * [androidx.compose.material3.MaterialTheme].
 *
 * Dynamic color (Material You wallpaper-driven palette) is the default on
 * API 31+ and falls through to the brand seeds on older devices. A user-
 * facing toggle for dynamic color lands during the Settings phase; for
 * Phase A the API-conditional default is what ships.
 *
 * Per m3-expressive.md gotcha #2, cards / settings rows / catalog cards
 * default to `surfaceContainerHigh` on dark mode (consumed by
 * `SettingsCard`); the page surface stays on `surface`.
 */
@Composable
fun PageboyTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Dynamic color is available on Android 12+.
  dynamicColor: Boolean = true,
  content: @Composable () -> Unit,
) {
  val colorScheme = when {
    dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
      val context = LocalContext.current
      if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    }
    darkTheme -> DarkColorScheme
    else -> LightColorScheme
  }

  MaterialExpressiveTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
