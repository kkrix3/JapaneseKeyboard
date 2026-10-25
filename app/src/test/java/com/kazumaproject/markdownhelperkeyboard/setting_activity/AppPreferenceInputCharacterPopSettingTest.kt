package com.kazumaproject.markdownhelperkeyboard.setting_activity

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
