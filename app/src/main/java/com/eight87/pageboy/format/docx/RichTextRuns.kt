package com.eight87.pageboy.format.docx

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import com.eight87.pageboy.format.docx.internal.RichTextRun

/**
 * Phase I — inline-run folding for DOCX. Each block's runs are folded
 * down into one [AnnotatedString] the Compose `Text` consumes; this
 * keeps the per-block render cheap (one `Text` per paragraph instead of
 * a `Row` of styled fragments).
 *
 * Hyperlinks fold into spans tagged `URL` so the Compose layer can
 * pick them up via `pushStringAnnotation`-style click handlers later.
 * In v1 the chrome doesn't surface link taps from inside DOCX — Phase
 * N opens that path.
 */
internal object RichTextRuns {

  fun foldInlines(
    runs: List<RichTextRun>,
    colors: ColorScheme,
    typography: Typography,
  ): AnnotatedString = buildAnnotatedString {
    for (run in runs) {
      when (run) {
        is RichTextRun.Text -> withStyle(textSpan(run, colors)) { append(run.value) }
        is RichTextRun.Hyperlink -> {
          val span = SpanStyle(
            color = colors.primary,
            textDecoration = TextDecoration.Underline,
          )
          pushStringAnnotation(tag = LINK_TAG, annotation = run.url)
          withStyle(span) { append(run.text) }
          pop()
        }
        is RichTextRun.FieldCode -> append(run.cachedText)
        is RichTextRun.Tab -> append(TAB_PADDING)
        is RichTextRun.SoftBreak -> append('\n')
      }
    }
  }

  /**
   * Wrap a Material typography style with the DOCX run's inline
   * toggles. Mostly the body style; headings override at the block
   * level via [HeadingStyle].
   */
  private fun textSpan(text: RichTextRun.Text, colors: ColorScheme): SpanStyle {
    val weight = if (text.bold) FontWeight.SemiBold else FontWeight.Normal
    val italic = if (text.italic) FontStyle.Italic else FontStyle.Normal
    val deco = when {
      text.underline && text.strikethrough -> TextDecoration.combine(
        listOf(TextDecoration.Underline, TextDecoration.LineThrough),
      )
      text.underline -> TextDecoration.Underline
      text.strikethrough -> TextDecoration.LineThrough
      else -> null
    }
    val family = if (text.monospace) FontFamily.Monospace else null
    return SpanStyle(
      color = colors.onSurface,
      fontWeight = weight,
      fontStyle = italic,
      textDecoration = deco,
      fontFamily = family,
    )
  }

  /**
   * Convenience overload pulling the Material colours / typography
   * out of [MaterialTheme] for use inside Composables.
   */
  @Composable
  fun foldInlinesFromTheme(runs: List<RichTextRun>): AnnotatedString =
    foldInlines(runs, MaterialTheme.colorScheme, MaterialTheme.typography)

  const val LINK_TAG: String = "URL"
  private const val TAB_PADDING: String = "    "
}
