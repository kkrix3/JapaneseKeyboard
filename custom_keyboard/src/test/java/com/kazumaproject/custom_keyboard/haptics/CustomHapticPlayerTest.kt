package com.kazumaproject.custom_keyboard.haptics

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertArrayEquals
import android.content.Context
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import androidx.preference.PreferenceManager
import org.junit.Assert.*
import org.robolectric.Shadows.shadowOf
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CustomHapticPlayerTest {

    @Test
    fun legacyFallbackPreservesOffOnTimingStructure() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val player = CustomHapticPlayer(context)
        val pattern = HapticPattern(
            listOf(
                HapticStep(5, 32),
                HapticStep(20, 0),
                HapticStep(5, 32)
            )
        )

        assertArrayEquals(
            longArrayOf(0, 5, 20, 5),
            player.toLegacyOnOffPattern(pattern)
        )
    }

    @Test
    fun legacyFallbackMergesConsecutiveSegmentsWithSameOnOffState() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val player = CustomHapticPlayer(context)
        val pattern = HapticPattern(
            listOf(
                HapticStep(3, 255),
                HapticStep(1, 32),
                HapticStep(500, 0),
                HapticStep(5, 32)
            )
        )

        assertArrayEquals(
            longArrayOf(0, 4, 500, 5),
            player.toLegacyOnOffPattern(pattern)
        )
    }

    @Test fun noAmplitudeControlPreservesEveryGapAndTiming() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val player = CustomHapticPlayer(context)
        val pattern = HapticPattern.TwoStepFlickDefault
        assertArrayEquals(intArrayOf(-1, 0, -1), player.waveformAmplitudes(pattern, false))
        assertArrayEquals(intArrayOf(32, 0, 32), player.waveformAmplitudes(pattern, true))
    }

    @Test fun touchOffMediaAndMasterSwitchAreRespected() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val raw = PreferenceManager.getDefaultSharedPreferences(context)
        raw.edit().clear().putString("vibration_timing", "custom").putBoolean("vibration_preference", true).commit()
        val vibrator = context.getSystemService(VibratorManager::class.java).defaultVibrator
        val shadow = shadowOf(vibrator)
        shadow.setHasVibrator(true)
        shadow.setHasAmplitudeControl(true)
        val preferences = CustomHapticPreferences(context)
        val player = CustomHapticPlayer(context, preferences)
        Settings.System.putInt(context.contentResolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, 0)
        player.play(HapticPatternKind.NORMAL_FLICK)
        assertFalse(shadow.isVibrating)
        preferences.setMediaUsage(true)
        player.play(HapticPatternKind.NORMAL_FLICK)
        assertTrue(shadow.isVibrating)
        vibrator.cancel()
        raw.edit().putBoolean("vibration_preference", false).commit()
        player.play(HapticPatternKind.SPECIAL_KEY)
        assertFalse(shadow.isVibrating)
    }

    @Test fun zeroDurationAndOffOnlyPatternsAreSafeAndSilent() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val raw = PreferenceManager.getDefaultSharedPreferences(context)
        raw.edit().clear().putString("vibration_timing", "custom").putBoolean("vibration_preference", true).commit()
        val vibrator = context.getSystemService(VibratorManager::class.java).defaultVibrator
        shadowOf(vibrator).setHasVibrator(true)
        val preferences = CustomHapticPreferences(context)
        preferences.setMediaUsage(true)
        val player = CustomHapticPlayer(context, preferences)
        for (pattern in listOf(HapticPattern(emptyList()), HapticPattern(listOf(HapticStep(0, 255))),
            HapticPattern(listOf(HapticStep(20, 0))))) {
            preferences.savePattern(HapticPatternKind.NORMAL_FLICK, pattern)
            player.play(HapticPatternKind.NORMAL_FLICK)
            assertFalse(shadowOf(vibrator).isVibrating)
        }
    }
}
