package com.kazumaproject.markdownhelperkeyboard.setting_activity.backup

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.custom_keyboard.haptics.*
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Exercises the upstream exporter/importer across separate preference namespaces. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class CustomHapticBackupCompatibilityTest {
    private fun app(name: String): Context = object : ContextWrapper(
        ApplicationProvider.getApplicationContext<Context>()
    ) {
        override fun getPackageName() = name
        override fun getApplicationContext(): Context = this
    }

    @Test
    fun officialPreferencesRestoreAcrossPackageBoundaryWithAllSupportedTypes() {
        val official = app("test.official.full")
        val preview = app("test.preview.full")
        val source = PreferenceManager.getDefaultSharedPreferences(official)
        val destination = PreferenceManager.getDefaultSharedPreferences(preview)
        source.edit().clear().putBoolean("vibration_preference", true)
            .putString("vibration_timing", "release")
            .putInt("future_integer", 7).putLong("future_long", 12345678901L)
            .putFloat("future_float", 0.25f).putStringSet("future_set", setOf("a", "あ"))
            .putBoolean("gemma_enable_preference", true).commit()
        destination.edit().clear().putString("preview_only", "removed by replaceAll").commit()
        AppPreference.init(official)
        val json = AppPreference.exportAllToJson()
        assertFalse(destination.contains("future_integer"))
        AppPreference.init(preview)
        AppPreference.importAllFromJson(json)
        assertEquals("release", destination.getString("vibration_timing", null))
        assertEquals(7, destination.getInt("future_integer", 0))
        assertEquals(12345678901L, destination.getLong("future_long", 0))
        assertEquals(0.25f, destination.getFloat("future_float", 0f))
        assertEquals(setOf("a", "あ"), destination.getStringSet("future_set", emptySet()))
        assertTrue(destination.getBoolean("gemma_enable_preference", false))
        assertFalse(destination.contains("preview_only"))
        assertTrue(source.contains("future_integer"))
    }

    @Test
    fun customWaveformsAndMediaFlagUseTheExistingSettingsBackup() {
        val context = app("test.haptic.backup")
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        AppPreference.init(context)
        val prefs = CustomHapticPreferences(context)
        val raw = HapticPattern(listOf(HapticStep(5, 255), HapticStep(1, 32), HapticStep(20, 0)))
        prefs.savePattern(HapticPatternKind.SPECIAL_KEY, raw)
        prefs.savePattern(HapticPatternKind.NORMAL_FLICK, HapticPattern(emptyList()))
        prefs.setMediaUsage(true)
        val json = AppPreference.exportAllToJson()
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        AppPreference.importAllFromJson(json)
        assertEquals(raw, prefs.pattern(HapticPatternKind.SPECIAL_KEY))
        assertEquals(emptyList<HapticStep>(), prefs.pattern(HapticPatternKind.NORMAL_FLICK).steps)
        assertTrue(prefs.useMediaUsage())
    }

    @Test
    fun unknownKeysAreRetainedAndUnknownTypesAreSkipped() {
        val context = app("test.future.backup")
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        AppPreference.init(context)
        AppPreference.importAllFromJson("""{"version":1,"entries":[
            {"key":"future_key","type":"string","value":"kept"},
            {"key":"future_type","type":"new_type","value":"ignored"}
        ]}""")
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        assertEquals("kept", prefs.getString("future_key", null))
        assertFalse(prefs.contains("future_type"))
    }
}
