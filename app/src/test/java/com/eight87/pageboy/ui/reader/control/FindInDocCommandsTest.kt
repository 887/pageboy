package com.eight87.pageboy.ui.reader.control

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase C.9 — find state contract. Per-format renderers wire real
 * match emission; this test pins the in-memory state shape every
 * impl must satisfy.
 */
class FindInDocCommandsTest {

  @Test
  fun `initial state is empty query and no matches`() {
    val find = InMemoryFindInDocCommands()
    assertEquals("", find.query.value)
    assertTrue(find.matches.value.isEmpty())
    assertEquals(-1, find.currentMatchIndex.value)
  }

  @Test
  fun `setQuery updates the query and resets matches when emptied`() {
    val find = InMemoryFindInDocCommands()
    find.setQuery("hello")
    assertEquals("hello", find.query.value)
    find.submitMatches(listOf(FindMatch(0, 5), FindMatch(10, 15)))
    assertEquals(2, find.matches.value.size)
    assertEquals(0, find.currentMatchIndex.value)
    find.setQuery("")
    assertEquals("", find.query.value)
    assertTrue(find.matches.value.isEmpty())
    assertEquals(-1, find.currentMatchIndex.value)
  }

  @Test
  fun `next wraps from end to beginning`() {
    val find = InMemoryFindInDocCommands()
    find.setQuery("x")
    find.submitMatches(listOf(FindMatch(0, 1), FindMatch(2, 3)))
    assertEquals(0, find.currentMatchIndex.value)
    find.next(); assertEquals(1, find.currentMatchIndex.value)
    find.next(); assertEquals(0, find.currentMatchIndex.value)
  }

  @Test
  fun `previous wraps from beginning to end`() {
    val find = InMemoryFindInDocCommands()
    find.setQuery("x")
    find.submitMatches(listOf(FindMatch(0, 1), FindMatch(2, 3), FindMatch(4, 5)))
    assertEquals(0, find.currentMatchIndex.value)
    find.previous(); assertEquals(2, find.currentMatchIndex.value)
    find.previous(); assertEquals(1, find.currentMatchIndex.value)
  }

  @Test
  fun `next is a no-op when there are no matches`() {
    val find = InMemoryFindInDocCommands()
    find.setQuery("nothing-matches")
    find.next()
    assertEquals(-1, find.currentMatchIndex.value)
  }

  @Test
  fun `clear resets every state field`() {
    val find = InMemoryFindInDocCommands()
    find.setQuery("hello")
    find.submitMatches(listOf(FindMatch(0, 5)))
    find.clear()
    assertEquals("", find.query.value)
    assertTrue(find.matches.value.isEmpty())
    assertEquals(-1, find.currentMatchIndex.value)
  }
}
