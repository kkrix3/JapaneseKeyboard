package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.markdownhelperkeyboard.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.xmlpull.v1.XmlPullParser

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CustomHapticSettingsEntryPointTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val patternKeys = listOf(
        "custom_haptic_pattern_normal_flick",
        "custom_haptic_pattern_two_step_flick",
        "custom_haptic_pattern_special_key",
    )
    private val mediaKey = "custom_haptic_media_usage"

    @Test
    fun bothSettingsScreensExposeAllEditorsAndMediaOption() {
        for (xml in listOf(R.xml.pref_operation_feedback, R.xml.pref_common_legacy)) {
            val parser = context.resources.getXml(xml)
            val tags = mutableMapOf<String, String>()
            try {
                while (parser.next() != XmlPullParser.END_DOCUMENT) {
                    if (parser.eventType != XmlPullParser.START_TAG) continue
                    val key = parser.getAttributeValue(ANDROID_NS, "key") ?: continue
                    assertTrue("Duplicate preference $key", key !in tags)
                    tags[key] = parser.name
                    if (key == mediaKey) {
                        assertEquals("false", parser.getAttributeValue(ANDROID_NS, "defaultValue"))
                        assertEquals("vibration_preference", parser.getAttributeValue(ANDROID_NS, "dependency"))
                    }
                }
            } finally {
                parser.close()
            }
            patternKeys.forEach { assertEquals("Missing editor $it in $xml", "Preference", tags[it]) }
            assertEquals("SwitchPreferenceCompat", tags[mediaKey])
        }
    }

    @Test
    fun newSettingsSearchRoutesEachEditorToItsVisiblePreference() {
        val destinations = SettingSearchIndex.searchable(context)
        patternKeys.forEach { key ->
            val target = destinations.single {
                it.key == key && it.category == SettingCategory.OPERATION_FEEDBACK
            }.destination
            assertTrue(target is SettingDestinationType.NavDestination)
            assertEquals(R.id.operationFeedbackPreferenceFragment, SettingDestinations.destinationId(target))
            assertEquals(key, SettingDestinations.highlightPreferenceKey(target))
        }
    }

    @Test
    fun legacySearchExposesEditorsInCommonTabAndRetainsMediaDependency() {
        val destinations = SettingSearchIndex.legacySearchable(context).associateBy { it.key }
        (patternKeys + mediaKey).forEach { key ->
            val target = requireNotNull(destinations[key]?.legacyTarget) { "Missing legacy entry $key" }
            assertEquals(SettingTabRegistry.TAB_COMMON, target.tabKey)
            assertEquals(R.xml.pref_common_legacy, target.xmlRes)
            assertEquals(R.id.legacyCommonPreferenceFragment, target.destinationId)
            assertEquals(key, target.preferenceKey)
        }
        assertTrue(
            "vibration_preference" in requireNotNull(destinations[mediaKey]?.legacyTarget).relatedPreferenceKeys
        )
    }

    private companion object {
        const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
    }
}
