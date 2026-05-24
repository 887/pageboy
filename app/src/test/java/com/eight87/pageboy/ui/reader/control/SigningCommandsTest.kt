package com.eight87.pageboy.ui.reader.control

import android.net.Uri
import com.eight87.pageboy.TestApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Phase H.7 — state-machine contract for [InMemorySigningCommands].
 * Per-document signing flow walks Idle → DrawingStamp / KeySelecting →
 * (PlacingStamp | Signing) → (Success | Failed) → Idle (via cancel /
 * reset).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = TestApplication::class)
class SigningCommandsTest {

  @Test
  fun `initial state is Idle`() {
    val sc = InMemorySigningCommands()
    assertEquals(SigningState.Idle, sc.state.value)
  }

  @Test
  fun `start with visualStamp true moves into DrawingStamp`() {
    val sc = InMemorySigningCommands()
    sc.start(visualStamp = true)
    assertEquals(SigningState.DrawingStamp, sc.state.value)
  }

  @Test
  fun `start with visualStamp false moves into KeySelecting`() {
    val sc = InMemorySigningCommands()
    sc.start(visualStamp = false)
    assertEquals(SigningState.KeySelecting, sc.state.value)
  }

  @Test
  fun `commit from DrawingStamp advances to PlacingStamp carrying the bytes`() {
    val sc = InMemorySigningCommands()
    sc.start(visualStamp = true)
    val bytes = byteArrayOf(1, 2, 3, 4)
    sc.commit(bytes)
    val placing = sc.state.value as SigningState.PlacingStamp
    assertTrue(placing.pngBytes.contentEquals(bytes))
  }

  @Test
  fun `commit from KeySelecting starts a Signing transition`() {
    val sc = InMemorySigningCommands()
    sc.start(visualStamp = false)
    sc.commit(byteArrayOf())
    val signing = sc.state.value as SigningState.Signing
    assertEquals(0f, signing.progress, 0.001f)
  }

  @Test
  fun `cancel returns to Idle from any state`() {
    val sc = InMemorySigningCommands()
    sc.start(visualStamp = true)
    sc.cancel()
    assertEquals(SigningState.Idle, sc.state.value)
    sc.start(visualStamp = false)
    sc.cancel()
    assertEquals(SigningState.Idle, sc.state.value)
  }

  @Test
  fun `reportProgress clamps to 0-1`() {
    val sc = InMemorySigningCommands()
    sc.start(visualStamp = false)
    sc.commit(byteArrayOf())
    sc.reportProgress(0.75f)
    assertEquals(0.75f, (sc.state.value as SigningState.Signing).progress, 0.001f)
    sc.reportProgress(2f)
    assertEquals(1f, (sc.state.value as SigningState.Signing).progress, 0.001f)
    sc.reportProgress(-1f)
    assertEquals(0f, (sc.state.value as SigningState.Signing).progress, 0.001f)
  }

  @Test
  fun `reportSuccess transitions to Success carrying the URI`() {
    val sc = InMemorySigningCommands()
    sc.start(visualStamp = false)
    sc.commit(byteArrayOf())
    val uri = Uri.parse("content://example/signed.pdf")
    sc.reportSuccess(uri)
    val success = sc.state.value as SigningState.Success
    assertEquals(uri, success.savedUri)
  }

  @Test
  fun `reportFailure transitions to Failed carrying the reason`() {
    val sc = InMemorySigningCommands()
    sc.start(visualStamp = false)
    sc.commit(byteArrayOf())
    sc.reportFailure("p12 wrong password")
    assertEquals(SigningState.Failed("p12 wrong password"), sc.state.value)
  }

  @Test
  fun `reset returns to Idle preserving the instance`() {
    val sc = InMemorySigningCommands()
    sc.start(visualStamp = true)
    sc.commit(byteArrayOf(9, 9))
    sc.reset()
    assertEquals(SigningState.Idle, sc.state.value)
  }
}
