package com.eight87.pageboy.ui.reader.control

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Phase C.4 / R.C — share / export commands. Narrow surface (R.X.1):
 * the chrome's overflow menu calls [shareCurrentDocument]; renderers
 * that ship export-with-annotations (Phase G+) extend a sibling
 * `ExportCommands` interface that lands then.
 *
 * Per-document state is passed in by the chrome at call time
 * ([documentUriString] + [displayName]) rather than held on this object,
 * because the commands are app-scoped (one [ShareExportCommands] off
 * the AppGraph) but the document context is per-reader-instance.
 */
interface ShareExportCommands {

  /**
   * Fire an Android share intent for the document. The chrome wires
   * the resolved SAF URI + display name from the current
   * [ReaderState.Open]; this impl produces the intent + launches it.
   */
  fun shareCurrentDocument(documentUriString: String, displayName: String)
}

/**
 * Default Android [ShareExportCommands]. Builds an `ACTION_SEND` intent
 * with the SAF URI + the entity's display name, wrapped in a chooser
 * so the user picks the target app.
 *
 * Phase C ships the wiring. The behaviour is verified end-to-end in
 * Phase D+ when there's an actual format renderer to share from —
 * for Phase C smoke purposes the share affordance is reachable but
 * tapping it on a [com.eight87.pageboy.format.placeholder.PlaceholderHandle]
 * shares a `content://` URI that the user pointed pageboy at, which is
 * still a meaningful action (e.g. forwarding the file from the library
 * to another app without first having to open the system file manager).
 */
class AndroidShareExportCommands(
  private val context: Context,
) : ShareExportCommands {

  override fun shareCurrentDocument(documentUriString: String, displayName: String) {
    val uri = Uri.parse(documentUriString)
    val intent = Intent(Intent.ACTION_SEND).apply {
      type = context.contentResolver.getType(uri) ?: "application/octet-stream"
      putExtra(Intent.EXTRA_STREAM, uri)
      putExtra(Intent.EXTRA_TITLE, displayName)
      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = Intent.createChooser(intent, displayName).apply {
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(chooser)
  }
}
