package com.kazumaproject.markdownhelperkeyboard.repository

import com.kazumaproject.markdownhelperkeyboard.short_cut.ShortcutType
import com.kazumaproject.markdownhelperkeyboard.short_cut.data.ShortcutItem
import com.kazumaproject.markdownhelperkeyboard.short_cut.database.ShortcutDao
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.*

class ShortcutRepositoryVibrationTest {
    @Test fun existingToolbarIsNotRewrittenOrAutomaticallyExtended() = runBlocking {
        val dao = mock<ShortcutDao>()
        val existing = listOf(ShortcutItem(typeId = "paste", sortOrder = 0),
            ShortcutItem(typeId = "settings", sortOrder = 1))
        whenever(dao.getAllShortcuts()).thenReturn(existing)
        whenever(dao.getAllShortcutsFlow()).thenReturn(flowOf(existing))
        val repository = ShortcutRepository(dao)
        repository.initDefaultShortcutsIfNeeded()
        verify(dao, never()).replaceAll(any())
        assertEquals(listOf(ShortcutType.PASTE, ShortcutType.SETTINGS), repository.enabledShortcutsFlow.first())
        val candidates = repository.getAllShortcutsWithStatus()
        assertEquals(ShortcutType.PASTE to true, candidates[0])
        assertEquals(ShortcutType.SETTINGS to true, candidates[1])
        assertTrue(ShortcutType.VIBRATION_TOGGLE to false in candidates)
    }

    @Test fun userSelectedToggleIsSavedByStableIdInTheSelectedOrder() = runBlocking {
        val dao = mock<ShortcutDao>()
        whenever(dao.getAllShortcutsFlow()).thenReturn(flowOf(emptyList()))
        val repository = ShortcutRepository(dao)
        val chosen = listOf(ShortcutType.PASTE, ShortcutType.VIBRATION_TOGGLE, ShortcutType.SETTINGS)
        repository.updateShortcuts(chosen)
        val saved = argumentCaptor<List<ShortcutItem>>()
        verify(dao).replaceAll(saved.capture())
        assertEquals(listOf("paste", "vibration_toggle", "settings"), saved.firstValue.map { it.typeId })
        assertEquals(listOf(0, 1, 2), saved.firstValue.map { it.sortOrder })
        assertEquals(chosen, saved.firstValue.map { ShortcutType.fromId(it.typeId) })
    }
}
