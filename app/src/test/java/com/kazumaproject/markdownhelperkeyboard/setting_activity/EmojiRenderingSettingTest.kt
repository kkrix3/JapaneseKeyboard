package com.kazumaproject.markdownhelperkeyboard.setting_activity

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.symbol_keyboard.EmojiRenderingStyle
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.xmlpull.v1.XmlPullParser

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EmojiRenderingSettingTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    @Before fun setup() { prefs.edit().clear().commit(); AppPreference.init(context) }
    @After fun cleanup() { prefs.edit().clear().commit() }

    @Test fun defaultsPersistsRestoresAndSurvivesBackup() {
        assertEquals(EmojiRenderingStyle.DEVICE, AppPreference.emoji_rendering_style_preference)
        AppPreference.emoji_rendering_style_preference = EmojiRenderingStyle.GOOGLE
        AppPreference.init(context)
        assertEquals(EmojiRenderingStyle.GOOGLE, AppPreference.emoji_rendering_style_preference)
        val backup = AppPreference.exportAllToJson()
        AppPreference.emoji_rendering_style_preference = EmojiRenderingStyle.DEVICE
        assertEquals(EmojiRenderingStyle.DEVICE, AppPreference.emoji_rendering_style_preference)
        AppPreference.importAllFromJson(backup)
        assertEquals(EmojiRenderingStyle.GOOGLE, AppPreference.emoji_rendering_style_preference)
    }

    @Test fun unknownAndWronglyTypedValuesRecoverToDevice() {
        prefs.edit().putString(EmojiRenderingStyle.PREFERENCE_KEY, "unknown").commit()
        assertEquals(EmojiRenderingStyle.DEVICE, AppPreference.emoji_rendering_style_preference)
        prefs.edit().putInt(EmojiRenderingStyle.PREFERENCE_KEY, 1).commit()
        assertEquals(EmojiRenderingStyle.DEVICE, AppPreference.emoji_rendering_style_preference)
    }

    @Test fun bothSettingsScreensExposeSameChoicesAndDefault() {
        val ns = "http://schemas.android.com/apk/res/android"
        for (screen in listOf(R.xml.pref_clipboard_shortcut, R.xml.pref_common_legacy)) {
            var found = false
            context.resources.getXml(screen).use { xml ->
                while (xml.next() != XmlPullParser.END_DOCUMENT) {
                    if (xml.eventType != XmlPullParser.START_TAG ||
                        xml.getAttributeValue(ns, "key") != EmojiRenderingStyle.PREFERENCE_KEY) continue
                    found = true
                    assertEquals("device", xml.getAttributeValue(ns, "defaultValue"))
                    val values = context.resources.getStringArray(xml.getAttributeResourceValue(ns, "entryValues", 0))
                    assertArrayEquals(arrayOf("device", "google"), values)
                }
            }
            assertTrue(found)
        }
    }
}
