package com.kazumaproject.custom_keyboard.haptics

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings

class CustomHapticPlayer(
    context: Context,
    private val preferences: CustomHapticPreferences = CustomHapticPreferences(context)
) {
    private val appContext = context.applicationContext

    @Suppress("DEPRECATION")
    private val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val manager = appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        manager.defaultVibrator
    } else {
        appContext.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    fun play(kind: HapticPatternKind) {
        if (!preferences.isVibrationEnabled()) return
        if (!preferences.isCustomModeEnabled()) return
        if (!vibrator.hasVibrator()) return

        val useMediaUsage = preferences.useMediaUsage()
        if (!useMediaUsage && !isTouchHapticsEnabled()) return

        playPattern(preferences.pattern(kind), useMediaUsage)
    }

    /** Preview an unsaved editor draft, while respecting both vibration enable switches. */
    fun preview(pattern: HapticPattern) {
        if (!preferences.isVibrationEnabled() || !vibrator.hasVibrator()) return
        val useMediaUsage = preferences.useMediaUsage()
        if (!useMediaUsage && !isTouchHapticsEnabled()) return
        playPattern(pattern, useMediaUsage)
    }

    private fun playPattern(pattern: HapticPattern, useMediaUsage: Boolean) {
        // Android rejects all-zero timings. OFF-only patterns deliberately remain silent.
        if (pattern.steps.none { it.durationMs > 0 && it.amplitude > 0 }) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            playWaveform(pattern, useMediaUsage)
        } else {
            playLegacy(pattern, useMediaUsage)
        }
    }

    private fun playWaveform(pattern: HapticPattern, useMediaUsage: Boolean) {
        val timings = pattern.steps.map { it.durationMs }.toLongArray()
        val amplitudes = waveformAmplitudes(pattern, vibrator.hasAmplitudeControl())

        val effect = VibrationEffect.createWaveform(timings, amplitudes, -1)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val usage = if (useMediaUsage) {
                VibrationAttributes.USAGE_MEDIA
            } else {
                VibrationAttributes.USAGE_TOUCH
            }
            val attributes = VibrationAttributes.Builder()
                .setUsage(usage)
                .build()
            vibrator.vibrate(effect, attributes)
        } else {
            vibrator.vibrate(effect, audioAttributes(useMediaUsage))
        }
    }

    @Suppress("DEPRECATION")
    private fun playLegacy(pattern: HapticPattern, useMediaUsage: Boolean) {
        val timings = toLegacyOnOffPattern(pattern)
        if (timings.isNotEmpty()) {
            vibrator.vibrate(timings, -1, audioAttributes(useMediaUsage))
        }
    }

    internal fun waveformAmplitudes(pattern: HapticPattern, amplitudeControl: Boolean): IntArray =
        pattern.steps.map { step ->
            when {
                step.amplitude == 0 -> 0
                amplitudeControl -> step.amplitude
                else -> VibrationEffect.DEFAULT_AMPLITUDE
            }
        }.toIntArray()

    private fun audioAttributes(useMediaUsage: Boolean): AudioAttributes = AudioAttributes.Builder()
        .setUsage(if (useMediaUsage) AudioAttributes.USAGE_MEDIA else AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    /**
     * Vibrator.vibrate(long[], repeat) alternates OFF/ON durations and always starts with OFF.
     * Amplitude information is unavailable before API 26, so all non-zero steps collapse to ON.
     */
    internal fun toLegacyOnOffPattern(pattern: HapticPattern): LongArray {
        val result = mutableListOf<Long>()
        var expectingOn = false // first entry is OFF

        fun append(duration: Long, isOn: Boolean) {
            if (duration <= 0L) return
            if (isOn == expectingOn) {
                result += duration
                expectingOn = !expectingOn
            } else if (result.isEmpty()) {
                result += 0L
                expectingOn = true
                append(duration, isOn)
            } else {
                result[result.lastIndex] += duration
            }
        }

        pattern.steps.forEach { step ->
            append(step.durationMs, step.amplitude > 0)
        }
        return result.toLongArray()
    }

    private fun isTouchHapticsEnabled(): Boolean {
        return Settings.System.getInt(
            appContext.contentResolver,
            Settings.System.HAPTIC_FEEDBACK_ENABLED,
            1
        ) != 0
    }
}
