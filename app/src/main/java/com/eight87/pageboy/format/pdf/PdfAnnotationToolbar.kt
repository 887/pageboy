package com.eight87.pageboy.format.pdf

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BorderColor
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.FormatStrikethrough
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.StickyNote2
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import com.eight87.pageboy.domain.render.annotation.AnnotationCommands
import com.eight87.pageboy.domain.render.annotation.AnnotationTool

/**
 * Phase G.4 — annotation toolbar. Renders one chip per [AnnotationTool];
 * tapping a chip toggles that tool active (or off if already active).
 *
 * Reads tool state via [AnnotationCommands.state] so the chip
 * selection survives recomposition. The chips use M3 Expressive
 * [FilterChip] for the canonical "one of many" affordance.
 *
 * Toolbar appears below the chrome's top app bar (placed inside the
 * `PdfBody` Column). Hidden entirely when [AnnotationCommands] isn't
 * wired (the caller's responsibility).
 */
@Composable
internal fun PdfAnnotationToolbar(
  commands: AnnotationCommands,
  modifier: Modifier = Modifier,
) {
  val state by commands.state.collectAsState()

  Row(
    modifier = modifier
      .fillMaxWidth()
      .background(MaterialTheme.colorScheme.surfaceContainerHigh)
      .padding(horizontal = 8.dp, vertical = 4.dp)
      .semantics { testTag = "pdf_annotation_toolbar" },
    horizontalArrangement = Arrangement.spacedBy(6.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    AnnotationTool.values().forEach { tool ->
      val active = state.tool == tool
      FilterChip(
        selected = active,
        onClick = { commands.setTool(if (active) null else tool) },
        label = { Text(tool.label()) },
        leadingIcon = {
          Icon(
            imageVector = tool.icon(),
            contentDescription = null,
          )
        },
        modifier = Modifier.semantics { testTag = "pdf_tool_${tool.name}" },
      )
    }
  }
}

private fun AnnotationTool.label(): String = when (this) {
  AnnotationTool.Highlight -> "Highlight"
  AnnotationTool.Underline -> "Underline"
  AnnotationTool.Strikethrough -> "Strike"
  AnnotationTool.FreehandInk -> "Ink"
  AnnotationTool.StickyNote -> "Note"
  AnnotationTool.Stamp -> "Stamp"
}

private fun AnnotationTool.icon() = when (this) {
  AnnotationTool.Highlight -> Icons.Filled.FormatColorFill
  AnnotationTool.Underline -> Icons.Filled.FormatUnderlined
  AnnotationTool.Strikethrough -> Icons.Filled.FormatStrikethrough
  AnnotationTool.FreehandInk -> Icons.Filled.Draw
  AnnotationTool.StickyNote -> Icons.Filled.StickyNote2
  AnnotationTool.Stamp -> Icons.Filled.BorderColor
}
