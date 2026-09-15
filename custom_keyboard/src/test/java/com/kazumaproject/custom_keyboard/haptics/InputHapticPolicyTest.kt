package com.kazumaproject.custom_keyboard.haptics

import com.kazumaproject.custom_keyboard.data.KeyAction
import com.kazumaproject.custom_keyboard.view.TfbiFlickDirection
import org.junit.Assert.*
import org.junit.Test

class InputHapticPolicyTest {
    @Test fun twoStepCoordinatesDistinguishBaseOutputsFromSecondOptions() {
        for ((first, second) in listOf(
            TfbiFlickDirection.TAP to TfbiFlickDirection.TAP,
            TfbiFlickDirection.LEFT to TfbiFlickDirection.LEFT,
            TfbiFlickDirection.DOWN_RIGHT to TfbiFlickDirection.DOWN_RIGHT,
            TfbiFlickDirection.LEFT to TfbiFlickDirection.TAP,
            TfbiFlickDirection.TAP to TfbiFlickDirection.UP,
        )) {
            assertEquals("$first / $second", HapticPatternKind.NORMAL_FLICK,
                InputHapticPolicy.classify(KeyAction.Text("かな"),
                    InputHapticContext.fromTwoStepDirections(first, second)))
        }
        assertEquals(HapticPatternKind.TWO_STEP_FLICK,
            InputHapticPolicy.classify(KeyAction.Text("か"),
                InputHapticContext.fromTwoStepDirections(
                    TfbiFlickDirection.LEFT, TfbiFlickDirection.RIGHT)))
    }

    @Test fun committedActionsPlayExactlyOnceAfterInput() {
        for ((action, context, expected) in listOf(
            Triple(KeyAction.Text("あ"), InputHapticContext(), HapticPatternKind.NORMAL_FLICK),
            Triple(KeyAction.Text("こう"), InputHapticContext(true), HapticPatternKind.TWO_STEP_FLICK),
            Triple(KeyAction.Delete, InputHapticContext(true), HapticPatternKind.SPECIAL_KEY)
        )) {
            val events = mutableListOf<String>()
            CommittedInputHaptics { events += it.name }.dispatch(action, context) {
                events += "input"; true
            }
            assertEquals(listOf("input", expected.name), events)
        }
    }

    @Test fun rejectionEmptyAndCancellationRemainSilent() {
        val played = mutableListOf<HapticPatternKind>()
        val feedback = CommittedInputHaptics { played += it }
        feedback.dispatch(KeyAction.Text("あ"), InputHapticContext()) { false }
        for (action in listOf(KeyAction.DoNothing, KeyAction.Cancel, KeyAction.Text(""))) {
            feedback.dispatch(action, InputHapticContext(true)) { true }
        }
        assertTrue(played.isEmpty())
    }

    @Test fun textLengthDoesNotDetermineGestureAndLegacyCommandsAreSpecial() {
        assertEquals(HapticPatternKind.NORMAL_FLICK,
            InputHapticPolicy.classify(KeyAction.Text("定型句"), InputHapticContext()))
        assertEquals(HapticPatternKind.TWO_STEP_FLICK,
            InputHapticPolicy.classify(KeyAction.Text("あ"), InputHapticContext(true)))
        assertEquals(HapticPatternKind.SPECIAL_KEY,
            InputHapticPolicy.classify(KeyAction.InputText("濁点"), InputHapticContext()))
        assertEquals(HapticPatternKind.NORMAL_FLICK,
            InputHapticPolicy.classify(KeyAction.InputText(":"), InputHapticContext()))
    }

    @Test fun legacyModesRetainTheirTruthTableAndCustomSuppressesAllOldVibration() {
        val expectations = mapOf("press" to listOf(true, false), "release" to listOf(false, true),
            "both" to listOf(true, true), "custom" to listOf(false, false))
        expectations.forEach { (mode, expected) ->
            assertEquals(expected, listOf(LegacyHapticPolicy.shouldVibrate(mode, true),
                LegacyHapticPolicy.shouldVibrate(mode, false)))
            assertEquals(mode != "custom", LegacyHapticPolicy.isEnabled(mode))
        }
    }
}
