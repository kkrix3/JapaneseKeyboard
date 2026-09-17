package com.kazumaproject.markdownhelperkeyboard.ime_service.small_tsu
import kotlinx.coroutines.flow.update
import org.junit.Assert.*
import org.junit.Test
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
}
