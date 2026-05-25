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
import com.eight87.pageboy.data.settings.BaseThemeChoice
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
 * The color scheme is determined by [baseTheme]:
 *
 *  - [BaseThemeChoice.DefaultAndroid] -- Material You / dynamic colour
 *    on API 31+, falls back to the brand palette on older devices.
 *  - [BaseThemeChoice.DefaultColors] -- the static brand palette
 *    regardless of API.
 *  - [BaseThemeChoice.PureBlack] -- same primary colours as
 *    DefaultAndroid but with `surface` / `background` collapsed to
 *    pure black for AMOLED-friendly displays.
 *  - [BaseThemeChoice.Custom] -- HSL-derived palette from [seedColorRgb].
 *
 * Legacy [dynamicColor] param is kept for backward compat but defers
 * to [baseTheme] when the caller provides it.
 */
@Composable
fun PageboyTheme(
  themeMode: ThemeMode = ThemeMode.System,
  baseTheme: BaseThemeChoice = BaseThemeChoice.DefaultAndroid,
  dynamicColor: Boolean = true,
  seedColorRgb: Long = 0L,
  content: @Composable () -> Unit,
) {
  val darkTheme = when (themeMode) {
    ThemeMode.Light -> false
    ThemeMode.Dark -> true
    ThemeMode.System -> isSystemInDarkTheme()
  }

  val colorScheme = resolveColorScheme(darkTheme, baseTheme, seedColorRgb)

  MaterialExpressiveTheme(colorScheme = colorScheme, typography = Typography, content = content)
}

/**
 * Resolve the foundation [ColorScheme] for the active [BaseThemeChoice].
 *
 * Mirrors tonearmboy's `resolveBaseScheme` -- four-way dispatch:
 *  - DefaultAndroid: dynamic on API 31+, brand palette below.
 *  - DefaultColors: brand palette always (no dynamic).
 *  - PureBlack: same as DefaultAndroid but surface + background = Black.
 *  - Custom: HSL-derived from [seedColorRgb].
 */
@Composable
internal fun resolveColorScheme(
  darkTheme: Boolean,
  baseTheme: BaseThemeChoice,
  seedColorRgb: Long,
): ColorScheme {
  val dynamicAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

  return when (baseTheme) {
    BaseThemeChoice.DefaultAndroid -> {
      if (dynamicAvailable) {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      } else {
        if (darkTheme) DarkColorScheme else LightColorScheme
      }
    }
    BaseThemeChoice.DefaultColors -> {
      if (darkTheme) DarkColorScheme else LightColorScheme
    }
    BaseThemeChoice.PureBlack -> {
      val foundation = if (dynamicAvailable) {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      } else {
        if (darkTheme) DarkColorScheme else LightColorScheme
      }
      foundation.copy(background = Color.Black, surface = Color.Black)
    }
    BaseThemeChoice.Custom -> {
      if (seedColorRgb != 0L) deriveCustomScheme(seedColorRgb, darkTheme)
      else if (darkTheme) DarkColorScheme else LightColorScheme
    }
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

internal fun blendSurface(base: Color, tint: Color?, fraction: Float = 0.4f): Color {
  if (tint == null) return base
  val f = fraction.coerceIn(0f, 1f)
  return Color(
    red = base.red * (1f - f) + tint.red * f,
    green = base.green * (1f - f) + tint.green * f,
    blue = base.blue * (1f - f) + tint.blue * f,
    alpha = base.alpha,
  )
}
