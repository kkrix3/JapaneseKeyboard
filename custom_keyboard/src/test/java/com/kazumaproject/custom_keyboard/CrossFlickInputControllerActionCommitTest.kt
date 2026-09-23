package com.kazumaproject.custom_keyboard

import com.kazumaproject.custom_keyboard.controller.CrossFlickInputController
import com.kazumaproject.custom_keyboard.controller.commitCrossFlickAction
import com.kazumaproject.custom_keyboard.controller.isVisiblePopupAction
import com.kazumaproject.custom_keyboard.data.FlickAction
import com.kazumaproject.custom_keyboard.data.FlickDirection
import com.kazumaproject.custom_keyboard.data.KeyAction
import org.junit.Assert.assertEquals
import org.junit.Test

class CrossFlickInputControllerActionCommitTest {
    @Test
    fun tapActionUpCommitsThreeArgumentFlickCallbackWithoutLosingDirection() {
        val committed = mutableListOf<Triple<KeyAction, Boolean, FlickDirection>>()
        val listener = object : NoopCrossFlickListener() {
            override fun onFlick(
                action: KeyAction,
                isFlick: Boolean,
                direction: FlickDirection
            ) {
                committed += Triple(action, isFlick, direction)
            }
        }

        commitCrossFlickAction(
            currentDirection = FlickDirection.TAP,
            flickActionMap = mapOf(
                FlickDirection.TAP to FlickAction.Action(KeyAction.Paste)
            ),
            isLongPressTriggered = false,
            listener = listener
        )

        assertEquals(
            listOf(Triple(KeyAction.Paste, false, FlickDirection.TAP)),
            committed
        )
    }

    @Test
    fun actionUpNotifiesCommittedDirectionEvenWhenFallbackActionIsMissing() {
        val committed = mutableListOf<Pair<KeyAction?, FlickDirection>>()
        val listener = object : NoopCrossFlickListener() {
            override fun onFlickCommitted(
                fallbackAction: KeyAction?,
                isFlick: Boolean,
                direction: FlickDirection
            ) {
                committed += fallbackAction to direction
            }
        }

        commitCrossFlickAction(
            currentDirection = FlickDirection.UP_RIGHT_FAR,
            flickActionMap = emptyMap(),
            isLongPressTriggered = false,
            listener = listener
        )

        assertEquals(listOf(null to FlickDirection.UP_RIGHT_FAR), committed)
    }

    @Test
    fun doNothingActionIsNotVisibleInPopup() {
        assertEquals(false, isVisiblePopupAction(FlickAction.Action(KeyAction.DoNothing)))
    }

    @Test
    fun nonDoNothingActionRemainsVisibleInPopup() {
        assertEquals(true, isVisiblePopupAction(FlickAction.Action(KeyAction.Paste)))
    }

    @Test
    fun mixedSpecialFlickCommitsTextFromUpAndActionFromTap() {
        val committed = mutableListOf<Triple<KeyAction?, Boolean, FlickDirection>>()
        val listener = object : NoopCrossFlickListener() {
            override fun onFlickCommitted(
                fallbackAction: KeyAction?,
                isFlick: Boolean,
                direction: FlickDirection
            ) {
                committed += Triple(fallbackAction, isFlick, direction)
            }
        }
        val map = mapOf(
            FlickDirection.TAP to FlickAction.Action(KeyAction.ToggleDakuten),
            FlickDirection.UP to FlickAction.Input("っ")
        )

        commitCrossFlickAction(FlickDirection.TAP, map, false, listener)
        commitCrossFlickAction(FlickDirection.UP, map, false, listener)

        assertEquals(listOf(
            Triple(KeyAction.ToggleDakuten, false, FlickDirection.TAP),
            Triple(KeyAction.Text("っ"), true, FlickDirection.UP)
        ), committed)
        assertEquals(true, isVisiblePopupAction(map.getValue(FlickDirection.UP)))
    }
}

private open class NoopCrossFlickListener : CrossFlickInputController.CrossFlickListener {
    override fun onPress(action: KeyAction) = Unit
    override fun onFlick(action: KeyAction, isFlick: Boolean) = Unit
    override fun onFlickLongPress(action: KeyAction) = Unit
    override fun onFlickUpAfterLongPress(action: KeyAction, isFlick: Boolean) = Unit
}
