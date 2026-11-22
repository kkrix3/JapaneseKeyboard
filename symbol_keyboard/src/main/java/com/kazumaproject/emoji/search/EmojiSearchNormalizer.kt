package com.kazumaproject.emoji.search

import java.text.Normalizer
import java.util.Locale

object EmojiSearchNormalizer {
    const val MAX_QUERY_LENGTH = 128

    /** Only for matching. Never use the normalized value to commit or render an emoji. */
    fun text(value: String): String = buildString {
        Normalizer.normalize(value, Normalizer.Form.NFKC).lowercase(Locale.ROOT).forEach { c ->
            when {
                c in '\u30a1'..'\u30f6' -> append(c - 0x60)
                c == '\uFE0E' || c == '\uFE0F' -> Unit
                c.isWhitespace() || Character.isSpaceChar(c) || c in "。、，,.!?！？・:：;；\"'「」『』（）()" -> Unit
                else -> append(c)
            }
        }
    }

    /** Keep ZWJ, gender, direction and component order; collapse only presentation/skin variants. */
    fun identity(symbol: String): String = buildString {
        var offset = 0
        while (offset < symbol.length) {
            val cp = symbol.codePointAt(offset)
            if (cp != 0xFE0E && cp != 0xFE0F && cp !in 0x1F3FB..0x1F3FF) {
                if (isNotEmpty()) append('-')
                append(cp.toString(16).uppercase(Locale.ROOT))
            }
            offset += Character.charCount(cp)
        }
    }
}
