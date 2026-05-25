@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.eight87.pageboy.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.expressiveLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.eight87.pageboy.data.settings.ThemeMode

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
 * [MaterialExpressiveTheme] (the M3E wrapper introduced by Material 3 1.5.0).
 *
 * Close-out enhancement: reads [themeMode], [dynamicColor], and
 * [seedColorRgb] from the ThemeSettings facet so every surface gets
 * the user's chosen tint when a seed is picked.
 *
 * When [seedColorRgb] is non-zero and [dynamicColor] is false, the
 * full M3 color scheme is derived from the seed using HSL-shift
 * generation (same pattern tonearmboy uses). All `surfaceContainer*`
 * rungs are auto-derived from the seed so cards / settings rows / the
 * nav rail all tint consistently.
 */
@Composable
fun PageboyTheme(
  themeMode: ThemeMode = ThemeMode.System,
  dynamicColor: Boolean = true,
  seedColorRgb: Long = 0L,
  content: @Composable () -> Unit,
) {
  val darkTheme = when (themeMode) {
    ThemeMode.Light -> false
    ThemeMode.Dark -> true
    ThemeMode.System -> isSystemInDarkTheme()
  }

  val colorScheme = resolveColorScheme(darkTheme, dynamicColor, seedColorRgb)

  MaterialExpressiveTheme(colorScheme = colorScheme, typography = Typography, content = content)
}

/**
 * Resolve the [ColorScheme] from the user's three settings knobs.
 *
 * Priority:
 *  1. Dynamic color on + API 31+ -> wallpaper-derived scheme.
 *  2. Seed color non-zero -> HSL-derived custom scheme.
 *  3. Fallback -> brand palette (expressiveLightColorScheme / darkColorScheme).
 */
@Composable
internal fun resolveColorScheme(
  darkTheme: Boolean,
  dynamicColor: Boolean,
  seedColorRgb: Long,
): ColorScheme {
  val dynamicAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

  return when {
    dynamicColor && dynamicAvailable -> {
      val context = LocalContext.current
      if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    }
    seedColorRgb != 0L -> deriveCustomScheme(seedColorRgb, darkTheme)
    else -> if (darkTheme) DarkColorScheme else LightColorScheme
  }
}

/**
 * Derive a Material 3 [ColorScheme] from a 24-bit RGB seed.
 *
 * Strategy: build primary / secondary / tertiary tonal anchors by
 * shifting the seed's hue (secondary = +30 deg, tertiary = +60 deg)
 * and lightness, then plug them into the canonical
 * `lightColorScheme` / `darkColorScheme` factories. This sidesteps
 * Material 3's `dynamicColorScheme(seed, isDark)` so the build works
 * regardless of the active Material 3 version.
 *
 * Pure helper for unit-testability — no Compose runtime required.
 */
