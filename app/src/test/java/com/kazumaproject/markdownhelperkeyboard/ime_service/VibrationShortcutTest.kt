package com.kazumaproject.markdownhelperkeyboard.ime_service

import android.app.Application
import android.content.Context
import android.os.Looper
import android.os.VibratorManager
import android.widget.FrameLayout
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.custom_keyboard.haptics.*
import com.kazumaproject.markdownhelperkeyboard.ime_service.adapters.ShortcutAdapter
import com.kazumaproject.markdownhelperkeyboard.ime_service.adapters.SuggestionAdapter
import com.kazumaproject.markdownhelperkeyboard.ime_service.candidate.QuickActionsState
import com.kazumaproject.markdownhelperkeyboard.ime_service.candidate.CandidateStripContent
import com.kazumaproject.markdownhelperkeyboard.ime_service.input_behavior.ResolvedInputBehavior
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import com.kazumaproject.markdownhelperkeyboard.short_cut.ShortcutType
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class VibrationShortcutTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val prefs = PreferenceManager.getDefaultSharedPreferences(context)

    @Before fun reset() {
        prefs.edit().clear().commit()
        AppPreference.init(context)
    }

    @Test fun onOffOnChangesOnlyTheExistingPreference() {
        prefs.edit().putBoolean(AppPreference.VIBRATION_KEY, true)
            .putString("vibration_timing", "custom")
            .putBoolean("custom_haptic_media_usage", true)
            .putString("custom_haptic_pattern_normal_flick", "5,32;20,0;5,255")
            .putString("custom_haptic_pattern_two_step_flick", "7,80")
            .putString("custom_haptic_pattern_special_key", "9,96")
            .putInt("other_setting", 42).commit()
        val before = prefs.all.toMap()
        assertFalse(AppPreference.toggleVibrationEnabled())
        assertEquals(before + (AppPreference.VIBRATION_KEY to false), prefs.all)
        assertFalse(CustomHapticPreferences(context).isVibrationEnabled())
        assertTrue(AppPreference.toggleVibrationEnabled())
        assertEquals(before, prefs.all)
    }

    @Test fun defaultOnAndSettingsChangesAreReadWithoutASecondState() {
        assertFalse(AppPreference.toggleVibrationEnabled())
        // Same store and key as the settings SwitchPreference.
        prefs.edit().putBoolean(AppPreference.VIBRATION_KEY, true).commit()
        assertFalse(AppPreference.toggleVibrationEnabled())
        AppPreference.init(context)
        assertEquals(false, AppPreference.vibration_preference)
        assertFalse(CustomHapticPreferences(context).isVibrationEnabled())
    }

    @Test fun toggleWorksForEveryVibrationTiming() {
        for (timing in listOf("press", "release", "both", "custom")) {
            AppPreference.vibration_timing_preference = timing
            AppPreference.vibration_preference = true
            assertFalse(AppPreference.toggleVibrationEnabled())
            assertTrue(AppPreference.toggleVibrationEnabled())
            assertEquals(timing, AppPreference.vibration_timing_preference)
        }
    }

    @Test fun offStateAndWaveformsRoundTripThroughExistingBackup() {
        val haptics = CustomHapticPreferences(context)
        val pattern = HapticPattern(listOf(HapticStep(5, 32), HapticStep(20, 0), HapticStep(5, 255)))
        haptics.savePattern(HapticPatternKind.NORMAL_FLICK, pattern)
        haptics.setMediaUsage(true)
        AppPreference.vibration_timing_preference = "custom"
        AppPreference.vibration_preference = true
        AppPreference.toggleVibrationEnabled()
        val before = prefs.all.toMap()
        val json = AppPreference.exportAllToJson()
        prefs.edit().clear().commit()
        AppPreference.importAllFromJson(json)
        assertEquals(before, prefs.all)
        assertFalse(haptics.isVibrationEnabled())
        AppPreference.init(context)
        assertEquals(false, AppPreference.vibration_preference)
        assertTrue(AppPreference.toggleVibrationEnabled())
        assertEquals(pattern, CustomHapticPreferences(context).pattern(HapticPatternKind.NORMAL_FLICK))
    }

    @Test fun customPlayerIsSilentWhenOffAndReusesWaveformWhenOn() {
        val haptics = CustomHapticPreferences(context)
        val pattern = HapticPattern(listOf(HapticStep(7, 80), HapticStep(13, 0), HapticStep(11, 96)))
        HapticPatternKind.entries.forEach { haptics.savePattern(it, pattern) }
        haptics.setMediaUsage(true)
        AppPreference.vibration_timing_preference = "custom"
        AppPreference.vibration_preference = true
        val vibrator = context.getSystemService(VibratorManager::class.java).defaultVibrator
        val shadow = shadowOf(vibrator)
        shadow.setHasVibrator(true)
        shadow.setHasAmplitudeControl(true)
        val player = CustomHapticPlayer(context, haptics)
        assertFalse(AppPreference.toggleVibrationEnabled())
        HapticPatternKind.entries.forEach { player.play(it) }
        player.preview(pattern)
        assertFalse(shadow.isVibrating)
        assertTrue(AppPreference.toggleVibrationEnabled())
        player.play(HapticPatternKind.NORMAL_FLICK)
        assertTrue(shadow.isVibrating)
        assertArrayEquals(longArrayOf(7, 13, 11), shadow.pattern)
        assertEquals(pattern, haptics.pattern(HapticPatternKind.NORMAL_FLICK))
    }

    @Test fun imeToggleUpdatesLegacyCacheAndDoesNotVibrateItself() {
        val ime = Robolectric.buildService(IMEService::class.java).get()
        ime.appPreference = AppPreference
        val toggle = IMEService::class.java.getDeclaredMethod("toggleVibrationFromShortcut").apply {
            isAccessible = true
        }
        val vibration = IMEService::class.java.getDeclaredField("isVibration").apply {
            isAccessible = true
        }
        val vibrate = IMEService::class.java.getDeclaredMethod("vibrate").apply {
            isAccessible = true
        }
        val vibrator = ime.getSystemService(VibratorManager::class.java).defaultVibrator
        val shadow = shadowOf(vibrator)
        shadow.setHasVibrator(true)
        AppPreference.vibration_preference = true
        toggle.invoke(ime)
        assertEquals(false, vibration.get(ime))
        assertFalse(shadow.isVibrating)
        vibrate.invoke(ime)
        assertFalse(shadow.isVibrating)
        toggle.invoke(ime)
        assertEquals(true, vibration.get(ime))
        assertFalse(shadow.isVibrating)
        // The existing normal vibration path resumes after enabling.
        vibrate.invoke(ime)
        assertTrue(shadow.isVibrating)
    }

    @Test fun settingsChangesUseTheExistingRuntimePreferenceListenerToSyncCacheAndIcon() {
        val ime = Robolectric.buildService(IMEService::class.java).get()
        ime.appPreference = AppPreference
        val adapter = ShortcutAdapter()
        adapter.submitList(listOf(ShortcutType.VIBRATION_TOGGLE))
        IMEService::class.java.getDeclaredField("shortcutAdapter").apply {
            isAccessible = true
            set(ime, adapter)
        }
        val listener = IMEService::class.java.getDeclaredField("runtimeInputPreferenceListener").run {
            isAccessible = true
            get(ime) as android.content.SharedPreferences.OnSharedPreferenceChangeListener
        }
        val cache = IMEService::class.java.getDeclaredField("isVibration").apply {
            isAccessible = true
        }
        val holder = adapter.onCreateViewHolder(FrameLayout(context), 0)
        prefs.registerOnSharedPreferenceChangeListener(listener)
        try {
            for (enabled in listOf(false, true)) {
                prefs.edit().putBoolean(AppPreference.VIBRATION_KEY, enabled).commit()
                shadowOf(Looper.getMainLooper()).idle()
                assertEquals(enabled, cache.get(ime))
                adapter.onBindViewHolder(holder, 0)
                val type = ShortcutType.VIBRATION_TOGGLE
                val expected = if (enabled) type.activeIconResId else type.iconResId
                assertEquals(expected, shadowOf(holder.imageView.drawable).createdFromResId)
            }
        } finally {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    @Test fun independentAndIntegratedToolbarsUseTheTwoStateIcons() {
        val type = ShortcutType.VIBRATION_TOGGLE
        val adapter = ShortcutAdapter()
        adapter.submitList(listOf(type))
        val holder = adapter.onCreateViewHolder(FrameLayout(context), 0)
        val integrated = SuggestionAdapter()
        integrated.submitContent(CandidateStripContent.EmptyState(
            showShortcutEntry = false,
            quickActions = QuickActionsState(false, false, false, false, "", ""),
            clipboardPreview = null,
            shortcutItems = listOf(type),
            showIntegratedShortcuts = true,
        ))
        repeat(30) {
            shadowOf(Looper.getMainLooper()).idle()
            if (integrated.itemCount == 0) Thread.sleep(10)
        }
        assertEquals(1, integrated.itemCount)
        val integratedHolder = integrated.onCreateViewHolder(
            FrameLayout(context), integrated.getItemViewType(0)
        ) as SuggestionAdapter.ShortcutViewHolder
        for (enabled in listOf(false, true, false)) {
            val active = resolveShortcutActiveTypes(
                false, false, ResolvedInputBehavior.COMPOSING_TEXT, false,
                vibrationEnabled = enabled,
            )
            adapter.setActiveShortcutTypes(active)
            integrated.setActiveShortcutTypes(active)
            adapter.onBindViewHolder(holder, 0)
            integrated.onBindViewHolder(integratedHolder, 0)
            val expected = if (enabled) type.activeIconResId else type.iconResId
            assertEquals(expected, shadowOf(holder.imageView.drawable).createdFromResId)
            assertEquals(expected, shadowOf(integratedHolder.imageView.drawable).createdFromResId)
        }
        integrated.release()
    }
}
