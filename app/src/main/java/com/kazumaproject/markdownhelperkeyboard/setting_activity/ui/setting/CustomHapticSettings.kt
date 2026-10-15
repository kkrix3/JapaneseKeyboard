package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import androidx.preference.Preference
import androidx.preference.ListPreference
import androidx.preference.PreferenceFragmentCompat
import com.kazumaproject.custom_keyboard.haptics.CustomHapticPreferences
import com.kazumaproject.custom_keyboard.haptics.HapticPatternEditorDialog
import com.kazumaproject.custom_keyboard.haptics.HapticPatternKind

object CustomHapticSettings {
    fun bind(fragment: PreferenceFragmentCompat) {
        val patterns = mapOf(
            CustomHapticPreferences.KEY_PATTERN_NORMAL to HapticPatternKind.NORMAL_FLICK,
            CustomHapticPreferences.KEY_PATTERN_TWO_STEP to HapticPatternKind.TWO_STEP_FLICK,
            CustomHapticPreferences.KEY_PATTERN_SPECIAL to HapticPatternKind.SPECIAL_KEY,
            CustomHapticPreferences.KEY_PATTERN_STAGE_ADVANCE to HapticPatternKind.STAGE_ADVANCE,
            CustomHapticPreferences.KEY_PATTERN_STAGE_RETREAT to HapticPatternKind.STAGE_RETREAT,
            CustomHapticPreferences.KEY_PATTERN_STAGE_CHANGE to HapticPatternKind.STAGE_CHANGE
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
        val mode = fragment.findPreference<ListPreference>(CustomHapticPreferences.KEY_STAGE_CHANGE_MODE)
        val delay = fragment.findPreference<Preference>(CustomHapticPreferences.KEY_STAGE_DELAY_MS)
        delay?.isEnabled = mode?.value == "application"
        mode?.setOnPreferenceChangeListener { _, value ->
            delay?.isEnabled = value == "application"
            true
        }
    }
    private const val TAG = "custom-haptic-pattern-editor"
}
