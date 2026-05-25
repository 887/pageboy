package com.eight87.pageboy.ui.reader

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.eight87.pageboy.R

/**
 * Phase C.5 — reader top bar. Five affordances:
 *
 *   - Back (returns to the library)
 *   - Title (from `DocumentHandle.title`)
 *   - Find toggle (opens / closes the find panel)
 *   - Share (calls into [com.eight87.pageboy.ui.reader.control.ShareExportCommands])
 *   - Overflow (currently empty placeholder; Phase G+ adds annotation /
 *     bookmark / etc. entries)
 *
 * Takes only the narrow fields it renders (R.X.7) — title + four
 * callbacks. No god-state, no full controller. The overflow menu's
 * presence is independent of having entries — the icon stays visible
 * but the dropdown shows the "no actions yet" string so the affordance
 * doesn't appear and disappear across phases.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReaderTopBar(
  title: String,
  findActive: Boolean,
  onBack: () -> Unit,
  onToggleFind: () -> Unit,
  onShare: () -> Unit,
  // Phase M.7 — capability-gated overflow entries. Format renderers
  // that expose a non-empty table of contents (currently only EPUB)
  // set `tocAvailable = true`; the chrome surfaces a "Table of
  // contents…" entry that calls back into [onOpenToc]. Default false
  // so the existing renderers (Markdown / TXT / DOCX / etc.) stay
  // visually identical.
  tocAvailable: Boolean = false,
  onOpenToc: () -> Unit = {},
  showSignAction: Boolean = false,
  onSign: () -> Unit = {},
  showExportAnnotations: Boolean = false,
  onExportAnnotations: () -> Unit = {},
) {
  var overflowOpen by remember { mutableStateOf(false) }
  TopAppBar(
    modifier = Modifier.semantics { testTag = "reader_top_bar" },
    title = {
      Text(
        text = title,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.semantics { testTag = "reader_title" },
      )
    },
    navigationIcon = {
      IconButton(
        onClick = onBack,
        modifier = Modifier.semantics { testTag = "reader_back_button" },
      ) {
        Icon(
          imageVector = Icons.AutoMirrored.Filled.ArrowBack,
          contentDescription = stringResource(R.string.reader_back_cd),
        )
      }
    },
    actions = {
      IconButton(
        onClick = onToggleFind,
        modifier = Modifier
          .size(48.dp)
          .semantics { testTag = "reader_find_button" },
      ) {
        Icon(
          imageVector = Icons.Filled.Search,
          contentDescription = stringResource(
            if (findActive) R.string.reader_find_close_cd
            else R.string.reader_find_cd,
          ),
        )
      }
      IconButton(
        onClick = onShare,
        modifier = Modifier
          .size(48.dp)
          .semantics { testTag = "reader_share_button" },
      ) {
        Icon(
          imageVector = Icons.Filled.Share,
          contentDescription = stringResource(R.string.reader_share_cd),
        )
      }
      IconButton(
        onClick = { overflowOpen = true },
        modifier = Modifier
          .size(48.dp)
          .semantics { testTag = "reader_overflow_button" },
      ) {
        Icon(
          imageVector = Icons.Filled.MoreVert,
          contentDescription = stringResource(R.string.reader_overflow_cd),
        )
      }
      DropdownMenu(
        expanded = overflowOpen,
        onDismissRequest = { overflowOpen = false },
      ) {
        // Phase M.7 — capability-gated "Table of contents…" entry,
        // surfaced when the resolved DocumentHandle.tocAvailable is
        // true (currently only EPUB).
        if (tocAvailable) {
          DropdownMenuItem(
            text = { Text(stringResource(R.string.reader_overflow_toc_label)) },
            onClick = {
              overflowOpen = false
              onOpenToc()
            },
            modifier = Modifier.semantics { testTag = "reader_overflow_toc_item" },
          )
        }
        // Phase H — single entry point into the sign sheet; visual
        // stamp vs cryptographic signature picked inside the sheet.
        // PDF-only in v1.
        if (showSignAction) {
          DropdownMenuItem(
            text = { Text(stringResource(R.string.reader_overflow_sign_label)) },
            onClick = {
              overflowOpen = false
              onSign()
            },
            modifier = Modifier.semantics { testTag = "reader_overflow_sign" },
          )
        }
        // Phase G — export with annotations entry (PDF only).
        if (showExportAnnotations) {
          DropdownMenuItem(
            text = { Text(stringResource(R.string.reader_overflow_export_annotations_label)) },
            onClick = {
              overflowOpen = false
              onExportAnnotations()
            },
            modifier = Modifier.semantics { testTag = "reader_overflow_export_annotations" },
          )
        }
        // Phase C placeholder — kept for formats with no actions in v1.
        if (!tocAvailable && !showSignAction && !showExportAnnotations) {
          DropdownMenuItem(
            text = { Text(stringResource(R.string.reader_overflow_empty_label)) },
            onClick = { overflowOpen = false },
            enabled = false,
          )
        }
      }
    },
  )
}
