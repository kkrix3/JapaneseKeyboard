package com.kazumaproject.custom_keyboard.haptics

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.preference.PreferenceManager

/** One coordinator per keyboard view, shared by its recognizers. No input work is scheduled. */
class FlickStageHapticFeedback(
    context: Context,
    private val preferences: CustomHapticPreferences = CustomHapticPreferences(context),
    private val handler: Handler = Handler(Looper.getMainLooper()),
    private val play: (HapticPatternKind) -> Unit = CustomHapticPlayer(context)::playStage
) {
    private val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
    private val state = FlickStageHapticState()
    private var owner: Any? = null
    private var settings = FlickStageHapticSettings()
    private var pending: Runnable? = null
    private var revision = 0L
    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null || key == CustomHapticPreferences.KEY_VIBRATION_ENABLED ||
            key == CustomHapticPreferences.KEY_MEDIA_USAGE ||
            key.startsWith("flick_stage_haptic_") || key.startsWith("custom_haptic_pattern_stage_")) {
            // Includes master OFF, settings imports and an edited waveform. A fresh DOWN
            // takes the new configuration; no old reservation survives a settings change.
            cancel()
        }
    }

    fun begin(source: Any) {
        cancel()
        if (!preferences.isStageEnabled() || !preferences.isVibrationEnabled()) return
        owner = source
        settings = preferences.stageSettings()
        sharedPreferences.registerOnSharedPreferenceChangeListener(preferenceListener)
        trace("begin")
    }

    fun update(source: Any, path: List<String>) {
        if (owner !== source) return
        if (!preferences.isStageEnabled() || !preferences.isVibrationEnabled()) {
            cancel()
            return
        }
        val transition = state.update(path, SystemClock.uptimeMillis(), settings) ?: return
        cancelPending()
        trace("generated retreat=${transition.retreats} advance=${transition.advances} suppressed=${transition.suppressed}")
        if (transition.suppressed) return

        if (transition.retreats > 0 && transition.advances > 0 && settings.changeMode == StageChangeMode.COMPOSITE) {
            // One replacement uses the independent compound waveform. Remaining skipped
            // levels (if any) still produce their own retreat/advance commands.
            repeat(transition.retreats - 1) { emit(HapticPatternKind.STAGE_RETREAT) }
            emit(HapticPatternKind.STAGE_CHANGE)
            repeat(transition.advances - 1) { emit(HapticPatternKind.STAGE_ADVANCE) }
            return
        }
        repeat(transition.retreats) { emit(HapticPatternKind.STAGE_RETREAT) }
        if (transition.retreats > 0 && transition.advances > 0 && settings.changeMode == StageChangeMode.APPLICATION) {
            val expectedRevision = revision
            val task = Runnable {
                pending = null
                if (revision == expectedRevision && owner === source &&
                    preferences.isStageEnabled() && preferences.isVibrationEnabled()) {
                    repeat(transition.advances) { emit(HapticPatternKind.STAGE_ADVANCE) }
                }
            }
            pending = task
            // Start-to-start delay, independent of the duration of the retreat waveform.
            handler.postDelayed(task, settings.delayMs)
        } else {
            repeat(transition.advances) { emit(HapticPatternKind.STAGE_ADVANCE) }
        }
    }

    fun end(source: Any) {
        if (owner === source) cancel()
    }

    fun cancel() {
        cancelPending()
        if (owner != null) trace("end")
        owner = null
        state.reset()
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(preferenceListener)
        // Do not call Vibrator.cancel(): it would cancel existing release haptics as well.
        // An already submitted independent composite is intentionally allowed to finish.
    }

    private fun cancelPending() {
        revision++
        pending?.let {
            handler.removeCallbacks(it)
            trace("reservation canceled")
        }
        pending = null
    }

    private fun emit(kind: HapticPatternKind) {
        trace("play $kind")
        play(kind)
    }

    private fun trace(message: String) {
        if (Log.isLoggable(TAG, Log.DEBUG)) Log.d(TAG, message)
    }

    companion object {
        const val TAG = "FlickStageHaptics"
    }
}
