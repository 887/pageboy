package com.eight87.pageboy.format.markdown

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

/**
 * Phase D — pure typography lookup. Maps Markdown block kinds to
 * Material 3 Expressive [Typography] entries; no logic, no state. Lives
 * apart from [MarkdownBlocks] so the per-block renderers read styling
 * via one indirection that's swappable in tests if we ever need to
 * verify "an H2 uses headlineSmall, not headlineMedium" without firing
 * up Compose.
 */
internal object MarkdownStyle {

  fun headingStyle(level: Int, typography: Typography, colors: ColorScheme): TextStyle {
    val base = when (level.coerceIn(1, 6)) {
      1 -> typography.headlineLarge
      2 -> typography.headlineMedium
      3 -> typography.headlineSmall
      4 -> typography.titleLarge
      5 -> typography.titleMedium
      else -> typography.titleSmall
    }
    return base.copy(color = colors.onSurface, fontWeight = FontWeight.SemiBold)
  }

  fun paragraphStyle(typography: Typography, colors: ColorScheme): TextStyle =
    typography.bodyLarge.copy(color = colors.onSurface)

  fun blockquoteStyle(typography: Typography, colors: ColorScheme): TextStyle =
    typography.bodyLarge.copy(color = colors.onSurfaceVariant)

  fun listItemStyle(typography: Typography, colors: ColorScheme): TextStyle =
    typography.bodyLarge.copy(color = colors.onSurface)

  fun codeStyle(typography: Typography, colors: ColorScheme): TextStyle =
    typography.bodyMedium.copy(
      fontFamily = FontFamily.Monospace,
      color = colors.onSurface,
    )

  fun captionStyle(typography: Typography, colors: ColorScheme): TextStyle =
    typography.bodySmall.copy(color = colors.onSurfaceVariant)
}
