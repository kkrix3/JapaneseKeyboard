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

    @Test
    fun heldTextTapAndUpCommitTheSelectedTextAndDirection() {
        val committed = mutableListOf<Triple<KeyAction?, Boolean, FlickDirection>>()
        val heldActions = mutableListOf<KeyAction>()
        val listener = object : NoopCrossFlickListener() {
            override fun onFlickCommitted(
                fallbackAction: KeyAction?,
                isFlick: Boolean,
                direction: FlickDirection
            ) {
                committed += Triple(fallbackAction, isFlick, direction)
            }

            override fun onFlickUpAfterLongPress(
                action: KeyAction,
                isFlick: Boolean,
                direction: FlickDirection
            ) {
                heldActions += action
            }
        }
        val map = mapOf(
            FlickDirection.TAP to FlickAction.Input("かな"),
            FlickDirection.UP to FlickAction.Input("っ")
        )

        commitCrossFlickAction(FlickDirection.TAP, map, true, listener)
        commitCrossFlickAction(FlickDirection.UP, map, true, listener)

        assertEquals(listOf(
            Triple(KeyAction.Text("かな"), false, FlickDirection.TAP),
            Triple(KeyAction.Text("っ"), true, FlickDirection.UP)
        ), committed)
        assertEquals(emptyList<KeyAction>(), heldActions)
    }

    @Test
    fun heldSpecialActionKeepsItsExistingLongPressCallback() {
        val committed = mutableListOf<KeyAction?>()
        val heldActions = mutableListOf<Triple<KeyAction, Boolean, FlickDirection>>()
        val listener = object : NoopCrossFlickListener() {
            override fun onFlickCommitted(
                fallbackAction: KeyAction?,
                isFlick: Boolean,
                direction: FlickDirection
            ) {
                committed += fallbackAction
            }

            override fun onFlickUpAfterLongPress(
                action: KeyAction,
                isFlick: Boolean,
                direction: FlickDirection
            ) {
                heldActions += Triple(action, isFlick, direction)
            }
        }

        commitCrossFlickAction(
            FlickDirection.TAP,
            mapOf(FlickDirection.TAP to FlickAction.Action(KeyAction.ToggleDakuten)),
            true,
            listener
        )

        assertEquals(emptyList<KeyAction?>(), committed)
        assertEquals(
            listOf(Triple(KeyAction.ToggleDakuten, false, FlickDirection.TAP)),
            heldActions
        )
    }

}

private open class NoopCrossFlickListener : CrossFlickInputController.CrossFlickListener {
    override fun onPress(action: KeyAction) = Unit
    override fun onFlick(action: KeyAction, isFlick: Boolean) = Unit
    override fun onFlickLongPress(action: KeyAction) = Unit
    override fun onFlickUpAfterLongPress(action: KeyAction, isFlick: Boolean) = Unit
}
