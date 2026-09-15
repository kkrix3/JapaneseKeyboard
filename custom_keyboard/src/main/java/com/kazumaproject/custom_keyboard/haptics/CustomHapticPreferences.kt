package com.kazumaproject.custom_keyboard.haptics

import android.content.Context
import androidx.preference.PreferenceManager

class CustomHapticPreferences(context: Context) {
    private val prefs = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)

    fun isVibrationEnabled(): Boolean = prefs.getBoolean(KEY_VIBRATION_ENABLED, true)

    fun isCustomModeEnabled(): Boolean = prefs.getString(KEY_VIBRATION_TIMING, null) == VALUE_CUSTOM

    fun useMediaUsage(): Boolean = prefs.getBoolean(KEY_MEDIA_USAGE, false)

    fun pattern(kind: HapticPatternKind): HapticPattern {
        val raw = prefs.getString(patternKey(kind), null)
        return parsePattern(raw) ?: HapticPattern.defaultFor(kind)
    }

    fun savePattern(kind: HapticPatternKind, pattern: HapticPattern) {
        prefs.edit().putString(patternKey(kind), serializePattern(pattern)).apply()
    }

    fun setMediaUsage(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_MEDIA_USAGE, enabled).apply()
    }

    internal fun serializePattern(pattern: HapticPattern): String =
        pattern.steps.joinToString(separator = ";") { step ->
            "${step.durationMs},${step.amplitude}"
        }

    internal fun parsePattern(raw: String?): HapticPattern? {
        if (raw == null) return null
        if (raw.isEmpty()) return HapticPattern(emptyList())
        val tokens = raw.split(';')
        if (tokens.size > HapticPattern.MAX_STEPS) return null
        val steps = tokens.map { token ->
            val parts = token.split(',')
            if (parts.size != 2) return null
            val duration = parts[0].trim().toLongOrNull() ?: return null
            val amplitude = parts[1].trim().toIntOrNull() ?: return null
            if (duration < 0L || amplitude !in 0..255) return null
            HapticStep(duration, amplitude)
        }
        return HapticPattern(steps)
    }

    private fun patternKey(kind: HapticPatternKind): String = when (kind) {
        HapticPatternKind.NORMAL_FLICK -> KEY_PATTERN_NORMAL
        HapticPatternKind.TWO_STEP_FLICK -> KEY_PATTERN_TWO_STEP
        HapticPatternKind.SPECIAL_KEY -> KEY_PATTERN_SPECIAL
    }

    companion object {
        const val KEY_VIBRATION_ENABLED = "vibration_preference"
        const val KEY_VIBRATION_TIMING = "vibration_timing"
        const val VALUE_CUSTOM = "custom"
        const val KEY_MEDIA_USAGE = "custom_haptic_media_usage"
        const val KEY_PATTERN_NORMAL = "custom_haptic_pattern_normal_flick"
        const val KEY_PATTERN_TWO_STEP = "custom_haptic_pattern_two_step_flick"
        const val KEY_PATTERN_SPECIAL = "custom_haptic_pattern_special_key"
    }
}
