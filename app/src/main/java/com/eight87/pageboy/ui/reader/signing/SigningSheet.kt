package com.eight87.pageboy.ui.reader.signing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eight87.pageboy.R
import com.eight87.pageboy.ui.reader.control.SigningCommands
import com.eight87.pageboy.ui.reader.control.SigningState

/**
 * Phase H.4 / H.5 — bottom-sheet UI driven by [SigningCommands.state].
 *
 * One sheet, multiple sub-pages keyed off the sealed [SigningState]:
 *
 *  - [SigningState.Idle] — sheet hidden; the reader's "Sign…" overflow
 *    entry calls [SigningCommands.start] to open it.
 *  - [SigningState.DrawingStamp] — capture pad (Phase H.4). v1 ships a
 *    placeholder that calls `commit(EMPTY_PNG)` immediately so the
 *    state machine is exercised end-to-end; the real `androidx.ink`
 *    pad lands when Phase G's ink pipeline merges.
 *  - [SigningState.PlacingStamp] — instructs the user to tap the PDF.
 *  - [SigningState.KeySelecting] — picks Keystore vs PKCS#12.
 *  - [SigningState.Signing] — determinate progress.
 *  - [SigningState.Success] / [SigningState.Failed] — terminal sheets.
 *
 * **Narrow surface (R.X.7).** Takes only the chrome handles it needs:
 * the [SigningCommands] (read state + dispatch transitions) + two
 * callbacks for picking the key source. The actual signing pipeline
 * lives in `format/pdf/signing/`; this sheet is pure UI.
 *
 * **No emoji, plain copy** per the Editorial section of the project's
 * CLAUDE.md — the signature surface is one the user shows other
 * people.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SigningSheet(
  commands: SigningCommands,
  onPickKeystore: () -> Unit,
  onPickPkcs12: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val state by commands.state.collectAsStateWithLifecycle()
  if (state is SigningState.Idle) return

  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  ModalBottomSheet(
    onDismissRequest = { commands.cancel() },
    sheetState = sheetState,
    modifier = modifier.semantics { testTag = "signing_sheet" },
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 24.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text(
        text = stringResource(R.string.sign_sheet_title),
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.semantics { testTag = "signing_sheet_title" },
      )
      when (val s = state) {
        is SigningState.DrawingStamp -> DrawingStampBody(commands)
        is SigningState.PlacingStamp -> PlacingStampBody()
        is SigningState.KeySelecting -> KeySelectingBody(onPickKeystore, onPickPkcs12)
        is SigningState.Signing -> SigningProgressBody(progress = s.progress)
        is SigningState.Success -> SuccessBody()
        is SigningState.Failed -> FailedBody(reason = s.reason)
        is SigningState.Idle -> Unit // unreachable, guarded above
      }
      Spacer(Modifier.height(8.dp))
      TextButton(
        onClick = { commands.cancel() },
        modifier = Modifier.semantics { testTag = "signing_sheet_cancel" },
      ) {
        Text(stringResource(R.string.sign_sheet_cancel))
      }
    }
  }
}

@Composable
private fun DrawingStampBody(commands: SigningCommands) {
  Text(
    text = stringResource(R.string.sign_sheet_state_drawing),
    style = MaterialTheme.typography.bodyMedium,
    modifier = Modifier.semantics { testTag = "signing_sheet_drawing" },
  )
  // v1 placeholder pad — commits an empty PNG so the state machine
  // advances to PlacingStamp. Phase G's androidx.ink capture pipeline
  // replaces this with a real Canvas + stroke buffer once it merges.
  OutlinedButton(
    onClick = { commands.commit(EMPTY_PNG_PLACEHOLDER) },
    modifier = Modifier.semantics { testTag = "signing_sheet_drawing_done" },
  ) {
    Text("Done")
  }
}

@Composable
private fun PlacingStampBody() {
  Text(
    text = stringResource(R.string.sign_sheet_state_placing),
    style = MaterialTheme.typography.bodyMedium,
    modifier = Modifier.semantics { testTag = "signing_sheet_placing" },
  )
}

@Composable
private fun KeySelectingBody(onPickKeystore: () -> Unit, onPickPkcs12: () -> Unit) {
  Text(
    text = stringResource(R.string.sign_sheet_state_key_select),
    style = MaterialTheme.typography.bodyMedium,
    modifier = Modifier.semantics { testTag = "signing_sheet_key_select" },
  )
  OutlinedButton(
    onClick = onPickKeystore,
    modifier = Modifier
      .fillMaxWidth()
      .semantics { testTag = "signing_sheet_pick_keystore" },
  ) {
    Column {
      Text(stringResource(R.string.sign_keys_keystore_label))
      Text(
        text = stringResource(R.string.sign_keys_keystore_subtitle),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
  OutlinedButton(
    onClick = onPickPkcs12,
    modifier = Modifier
      .fillMaxWidth()
      .semantics { testTag = "signing_sheet_pick_p12" },
  ) {
    Column {
      Text(stringResource(R.string.sign_keys_p12_label))
      Text(
        text = stringResource(R.string.sign_keys_p12_subtitle),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
private fun SigningProgressBody(progress: Float) {
  Text(
    text = stringResource(R.string.sign_sheet_state_signing),
    style = MaterialTheme.typography.bodyMedium,
    modifier = Modifier.semantics { testTag = "signing_sheet_progress_label" },
  )
  LinearProgressIndicator(
    progress = { progress.coerceIn(0f, 1f) },
    modifier = Modifier
      .fillMaxWidth()
      .semantics { testTag = "signing_sheet_progress" },
  )
}

@Composable
private fun SuccessBody() {
  Text(
    text = stringResource(R.string.sign_sheet_state_success),
    style = MaterialTheme.typography.bodyLarge,
    color = MaterialTheme.colorScheme.primary,
    modifier = Modifier.semantics { testTag = "signing_sheet_success" },
  )
}

@Composable
private fun FailedBody(reason: String) {
  Text(
    text = stringResource(R.string.sign_sheet_state_failed, reason),
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.error,
    modifier = Modifier.semantics { testTag = "signing_sheet_failed" },
  )
}

/**
 * 1x1 fully-transparent PNG. Stand-in for the captured signature until
 * Phase G's `androidx.ink` capture pipeline merges and replaces the
 * placeholder pad.
 */
private val EMPTY_PNG_PLACEHOLDER: ByteArray = byteArrayOf(
  0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
  // IHDR
  0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
  0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
  0x08, 0x06, 0x00, 0x00, 0x00, 0x1F, 0x15.toByte(), 0xC4.toByte(), 0x89.toByte(),
  // IDAT
  0x00, 0x00, 0x00, 0x0D, 0x49, 0x44, 0x41, 0x54,
  0x78, 0x9C.toByte(), 0x63, 0x00, 0x01, 0x00, 0x00, 0x05,
  0x00, 0x01, 0x0D, 0x0A, 0x2D, 0xB4.toByte(),
  // IEND
  0x00, 0x00, 0x00, 0x00, 0x49, 0x45, 0x4E, 0x44,
  0xAE.toByte(), 0x42, 0x60, 0x82.toByte(),
)
