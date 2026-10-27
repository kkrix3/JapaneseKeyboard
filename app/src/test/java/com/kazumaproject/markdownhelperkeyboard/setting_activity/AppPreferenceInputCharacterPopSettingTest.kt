package com.kazumaproject.markdownhelperkeyboard.setting_activity

import com.kazumaproject.custom_keyboard.feedback.InputCharacterPopSettings
import com.kazumaproject.custom_keyboard.feedback.PopEasing
import com.kazumaproject.markdownhelperkeyboard.R
import android.content.Context
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppPreferenceInputCharacterPopSettingTest {
    @Test fun allParametersPersistRestoreFromBackupAndRecoverInvalidValues() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        prefs.edit().clear().commit()
        AppPreference.init(context)
        assertEquals(InputCharacterPopSettings(), AppPreference.inputCharacterPopSettings)
        val custom = InputCharacterPopSettings(1500, 400, 10, 0, 100, 100,
            PopEasing.EASE_IN_OUT, PopEasing.LINEAR, 0)
        AppPreference.inputCharacterPopSettings = custom
        AppPreference.init(context)
        assertEquals(custom, AppPreference.inputCharacterPopSettings)
        val backup = AppPreference.exportAllToJson()
        AppPreference.inputCharacterPopSettings = InputCharacterPopSettings()
        AppPreference.importAllFromJson(backup)
        assertEquals(custom, AppPreference.inputCharacterPopSettings)
        prefs.edit().putInt("input_character_pop_duration_ms", -1)
            .putInt("input_character_pop_distance_dp", 999)
            .putString("input_character_pop_scale_easing", "bad")
            .putString("input_character_pop_start_scale", "wrong type").commit()
        val safe = AppPreference.inputCharacterPopSettings
        assertEquals(50, safe.durationMs); assertEquals(100, safe.distanceDp)
        assertEquals(PopEasing.EASE_OUT, safe.scaleEasing)
        assertEquals(100, safe.startScalePercent)
        prefs.edit().clear().commit()
    }

    @Test fun bothSettingsScreensExposeMatchingDefaultsLimitsAndDependencies() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val android = "http://schemas.android.com/apk/res/android"
        val app = "http://schemas.android.com/apk/res-auto"
        val expected = mapOf(
            "duration_ms" to listOf(280, 50, 1500, 10),
            "start_scale" to listOf(100, 10, 400, 5), "end_scale" to listOf(180, 10, 400, 5),
            "start_opacity" to listOf(100, 0, 100, 1), "end_opacity" to listOf(0, 0, 100, 1),
            "fade_start" to listOf(20, 0, 100, 1), "distance_dp" to listOf(20, 0, 100, 1))
        for (screen in listOf(R.xml.pref_keyboard_display, R.xml.pref_common_legacy)) {
            val found = mutableSetOf<String>()
            context.resources.getXml(screen).use { parser ->
                while (parser.next() != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                    if (parser.eventType != org.xmlpull.v1.XmlPullParser.START_TAG) continue
                    val key = parser.getAttributeValue(android, "key") ?: continue
                    val suffix = key.removePrefix("input_character_pop_")
                    val spec = expected[suffix] ?: continue
                    found += suffix
                    assertEquals("input_character_pop_animation_preference", parser.getAttributeValue(android, "dependency"))
                    assertEquals(spec[0], parser.getAttributeIntValue(android, "defaultValue", -1))
                    assertEquals(spec[1], parser.getAttributeIntValue(app, "min", -1))
                    assertEquals(spec[2], parser.getAttributeIntValue(android, "max", -1))
                    assertEquals(spec[3], parser.getAttributeIntValue(app, "seekBarIncrement", -1))
                }
            }
            assertEquals(expected.keys, found)
        }
    }

    @Test fun defaultsOffPersistsAndSurvivesBackupWithoutChangingTouchEffect() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        prefs.edit().clear().commit()
        AppPreference.init(context)
        assertFalse(AppPreference.input_character_pop_animation_preference)
        val effect = AppPreference.keyboard_touch_effect_type_preference
        AppPreference.input_character_pop_animation_preference = true
        AppPreference.init(context)
        assertTrue(AppPreference.input_character_pop_animation_preference)
        val backup = AppPreference.exportAllToJson()
        AppPreference.input_character_pop_animation_preference = false
        AppPreference.importAllFromJson(backup)
        assertTrue(AppPreference.input_character_pop_animation_preference)
        assertEquals(effect, AppPreference.keyboard_touch_effect_type_preference)
        prefs.edit().clear().commit()
    }
}
