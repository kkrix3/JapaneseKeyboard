package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.kazumaproject.custom_keyboard.haptics.CustomHapticPreferences
import com.kazumaproject.custom_keyboard.haptics.HapticPatternEditorDialog
import com.kazumaproject.custom_keyboard.haptics.HapticPatternKind

object CustomHapticSettings {
    fun bind(fragment: PreferenceFragmentCompat) {
        val patterns = mapOf(
            CustomHapticPreferences.KEY_PATTERN_NORMAL to HapticPatternKind.NORMAL_FLICK,
            CustomHapticPreferences.KEY_PATTERN_TWO_STEP to HapticPatternKind.TWO_STEP_FLICK,
            CustomHapticPreferences.KEY_PATTERN_SPECIAL to HapticPatternKind.SPECIAL_KEY
        )
        patterns.forEach { (key, kind) ->
            fragment.findPreference<Preference>(key)?.setOnPreferenceClickListener {
                val manager = fragment.parentFragmentManager
                if (manager.findFragmentByTag(TAG) == null) {
                    HapticPatternEditorDialog.newInstance(kind).show(manager, TAG)
                }
                true
            }
        }
    }
    private const val TAG = "custom-haptic-pattern-editor"
}
