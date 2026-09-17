package com.kazumaproject.markdownhelperkeyboard.ime_service.small_tsu

import kotlinx.coroutines.flow.MutableStateFlow

/** Counts synchronous reading mutations, including A -> B -> A between flow emissions. */
internal class ReadingStateFlow private constructor(
    private val backing: MutableStateFlow<String>,
) : MutableStateFlow<String> by backing {
    constructor(initial: String) : this(MutableStateFlow(initial))
    var revision: Long = 0
        private set
    override var value: String
        get() = backing.value
        set(value) { if (backing.value != value) revision++; backing.value = value }
    override fun compareAndSet(expect: String, update: String): Boolean {
        val changed = backing.compareAndSet(expect, update)
        if (changed && expect != update) revision++
        return changed
    }
    override suspend fun emit(value: String) { this.value = value }
    override fun tryEmit(value: String): Boolean { this.value = value; return true }
}
