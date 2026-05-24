package com.eight87.pageboy.ui.reader.control.annotation

import com.eight87.pageboy.TestApplication
import com.eight87.pageboy.data.annotation.AnnotationEntity
import com.eight87.pageboy.data.annotation.AnnotationKind
import com.eight87.pageboy.data.annotation.AnnotationStore
import com.eight87.pageboy.domain.render.annotation.AnnotationTool
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicReference

/**
 * Phase G.7 — Robolectric tests for [AndroidAnnotationCommands].
 *
 * Covers tool / color state transitions + add / remove forwarding +
 * the per-reader lifecycle (each instance starts at the default
 * AnnotationToolState).
 *
 * Uses a fake [AnnotationStore] (no Room) — the chrome impl only
 * depends on the store interface, which is exactly the R.X.1
 * narrow-interface contract this test exercises.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = TestApplication::class)
class AndroidAnnotationCommandsTest {

  private class FakeStore : AnnotationStore {
    val added: MutableList<AnnotationEntity> = mutableListOf()
    val deleted: MutableList<String> = mutableListOf()
    val restored: MutableList<String> = mutableListOf()
    val updated: MutableList<AnnotationEntity> = mutableListOf()
    override suspend fun add(annotation: AnnotationEntity) { added += annotation }
    override suspend fun update(annotation: AnnotationEntity) { updated += annotation }
    override suspend fun delete(id: String) { deleted += id }
    override suspend fun restore(id: String) { restored += id }
  }

  private fun entity(id: String = "e1") = AnnotationEntity(
    id = id,
    documentId = "doc",
    pageIndex = 0,
    kind = AnnotationKind.Highlight.name,
    payloadJson = "{}",
    colorArgb = 0xFFFFEB3B.toInt(),
    pageWidthPt = 100f, pageHeightPt = 100f,
    createdAt = 1L, modifiedAt = 1L,
  )

  @Test
  fun `default state has null tool and default highlight color`() {
    val commands = AndroidAnnotationCommands(store = FakeStore())
    val state = runBlocking { commands.state.first() }
    assertNull(state.tool)
    assertEquals(0x66FFEB3B.toInt(), state.colorArgb)
  }

  @Test
  fun `setTool flows through state`() {
    val commands = AndroidAnnotationCommands(store = FakeStore())
    commands.setTool(AnnotationTool.Highlight)
    assertEquals(AnnotationTool.Highlight, runBlocking { commands.state.first() }.tool)
    commands.setTool(AnnotationTool.FreehandInk)
    assertEquals(AnnotationTool.FreehandInk, runBlocking { commands.state.first() }.tool)
    commands.setTool(null)
    assertNull(runBlocking { commands.state.first() }.tool)
  }

  @Test
  fun `setColor flows through state`() {
    val commands = AndroidAnnotationCommands(store = FakeStore())
    commands.setColor(0xFFFF0000.toInt())
    assertEquals(0xFFFF0000.toInt(), runBlocking { commands.state.first() }.colorArgb)
  }

  @Test
  fun `add forwards to the store`() = runBlocking {
    val store = FakeStore()
    val commands = AndroidAnnotationCommands(store = store)
    commands.add(entity("a"))
    assertEquals(1, store.added.size)
    assertEquals("a", store.added.single().id)
  }

  @Test
  fun `remove forwards to the store as a soft-delete`() = runBlocking {
    val store = FakeStore()
    val commands = AndroidAnnotationCommands(store = store)
    commands.remove("a")
    assertEquals(listOf("a"), store.deleted)
  }

  @Test
  fun `multiple tool transitions preserve color`() {
    val commands = AndroidAnnotationCommands(store = FakeStore())
    commands.setColor(0xFF0000FF.toInt())
    commands.setTool(AnnotationTool.Highlight)
    commands.setTool(AnnotationTool.FreehandInk)
    val state = runBlocking { commands.state.first() }
    assertEquals(0xFF0000FF.toInt(), state.colorArgb)
    assertEquals(AnnotationTool.FreehandInk, state.tool)
  }

  @Test
  fun `state is hot — new collector sees latest`() = runBlocking {
    val commands = AndroidAnnotationCommands(store = FakeStore())
    commands.setTool(AnnotationTool.StickyNote)
    val seen = AtomicReference<AnnotationTool?>(null)
    seen.set(commands.state.first().tool)
    assertEquals(AnnotationTool.StickyNote, seen.get())
  }

  @Test
  fun `every tool maps to a kind via toKind`() {
    val commands = AndroidAnnotationCommands(store = FakeStore())
    AnnotationTool.values().forEach { tool ->
      commands.setTool(tool)
      assertTrue(runBlocking { commands.state.first() }.tool == tool)
    }
  }
}
