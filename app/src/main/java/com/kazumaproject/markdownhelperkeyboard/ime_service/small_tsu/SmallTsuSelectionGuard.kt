package com.kazumaproject.markdownhelperkeyboard.ime_service.small_tsu

/** Accept only acknowledgements of our own composing writes (including preview/live display). */
internal class SmallTsuSelectionGuard {
    private data class Selection(val cursor: Int, val start: Int, val end: Int)
    private var anchor = -1
    private val expected = ArrayDeque<Selection>()
    private var last: Selection? = null
    var safe: Boolean = false
        private set

    fun reset(start: Int, end: Int) {
        expected.clear(); last = null
        anchor = start
        safe = start >= 0 && start == end
    }

    fun wrote(length: Int, cursorPosition: Int) {
        if (!safe || cursorPosition != 1 || length <= 0) return
        expected.addLast(Selection(anchor + length, anchor, anchor + length))
        if (expected.size > 64) expected.removeFirst()
    }

    fun selection(start: Int, end: Int, composingStart: Int, composingEnd: Int): Boolean {
        val actual = Selection(end, composingStart, composingEnd)
        val index = expected.indexOf(actual)
        if (safe && start == end && (index >= 0 || actual == last)) {
            repeat(index + 1) { expected.removeFirst() }
            last = actual
            return true
        }
        reset(start, end)
        // A moved caret within an existing composition has no proven ownership boundary.
        if (composingStart >= 0 || composingEnd >= 0) safe = false
        return false
    }
}
