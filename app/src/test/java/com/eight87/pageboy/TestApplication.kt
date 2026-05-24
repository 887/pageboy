package com.eight87.pageboy

import android.app.Application

/**
 * Robolectric uses this stub Application in unit tests instead of the real
 * [PageboyApplication]. Phase A's [PageboyApplication] is empty, but the
 * future graph wiring (Room database open, DataStore handles, SAF tree
 * persistence) will run on every `:app:testDebugUnitTest` worker if we let
 * Robolectric instantiate the real one. The override matches the same
 * pattern whisperboy uses for `LicensesScreenTest`.
 */
class TestApplication : Application()
