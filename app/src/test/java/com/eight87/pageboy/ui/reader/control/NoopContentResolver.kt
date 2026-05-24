package com.eight87.pageboy.ui.reader.control

import android.content.ContentResolver
import androidx.test.core.app.ApplicationProvider

/**
 * Phase C.9 — Robolectric-backed [ContentResolver] for projector tests.
 * Real `ContentResolver` is abstract; this hands back the one Robolectric
 * binds to the test [android.app.Application]. The projector tests
 * exercise the projector itself, not SAF — the resolver is only ever
 * touched by [com.eight87.pageboy.format.api.SafDocumentBytesSource],
 * which the projector constructs lazily; tests that go through the
 * failure paths (renderer throws, document not found) never reach it.
 */
internal val NoopContentResolver: ContentResolver
  get() = ApplicationProvider.getApplicationContext<android.content.Context>().contentResolver
