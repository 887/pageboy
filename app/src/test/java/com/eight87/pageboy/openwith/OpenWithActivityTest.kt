package com.eight87.pageboy.openwith

import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.eight87.pageboy.PageboyActivity
import com.eight87.pageboy.PageboyApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Phase N.13 — Robolectric activity test for [OpenWithActivity]. Fires
 * a synthetic `ACTION_VIEW` intent with a registered content stream
 * and asserts the activity launches [PageboyActivity] with the
 * resolved documentId extra.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = PageboyApplication::class)
class OpenWithActivityTest {

  @Test
  fun `view intent with pdf launches PageboyActivity with documentId extra`() {
    val app = ApplicationProvider.getApplicationContext<android.app.Application>()
    val uri = Uri.parse("content://activity-test/notes.pdf")
    val bytes = "%PDF-1.6\nbody\n%%EOF".toByteArray(Charsets.US_ASCII)
    shadowOf(app.contentResolver).registerInputStream(uri, bytes.inputStream())

    val intent = Intent(app, OpenWithActivity::class.java).apply {
      action = Intent.ACTION_VIEW
      // setDataAndType required: setType wipes setData unless paired.
      setDataAndType(uri, "application/pdf")
      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    ActivityScenario.launch<OpenWithActivity>(intent).use { scenario ->
      shadowOf(android.os.Looper.getMainLooper()).idle()
      scenario.onActivity { activity ->
        val shadow = shadowOf(activity)
        val next = shadow.nextStartedActivity
        assertNotNull("OpenWithActivity should start the reader activity", next)
        assertEquals(
          PageboyActivity::class.java.name,
          next.component?.className,
        )
        val docId = next.getStringExtra(PageboyActivity.EXTRA_INITIAL_DOCUMENT_ID)
        assertNotNull("PageboyActivity receives EXTRA_INITIAL_DOCUMENT_ID", docId)
        assertTrue("activity finished after dispatch", activity.isFinishing)
      }
    }
  }

  @Test
  fun `view intent without data finishes activity without launching reader`() {
    val app = ApplicationProvider.getApplicationContext<android.app.Application>()
    val intent = Intent(app, OpenWithActivity::class.java).apply {
      action = Intent.ACTION_VIEW
    }
    ActivityScenario.launch<OpenWithActivity>(intent).use { scenario ->
      shadowOf(android.os.Looper.getMainLooper()).idle()
      scenario.onActivity { activity ->
        val shadow = shadowOf(activity)
        val next = shadow.nextStartedActivity
        // No launched activity — failed dispatch finishes without forward.
        assertTrue("no follow-up activity on missing data", next == null)
        assertTrue("activity finished", activity.isFinishing)
      }
    }
  }
}
