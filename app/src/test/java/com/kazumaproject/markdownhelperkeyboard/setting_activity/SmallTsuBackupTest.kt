package com.kazumaproject.markdownhelperkeyboard.setting_activity
import android.content.Context
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.core.domain.small_tsu.SmallTsuSettings
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
@RunWith(RobolectricTestRunner::class) @Config(sdk=[35])
class SmallTsuBackupTest {
    private val context=ApplicationProvider.getApplicationContext<Context>()
    private val prefs=PreferenceManager.getDefaultSharedPreferences(context)
    @Before fun setup(){prefs.edit().clear().commit();AppPreference.init(context)}
    @Test fun defaultIsOffAndOldBackupResetsNewSettings(){
        prefs.edit().putBoolean(SmallTsuPreferences.ENABLED,true).commit()
        AppPreference.importAllFromJson("""{"version":1,"entries":[{"key":"unrelated","type":"string","value":"keep"}]}""",false)
        assertEquals(SmallTsuSettings(),SmallTsuPreferences.read(prefs));assertEquals("keep",prefs.getString("unrelated",null))
    }
    @Test fun newBackupRoundTripsAndPreservesUnknownKeys(){
        prefs.edit().putBoolean(SmallTsuPreferences.ENABLED,true).putInt(SmallTsuPreferences.INTERVAL,235)
            .putStringSet(SmallTsuPreferences.ROWS,setOf("か","ま")).putString("future_feature","keep").commit()
        val exported=AppPreference.exportAllToJson();prefs.edit().clear().commit();AppPreference.importAllFromJson(exported)
        assertEquals(SmallTsuSettings(true,235,setOf("か","ま")),SmallTsuPreferences.read(prefs))
        assertEquals("keep",prefs.getString("future_feature",null))
    }
    @Test fun malformedFeatureValuesCannotEnableOrCrashOtherSettings(){
        for(value in listOf("-1","501","200.5","\"wrong\"","null")) {
            AppPreference.importAllFromJson("""{"version":1,"entries":[{"key":"double_tap_small_tsu_enabled","type":"boolean","value":"true"},{"key":"double_tap_small_tsu_interval","type":"int","value":$value},{"key":"unrelated","type":"int","value":42}]}""")
            assertFalse(SmallTsuPreferences.read(prefs).enabled);assertEquals(200,SmallTsuPreferences.read(prefs).intervalMillis)
            assertEquals(42,prefs.getInt("unrelated",0))
        }
    }
    @Test fun emptyRowsAndUnknownRowsAreSafe(){
        prefs.edit().putStringSet(SmallTsuPreferences.ROWS,setOf("unknown")).commit();assertTrue(SmallTsuPreferences.read(prefs).rows.isEmpty())
    }
}