internal fun deriveCustomScheme(seedRgb: Long, darkTheme: Boolean): ColorScheme {
  val primary = colorFromRgbLong(seedRgb)
  val (h, s, _) = rgbToHslTriple(primary)
  val secondary = hslColor(((h + 30f) % 360f), (s * 0.7f).coerceIn(0f, 1f), if (darkTheme) 0.7f else 0.45f)
  val tertiary = hslColor(((h + 60f) % 360f), (s * 0.6f).coerceIn(0f, 1f), if (darkTheme) 0.7f else 0.5f)
  val primaryDark = hslColor(h, s, if (darkTheme) 0.7f else 0.4f)
  val onPrimary = if (luminance(primaryDark) > 0.5f) Color.Black else Color.White

  // Derive surface container ladder from a desaturated, lightened/darkened seed.
  val surfaceBase = hslColor(h, (s * 0.12f).coerceIn(0f, 1f), if (darkTheme) 0.10f else 0.96f)
  val surfContainerLowest = hslColor(h, (s * 0.08f).coerceIn(0f, 1f), if (darkTheme) 0.04f else 1.0f)
  val surfContainerLow = hslColor(h, (s * 0.10f).coerceIn(0f, 1f), if (darkTheme) 0.08f else 0.97f)
  val surfContainer = hslColor(h, (s * 0.12f).coerceIn(0f, 1f), if (darkTheme) 0.12f else 0.94f)
  val surfContainerHigh = hslColor(h, (s * 0.14f).coerceIn(0f, 1f), if (darkTheme) 0.16f else 0.91f)
  val surfContainerHighest = hslColor(h, (s * 0.16f).coerceIn(0f, 1f), if (darkTheme) 0.20f else 0.88f)

  return if (darkTheme) {
    darkColorScheme(
      primary = primaryDark,
      secondary = secondary,
      tertiary = tertiary,
      onPrimary = onPrimary,
      surface = surfaceBase,
      surfaceContainerLowest = surfContainerLowest,
      surfaceContainerLow = surfContainerLow,
      surfaceContainer = surfContainer,
      surfaceContainerHigh = surfContainerHigh,
      surfaceContainerHighest = surfContainerHighest,
    )
  } else {
    lightColorScheme(
      primary = primaryDark,
      secondary = secondary,
      tertiary = tertiary,
      onPrimary = onPrimary,
      surface = surfaceBase,
      surfaceContainerLowest = surfContainerLowest,
      surfaceContainerLow = surfContainerLow,
      surfaceContainer = surfContainer,
      surfaceContainerHigh = surfContainerHigh,
      surfaceContainerHighest = surfContainerHighest,
    )
  }
}

private fun colorFromRgbLong(rgb: Long): Color {
  val r = ((rgb shr 16) and 0xFFL).toInt()
  val g = ((rgb shr 8) and 0xFFL).toInt()
  val b = (rgb and 0xFFL).toInt()
  return Color(red = r / 255f, green = g / 255f, blue = b / 255f, alpha = 1f)
}

/** Returns (hue 0..360, saturation 0..1, lightness 0..1). */
internal fun rgbToHslTriple(c: Color): Triple<Float, Float, Float> {
  val r = c.red; val g = c.green; val b = c.blue
  val max = maxOf(r, g, b); val min = minOf(r, g, b)
  val l = (max + min) / 2f
  val delta = max - min
  if (delta == 0f) return Triple(0f, 0f, l)
  val s = if (l > 0.5f) delta / (2f - max - min) else delta / (max + min)
  val h = when (max) {
    r -> 60f * (((g - b) / delta) % 6f)
    g -> 60f * (((b - r) / delta) + 2f)
    else -> 60f * (((r - g) / delta) + 4f)
  }.let { if (it < 0f) it + 360f else it }
  return Triple(h, s, l)
}

internal fun hslColor(hue: Float, saturation: Float, lightness: Float): Color {
  val h = ((hue % 360f) + 360f) % 360f
  val s = saturation.coerceIn(0f, 1f)
  val l = lightness.coerceIn(0f, 1f)
  val c = (1f - kotlin.math.abs(2f * l - 1f)) * s
  val hp = h / 60f
  val x = c * (1f - kotlin.math.abs((hp % 2f) - 1f))
  val (r1, g1, b1) = when {
    hp < 1f -> Triple(c, x, 0f)
    hp < 2f -> Triple(x, c, 0f)
    hp < 3f -> Triple(0f, c, x)
    hp < 4f -> Triple(0f, x, c)
    hp < 5f -> Triple(x, 0f, c)
    else -> Triple(c, 0f, x)
  }
  val m = l - c / 2f
  return Color(red = (r1 + m).coerceIn(0f, 1f), green = (g1 + m).coerceIn(0f, 1f), blue = (b1 + m).coerceIn(0f, 1f), alpha = 1f)
}

internal fun luminance(c: Color): Float =
  0.2126f * c.red + 0.7152f * c.green + 0.0722f * c.blue
