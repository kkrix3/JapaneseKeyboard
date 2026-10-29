package com.kazumaproject.symbol_keyboard

import android.content.SharedPreferences

/** Display policy only: original Unicode and input events remain unchanged. */
enum class EmojiRenderingStyle(val preferenceValue: String) {
    DEVICE("device"), GOOGLE("google");

    companion object {
        const val PREFERENCE_KEY = "emoji_rendering_style_preference"
        fun fromPreference(value: String?): EmojiRenderingStyle =
            entries.firstOrNull { it.preferenceValue == value } ?: DEVICE
        fun read(preferences: SharedPreferences): EmojiRenderingStyle = fromPreference(
            try { preferences.getString(PREFERENCE_KEY, null) } catch (_: ClassCastException) { null }
        )
    }
}
