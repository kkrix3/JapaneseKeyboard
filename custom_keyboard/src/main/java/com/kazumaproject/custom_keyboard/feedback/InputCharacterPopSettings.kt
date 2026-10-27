package com.kazumaproject.custom_keyboard.feedback

import android.content.SharedPreferences

/** Independent animation parameters. A normalized snapshot is captured for each accepted input. */
data class InputCharacterPopSettings(
    val durationMs: Int = 280,
    val startScalePercent: Int = 100,
    val endScalePercent: Int = 180,
    val startOpacityPercent: Int = 100,
    val endOpacityPercent: Int = 0,
    val fadeStartPercent: Int = 20,
    val scaleEasing: PopEasing = PopEasing.EASE_OUT,
    val fadeEasing: PopEasing = PopEasing.EASE_OUT,
    val distanceDp: Int = 20,
) {
    fun normalized() = copy(
        durationMs = durationMs.coerceIn(50, 1500),
        startScalePercent = startScalePercent.coerceIn(10, 400),
        endScalePercent = endScalePercent.coerceIn(10, 400),
        startOpacityPercent = startOpacityPercent.coerceIn(0, 100),
        endOpacityPercent = endOpacityPercent.coerceIn(0, 100),
        fadeStartPercent = fadeStartPercent.coerceIn(0, 100),
        distanceDp = distanceDp.coerceIn(0, 100),
    )

    fun scale(progress: Float): Float = (startScalePercent +
        (endScalePercent - startScalePercent) * scaleEasing.transform(progress)) / 100f

    fun alpha(progress: Float): Float {
        val start = fadeStartPercent / 100f
        val t = when {
            progress >= 1f -> 1f
            start >= 1f -> 0f
            else -> ((progress - start) / (1f - start)).coerceIn(0f, 1f)
        }
        return (startOpacityPercent + (endOpacityPercent - startOpacityPercent) *
            fadeEasing.transform(t)) / 100f
    }
}

enum class PopEasing(val preferenceValue: String) {
    LINEAR("linear"), EASE_IN("ease_in"), EASE_OUT("ease_out"), EASE_IN_OUT("ease_in_out");

    fun transform(progress: Float): Float {
        val t = if (progress.isNaN()) 0f else progress.coerceIn(0f, 1f)
        return when (this) {
            LINEAR -> t
            EASE_IN -> t * t
            EASE_OUT -> 1f - (1f - t) * (1f - t)
            EASE_IN_OUT -> if (t < .5f) 2f * t * t else 1f - 2f * (1f - t) * (1f - t)
        }
    }

    companion object {
        fun fromPreference(value: String?) = entries.firstOrNull { it.preferenceValue == value } ?: EASE_OUT
    }
}

object InputCharacterPopPreferences {
    const val PREFIX = "input_character_pop_"
    fun read(prefs: SharedPreferences): InputCharacterPopSettings {
        // Backup/import can contain stale or incorrectly typed values. Never throw on those.
        val values = prefs.all
        fun int(name: String, default: Int) = values[PREFIX + name] as? Int ?: default
        fun easing(name: String) = PopEasing.fromPreference(values[PREFIX + name] as? String)
        return InputCharacterPopSettings(
            int("duration_ms", 280), int("start_scale", 100), int("end_scale", 180),
            int("start_opacity", 100), int("end_opacity", 0), int("fade_start", 20),
            easing("scale_easing"), easing("fade_easing"), int("distance_dp", 20)
        ).normalized()
    }

    fun write(prefs: SharedPreferences, settings: InputCharacterPopSettings) {
        val s = settings.normalized()
        prefs.edit().putInt(PREFIX + "duration_ms", s.durationMs)
            .putInt(PREFIX + "start_scale", s.startScalePercent)
            .putInt(PREFIX + "end_scale", s.endScalePercent)
            .putInt(PREFIX + "start_opacity", s.startOpacityPercent)
            .putInt(PREFIX + "end_opacity", s.endOpacityPercent)
            .putInt(PREFIX + "fade_start", s.fadeStartPercent)
            .putString(PREFIX + "scale_easing", s.scaleEasing.preferenceValue)
            .putString(PREFIX + "fade_easing", s.fadeEasing.preferenceValue)
            .putInt(PREFIX + "distance_dp", s.distanceDp).apply()
    }
}
