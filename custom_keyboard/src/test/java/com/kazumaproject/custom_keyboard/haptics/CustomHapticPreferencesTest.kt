package com.kazumaproject.custom_keyboard.haptics

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CustomHapticPreferencesTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    @Before fun clear() { PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit() }

    @Test fun allKindsSaveAndRestoreIndependentlyIncludingEmptyAndTenSteps() {
        val preferences = CustomHapticPreferences(context)
        val patterns = listOf(HapticPattern(emptyList()), HapticPattern(List(10) { HapticStep(it.toLong(), it) }),
            HapticPattern(listOf(HapticStep(0, 255), HapticStep(1, 32))),
            HapticPattern(listOf(HapticStep(5, 50), HapticStep(5, 100), HapticStep(5, 50))),
            HapticPattern(listOf(HapticStep(5, 50))),
            HapticPattern(listOf(HapticStep(5, 50), HapticStep(25, 0), HapticStep(15, 100))))
        HapticPatternKind.entries.forEachIndexed { index, kind -> preferences.savePattern(kind, patterns[index]) }
        preferences.setMediaUsage(true)
        val restored = CustomHapticPreferences(context)
        HapticPatternKind.entries.forEachIndexed { index, kind -> assertEquals(patterns[index], restored.pattern(kind)) }
        assertTrue(restored.useMediaUsage())
    }

    @Test fun malformedPatternIsRejectedAsAWhole() {
        val preferences = CustomHapticPreferences(context)
        for (raw in listOf("5,32;bad;5,96", "5,32;", "-1,32", "5,-1", "5,256", " ")) {
            assertNull(raw, preferences.parsePattern(raw))
        }
        assertThrows(IllegalArgumentException::class.java) { HapticStep(-1, 0) }
        assertThrows(IllegalArgumentException::class.java) { HapticStep(0, -1) }
        assertThrows(IllegalArgumentException::class.java) { HapticStep(0, 256) }
        assertEquals(HapticStep(0, 0), HapticStep(0, 0))
    }

    @Test fun existingPreferencesAreNotOverwrittenByPatternEditing() {
        val raw = PreferenceManager.getDefaultSharedPreferences(context)
        raw.edit().putString("vibration_timing", "release").putBoolean("vibration_preference", false).commit()
        val preferences = CustomHapticPreferences(context)
        preferences.savePattern(HapticPatternKind.SPECIAL_KEY, HapticPattern.SpecialKeyDefault)
        assertEquals("release", raw.getString("vibration_timing", null))
        assertFalse(preferences.isVibrationEnabled())
        assertFalse(preferences.isCustomModeEnabled())
    }

    @Test fun absentStageSettingsAreCompatibleWithOldBackups() {
        val preferences = CustomHapticPreferences(context)
        assertFalse(preferences.isStageEnabled())
        assertEquals(FlickStageHapticSettings(), preferences.stageSettings())
        assertEquals(listOf(50, 100, 50), preferences.pattern(HapticPatternKind.STAGE_ADVANCE).steps.map { it.amplitude })
        assertEquals(listOf(50), preferences.pattern(HapticPatternKind.STAGE_RETREAT).steps.map { it.amplitude })
        assertEquals(listOf(50, 0, 50, 100, 50), preferences.pattern(HapticPatternKind.STAGE_CHANGE).steps.map { it.amplitude })
    }

    @Test fun importedValuesAreClampedAndUnknownModeFallsBack() {
        val raw = PreferenceManager.getDefaultSharedPreferences(context)
        raw.edit().putInt(CustomHapticPreferences.KEY_STAGE_DELAY_MS, -10)
            .putInt(CustomHapticPreferences.KEY_STAGE_SUPPRESSION_MS, 500)
            .putString(CustomHapticPreferences.KEY_STAGE_CHANGE_MODE, "future-mode").commit()
        assertEquals(FlickStageHapticSettings(true, 200, StageChangeMode.APPLICATION, 0),
            CustomHapticPreferences(context).stageSettings())
    }
}
