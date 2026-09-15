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
            HapticPattern(listOf(HapticStep(0, 255), HapticStep(1, 32))))
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
}
