package com.kazumaproject.markdownhelperkeyboard.ime_service.small_tsu
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
class SmallTsuOwnershipTest {
    @Test fun readingTracksABAWithoutWaitingForCollectors() {
        val state=ReadingStateFlow("か");state.update{"き"};state.update{"か"}
        assertEquals(2L,state.revision);state.value="か";assertEquals(2L,state.revision)
    }
    @Test fun normalAndCoalescedOwnSelectionNotificationsAreAccepted() {
        val guard=SmallTsuSelectionGuard();guard.reset(3,3);guard.wrote(1,1);guard.wrote(3,1)
        assertTrue(guard.selection(6,6,3,6));assertTrue(guard.selection(6,6,3,6))
    }
    @Test fun externalMoveSelectionAndCommitAreRejected() {
        for (selection in listOf(listOf(4,4,3,6),listOf(3,6,3,6),listOf(6,6,-1,-1))) {
            val guard=SmallTsuSelectionGuard();guard.reset(3,3);guard.wrote(3,1)
            assertFalse(guard.selection(selection[0],selection[1],selection[2],selection[3]))
        }
    }
    @Test fun liveConversionAndPreviewLengthsAreIndependentOfReading() {
        val guard=SmallTsuSelectionGuard();guard.reset(0,0);guard.wrote(3,1);guard.wrote(1,1);guard.wrote(4,1)
        assertTrue(guard.selection(1,1,0,1));assertTrue(guard.selection(4,4,0,4))
    }
    @Test fun concurrentStateFlowUpdatesKeepReadingAndRevisionTogether() {
        val state=ReadingStateFlow("")
        val start=CountDownLatch(1)
        val pool=Executors.newFixedThreadPool(4)
        try {
            val writers=(1..4).map { pool.submit {
                start.await()
                repeat(300) {
                    state.update { value -> value+"か" }
                    val snapshot=state.snapshot()
                    assertEquals(snapshot.reading.length.toLong(),snapshot.revision)
                }
            } }
            start.countDown()
            writers.forEach { it.get(10,TimeUnit.SECONDS) }
            assertEquals(1200L,state.snapshot().revision)
            assertEquals(1200,state.value.length)
        } finally { pool.shutdownNow() }
    }
    @Test fun reentrantCollectorSeesTheRevisionOfThePublishedReading() = runBlocking {
        val state=ReadingStateFlow("")
        val snapshots=mutableListOf<ReadingStateFlow.Snapshot>()
        val collector=launch(Dispatchers.Unconfined,start=CoroutineStart.UNDISPATCHED) {
            state.take(2).collect { snapshots.add(state.snapshot()) }
        }
        state.update { "か" }
        collector.join()
        assertEquals(listOf(ReadingStateFlow.Snapshot("",0),ReadingStateFlow.Snapshot("か",1)),snapshots)
    }
}
