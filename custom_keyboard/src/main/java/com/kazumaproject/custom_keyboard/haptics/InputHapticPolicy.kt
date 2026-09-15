package com.kazumaproject.custom_keyboard.haptics

import com.kazumaproject.custom_keyboard.data.KeyAction
import com.kazumaproject.custom_keyboard.view.TfbiFlickDirection

/** Metadata belongs to the committed gesture, never to text length or a global pending flag. */
data class InputHapticContext(val isTwoStepFlick: Boolean = false) {
    companion object {
        /**
         * TFBi represents a first-stage output as (direction, direction), and some release
         * paths use (direction, TAP). Neither means that a second option was selected.
         */
        fun fromTwoStepDirections(
            first: TfbiFlickDirection,
            second: TfbiFlickDirection
        ): InputHapticContext = InputHapticContext(
            first != TfbiFlickDirection.TAP &&
                second != TfbiFlickDirection.TAP &&
                second != first
        )
    }
}

object InputHapticPolicy {
    fun classify(action: KeyAction, context: InputHapticContext): HapticPatternKind? = when (action) {
        KeyAction.DoNothing, KeyAction.Cancel -> null
        is KeyAction.Text -> textKind(action.text, context)
        // InputText also contains legacy command tokens in Sumire.
        is KeyAction.InputText -> when (action.text) {
            "" -> null
            "^_^", "ひらがな小文字", "濁点", "半濁点" -> HapticPatternKind.SPECIAL_KEY
            else -> textKind(action.text, context)
        }
        else -> HapticPatternKind.SPECIAL_KEY
    }

    private fun textKind(text: String, context: InputHapticContext): HapticPatternKind? = when {
        text.isEmpty() -> null
        context.isTwoStepFlick -> HapticPatternKind.TWO_STEP_FLICK
        else -> HapticPatternKind.NORMAL_FLICK
    }
}

/** The IME acknowledges an operation after handling it; rejected input never plays a pattern. */
class CommittedInputHaptics(private val play: (HapticPatternKind) -> Unit) {
    fun dispatch(action: KeyAction, context: InputHapticContext, applyInput: () -> Boolean): Boolean {
        val kind = InputHapticPolicy.classify(action, context)
        val accepted = applyInput()
        if (accepted && kind != null) play(kind)
        return accepted
    }
}

object LegacyHapticPolicy {
    fun isEnabled(rawMode: String?): Boolean = rawMode != CustomHapticPreferences.VALUE_CUSTOM

    fun shouldVibrate(rawMode: String?, onPress: Boolean): Boolean = when (rawMode) {
        "press" -> onPress
        "release" -> !onPress
        "both" -> true
        else -> false
    }
}
