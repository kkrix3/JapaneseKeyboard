package com.kazumaproject.emoji.search

/** Append-only private query. No editor, InputConnection, composing span or persisted history. */
class EmojiSearchInputState {
    var query: String = ""
        private set

    fun append(text: String) {
        query = (query + text).take(EmojiSearchNormalizer.MAX_QUERY_LENGTH)
        if (query.lastOrNull()?.isHighSurrogate() == true) query = query.dropLast(1)
    }
    fun clear() { query = "" }
    fun delete() {
        if (query.isNotEmpty()) query = query.dropLast(Character.charCount(query.codePointBefore(query.length)))
    }
    fun modifyLastKana() {
        if (query.isEmpty()) return
        val cycles = listOf("かが", "きぎ", "くぐ", "けげ", "こご", "さざ", "しじ", "すず", "せぜ", "そぞ",
            "ただ", "ちぢ", "つづっ", "てで", "とど", "はばぱ", "ひびぴ", "ふぶぷ", "へべぺ", "ほぼぽ",
            "あぁ", "いぃ", "うぅゔ", "えぇ", "おぉ", "やゃ", "ゆゅ", "よょ", "わゎ")
        val last = query.last()
        val cycle = cycles.firstOrNull { last in it } ?: return
        query = query.dropLast(1) + cycle[(cycle.indexOf(last) + 1) % cycle.length]
    }
}
