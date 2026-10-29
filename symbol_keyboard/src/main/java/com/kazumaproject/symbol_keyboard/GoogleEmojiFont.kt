package com.kazumaproject.symbol_keyboard

import android.content.Context
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat

/** One bundled CBDT/CBLC Typeface for list, history and skin-tone choices. */
object GoogleEmojiFont {
    private var cached: Typeface? = null
    @Synchronized
    fun get(context: Context): Typeface = cached ?: requireNotNull(
        ResourcesCompat.getFont(context.applicationContext, R.font.noto_color_emoji)
    ).also { cached = it }
}
