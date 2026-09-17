package com.kazumaproject.core.domain.small_tsu

import java.text.Normalizer

/** Transport only: neither view delays ordinary input nor edits the editor. */
interface KanaGestureObserver {
    fun down(key: String, eventTime: Long)
    fun cancel()
    fun text(text: String, tap: Boolean, eventTime: Long, dispatch: () -> Unit)
}

data class SmallTsuSettings(
    val enabled: Boolean = false,
    val intervalMillis: Int = 200,
    val rows: Set<String> = DEFAULT_ROWS,
) {
    companion object {
        val ALL_ROWS = listOf("あ", "か", "が", "さ", "ざ", "た", "だ", "な", "は", "ば", "ぱ", "ま", "や", "ら", "わ")
        val DEFAULT_ROWS = setOf("か", "が", "さ", "ざ", "た", "だ", "は", "ば", "ぱ")
        const val MIN_INTERVAL = 50
        const val MAX_INTERVAL = 500
    }
}

object SmallTsuKana {
    private val rowCharacters = listOf(
        "ぁあぃいぅうぇえぉおゔ", "かきくけこ", "がぎぐげご", "さしすせそ", "ざじずぜぞ",
        "たちつてと", "だぢづでど", "なにぬねの", "はひふへほ", "ばびぶべぼ", "ぱぴぷぺぽ",
        "まみむめも", "ゃやゅゆょよ", "らりるれろ", "ゎわゐゑを",
    )

    /** NFC is for classification only. The caller retains the original output bytes. */
    fun prefix(text: String, rows: Set<String>): String? {
        val normalized = Normalizer.normalize(
            text.replace('゛', '\u3099').replace('゜', '\u309A'), Normalizer.Form.NFC,
        )
        val first = normalized.firstOrNull() ?: return null
        if (first == 'っ' || first == 'ッ') return null
        if (normalized.any { it !in 'ぁ'..'ゔ' && it !in 'ァ'..'ヴ' && it != 'ー' }) return null
        val hiragana = if (first in 'ァ'..'ヴ') (first.code - 0x60).toChar() else first
        val row = rowCharacters.indexOfFirst { hiragana in it }
        if (row < 0 || SmallTsuSettings.ALL_ROWS[row] !in rows) return null
        return if (first in 'ァ'..'ヴ') "ッ" else "っ"
    }
}

/** A reading owned by this IME, never text obtained using synchronous editor IPC. */
data class SmallTsuSnapshot(
    val owner: Any,
    val session: Long,
    val revision: Long,
    val context: String,
    val reading: String,
)

/** Non-overlapping pairs. The second DOWN latches timing; its UP has no deadline. */
class SmallTsuSession {
    private data class First(val key: String, val up: Long, val before: String,
        val after: SmallTsuSnapshot, val settings: SmallTsuSettings)
    private data class Stroke(val key: String, val first: First?)
    private var first: First? = null
    private var stroke: Stroke? = null
    private var epoch = 0L

    fun cancel() { first = null; stroke = null; epoch++ }

    fun down(key: String, time: Long, snapshot: SmallTsuSnapshot?, settings: SmallTsuSettings) {
        val previous = first
        first = null
        stroke = if (!settings.enabled || snapshot == null) null else Stroke(key,
            previous?.takeIf {
                it.key == key && it.after == snapshot && it.settings == settings &&
                    time - it.up in 0L..settings.intervalMillis.toLong()
            })
    }

    /** dispatch runs synchronously exactly once, even when eligibility or ownership fails. */
    fun text(text: String, tap: Boolean, time: Long, settings: SmallTsuSettings,
        snapshot: () -> SmallTsuSnapshot?, dispatch: (replacementReading: String?) -> Unit) {
        val active = stroke
        stroke = null
        val before = snapshot()
        val previous = active?.first
        val prefix = SmallTsuKana.prefix(text, settings.rows)
        val replacement = if (settings.enabled && previous != null &&
            previous.after == before && previous.settings == settings && prefix != null)
            previous.before + prefix + text else null
        val startedEpoch = epoch
        dispatch(replacement)
        val after = snapshot()
        // A paired second stroke is never reused, even when its final output is excluded.
        if (active != null && previous == null && tap && settings.enabled &&
            startedEpoch == epoch && before != null && after != null && text.isNotEmpty() &&
            after.owner === before.owner && after.session == before.session &&
            after.context == before.context && after.revision > before.revision &&
            after.reading == before.reading + text) {
            first = First(active.key, time, before.reading, after, settings)
        }
    }
}
