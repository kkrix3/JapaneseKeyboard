package com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui

import com.kazumaproject.custom_keyboard.data.FlickAction
import com.kazumaproject.custom_keyboard.data.FlickDirection
import com.kazumaproject.custom_keyboard.data.KeyAction
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.FlickMapping
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.toDbStrings
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.toFlickAction
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui.adapter.SpecialFlickMappingItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpecialFlickTextInputTest {
    @Test
    fun mixedActionAndMultipleTextDirectionsRoundTripThroughExistingDbFormat() {
        val entries = listOf(
            SpecialFlickMappingItem(direction = FlickDirection.TAP, action = KeyAction.ToggleDakuten),
            SpecialFlickMappingItem(direction = FlickDirection.UP, action = KeyAction.InputText("っ")),
            SpecialFlickMappingItem(direction = FlickDirection.DOWN, action = KeyAction.InputText("日本語"))
        )

        val saved = entries.mapNotNull { item ->
            item.toFlickAction(null)?.let { action ->
                val (type, value) = action.toDbStrings()
                FlickMapping(1L, 0, item.direction, type, value)
            }
        }

        assertEquals(listOf("TOGGLE_DAKUTEN", "INPUT_TEXT", "INPUT_TEXT"), saved.map { it.actionType })
        assertEquals(listOf(null, "っ", "日本語"), saved.map { it.actionValue })
        assertEquals(entries.map { it.action }, saved.map { it.toFlickAction().toSpecialFlickEditorAction() })
        assertEquals(FlickAction.Input("っ"), saved[1].toFlickAction())
        assertEquals(KeyAction.Text("っ"), KeyAction.InputText("っ").toSpecialFlickTapAction())
        assertEquals(KeyAction.ToggleDakuten, KeyAction.ToggleDakuten.toSpecialFlickTapAction())
    }

    @Test
    fun unsetAndEmptyTextDoNotCreateMappings() {
        assertNull(SpecialFlickMappingItem(direction = FlickDirection.UP).toFlickAction(null))
        assertNull(SpecialFlickMappingItem(direction = FlickDirection.UP, action = KeyAction.InputText(""))
            .toFlickAction(null))
        assertNull((null as FlickAction?).toSpecialFlickEditorAction())
    }

    @Test
    fun existingActionAndOrdinaryInputMappingsRemainDistinct() {
        val action = FlickAction.Action(KeyAction.Delete)
        val input = FlickAction.Input("あ")
        assertEquals(KeyAction.Delete, action.toSpecialFlickEditorAction())
        assertEquals(KeyAction.InputText("あ"), input.toSpecialFlickEditorAction())
        assertEquals(FlickAction.Action(KeyAction.Delete),
            SpecialFlickMappingItem(direction = FlickDirection.TAP, action = KeyAction.Delete).toFlickAction(null))
        assertEquals("INPUT_TEXT" to "あ", input.toDbStrings())
    }
}
