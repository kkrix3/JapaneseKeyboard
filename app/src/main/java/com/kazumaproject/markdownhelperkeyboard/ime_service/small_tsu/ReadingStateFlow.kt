package com.kazumaproject.markdownhelperkeyboard.ime_service.small_tsu

import kotlinx.coroutines.flow.MutableStateFlow

/** Counts synchronous reading mutations, including A -> B -> A between flow emissions. */
internal class ReadingStateFlow private constructor(
    private val backing: MutableStateFlow<String>,
) : MutableStateFlow<String> by backing {
    constructor(initial: String) : this(MutableStateFlow(initial))
    private val mutationLock = Any()
    @Volatile
    var revision: Long = 0
        private set
    data class Snapshot(val reading: String, val revision: Long)
    fun snapshot(): Snapshot = synchronized(mutationLock) { Snapshot(backing.value, revision) }
    override var value: String
        get() = backing.value
        set(value) { synchronized(mutationLock) {
            if (backing.value != value) revision++
            backing.value = value
        } }
    override fun compareAndSet(expect: String, update: String): Boolean = synchronized(mutationLock) {
        if (backing.value != expect) false else {
            // All writes use this lock. Increment before StateFlow resumes an
            // unconfined/reentrant collector so its snapshot is consistent too.
            if (expect != update) revision++
            backing.compareAndSet(expect, update)
        }
    }
    override suspend fun emit(value: String) { this.value = value }
    override fun tryEmit(value: String): Boolean { this.value = value; return true }
}
