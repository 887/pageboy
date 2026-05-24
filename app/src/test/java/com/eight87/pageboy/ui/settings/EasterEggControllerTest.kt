package com.eight87.pageboy.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class EasterEggControllerTest {

  @Test
  fun `first tap returns FirstPromptSnackbar`() {
    val controller = EasterEggController()
    assertEquals(EasterEggController.Outcome.FirstPromptSnackbar, controller.tap(nowMillis = 1_000L))
    assertEquals(1, controller.debugCount())
  }

  @Test
  fun `second tap within window returns SecondPromptSnackbar`() {
    val controller = EasterEggController()
    controller.tap(nowMillis = 1_000L)
    assertEquals(EasterEggController.Outcome.SecondPromptSnackbar, controller.tap(nowMillis = 2_000L))
    assertEquals(2, controller.debugCount())
  }

  @Test
  fun `third tap within window returns Reveal and resets counter`() {
    val controller = EasterEggController()
    controller.tap(nowMillis = 1_000L)
    controller.tap(nowMillis = 2_000L)
    assertEquals(EasterEggController.Outcome.Reveal, controller.tap(nowMillis = 3_000L))
    assertEquals(0, controller.debugCount())
  }

  @Test
  fun `tap outside window resets counter back to first prompt`() {
    val controller = EasterEggController(windowMillis = 5_000L)
    controller.tap(nowMillis = 1_000L)
    controller.tap(nowMillis = 2_000L)
    // 6+ seconds later — should reset to FirstPromptSnackbar
    assertEquals(EasterEggController.Outcome.FirstPromptSnackbar, controller.tap(nowMillis = 8_500L))
    assertEquals(1, controller.debugCount())
  }

  @Test
  fun `reveal then immediate tap starts a fresh sequence`() {
    val controller = EasterEggController()
    controller.tap(nowMillis = 1_000L)
    controller.tap(nowMillis = 2_000L)
    controller.tap(nowMillis = 3_000L) // Reveal — resets counter
    assertEquals(EasterEggController.Outcome.FirstPromptSnackbar, controller.tap(nowMillis = 3_500L))
  }

  @Test
  fun `default window is five seconds`() {
    assertEquals(5_000L, EasterEggController.DEFAULT_WINDOW_MILLIS)
  }
}
