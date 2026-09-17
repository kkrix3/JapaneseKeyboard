package com.kazumaproject.markdownhelperkeyboard.setting_activity

import android.content.SharedPreferences
import com.kazumaproject.core.domain.small_tsu.SmallTsuSettings
import com.kazumaproject.markdownhelperkeyboard.setting_activity.backup.PrefEntry

internal object SmallTsuPreferences {
    const val ENABLED = "double_tap_small_tsu_enabled"
    const val INTERVAL = "double_tap_small_tsu_interval"
    const val ROWS = "double_tap_small_tsu_rows"
    val keys = setOf(ENABLED, INTERVAL, ROWS)

    fun read(preferences: SharedPreferences): SmallTsuSettings {
        val all = preferences.all
        return SmallTsuSettings(
            enabled = all[ENABLED] as? Boolean ?: false,
            intervalMillis = (all[INTERVAL] as? Int)?.takeIf {
                it in SmallTsuSettings.MIN_INTERVAL..SmallTsuSettings.MAX_INTERVAL
            } ?: 200,
            rows = (all[ROWS] as? Set<*>)?.filterIsInstance<String>()
                ?.filter { it in SmallTsuSettings.ALL_ROWS }?.toSet() ?: SmallTsuSettings.DEFAULT_ROWS,
        )
    }

    /** Invalid values reset this feature only; unknown settings elsewhere remain untouched. */
    fun importEntry(editor: SharedPreferences.Editor, entry: PrefEntry) {
        when (entry.key) {
            ENABLED -> editor.putBoolean(ENABLED,
                entry.type == "boolean" && entry.value == true)
            INTERVAL -> {
                val number = (entry.value as? Number)?.toDouble()
                val valid = entry.type == "int" && number != null && number.isFinite() &&
                    number % 1.0 == 0.0 && number >= SmallTsuSettings.MIN_INTERVAL &&
                    number <= SmallTsuSettings.MAX_INTERVAL
                editor.putInt(INTERVAL, if (valid) number!!.toInt() else 200)
            }
            ROWS -> {
                val values = if (entry.type == "string_set") entry.value as? List<*> else null
                editor.putStringSet(ROWS, values?.filterIsInstance<String>()
                    ?.filter { it in SmallTsuSettings.ALL_ROWS }?.toSet() ?: SmallTsuSettings.DEFAULT_ROWS)
            }
        }
    }
}
