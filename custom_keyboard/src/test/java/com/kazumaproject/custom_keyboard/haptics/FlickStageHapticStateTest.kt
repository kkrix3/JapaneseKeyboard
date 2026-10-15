package com.kazumaproject.custom_keyboard.haptics

import org.junit.Assert.*
import org.junit.Test

class FlickStageHapticStateTest {
    private val state = FlickStageHapticState()
    private val settings = FlickStageHapticSettings()

    @Test fun advanceRetreatAndUnchangedPath() {
        assertEquals(FlickStageTransition(0, 1, false), state.update(listOf("UP"), 0, settings))
        assertNull(state.update(listOf("UP"), 1, settings))
        assertEquals(FlickStageTransition(0, 1, false), state.update(listOf("UP", "RIGHT"), 2, settings))
        assertEquals(FlickStageTransition(1, 0, false), state.update(listOf("UP"), 50, settings))
        assertEquals(FlickStageTransition(1, 0, false), state.update(emptyList(), 51, settings))
    }

    @Test fun replacementUsesLongestCommonPrefixAtAnyDepth() {
        state.update(listOf("UP", "RIGHT", "DOWN"), 0, settings)
        assertEquals(FlickStageTransition(2, 2, false),
            state.update(listOf("UP", "LEFT", "RIGHT"), 1, settings))
    }

    @Test fun repeatedBoundaryIsSuppressedButSelectionAlwaysAdvances() {
        state.update(listOf("UP"), 0, settings)
        assertFalse(state.update(listOf("RIGHT"), 1, settings)!!.suppressed)
        assertTrue(state.update(listOf("UP"), 20, settings)!!.suppressed)
        assertEquals(listOf("UP"), state.path)
        assertTrue(state.update(listOf("RIGHT"), 30, settings)!!.suppressed)
        assertFalse(state.update(listOf("UP"), 70, settings)!!.suppressed)
    }

    @Test fun suppressionDoesNotThrottleRapidTraversalOfDifferentStages() {
        for (depth in 1..20) {
            assertFalse(state.update(List(depth) { "direction-$it" }, depth.toLong(), settings)!!.suppressed)
        }
        assertEquals(20, state.path.size)
    }

    @Test fun suppressesTapBoundaryAndSupportsCompleteOffAndZeroWindow() {
        state.update(listOf("UP"), 0, settings)
        assertTrue(state.update(emptyList(), 1, settings)!!.suppressed)
        assertFalse(state.update(listOf("UP"), 2, settings.copy(suppressOscillation = false))!!.suppressed)
        assertFalse(state.update(emptyList(), 3, settings.copy(suppressionMs = 0))!!.suppressed)
    }

    @Test fun resetDoesNotCarrySuppressionToAnotherGesture() {
        state.update(listOf("UP"), 0, settings)
        state.reset()
        assertEquals(emptyList<String>(), state.path)
        assertFalse(state.update(listOf("UP"), 1, settings)!!.suppressed)
    }

    @Test fun pathsDoNotCountSubmenuEntryEchoAsAnotherStage() {
        assertEquals(emptyList<String>(), twoStepHapticPath("TAP", "TAP"))
        assertEquals(listOf("UP"), twoStepHapticPath("UP", "UP"))
        assertEquals(listOf("UP"), twoStepHapticPath("UP", "TAP"))
        assertEquals(listOf("UP", "RIGHT"), twoStepHapticPath("UP", "RIGHT"))
        assertEquals(listOf("UP", "LEFT"), hierarchicalHapticPath(listOf("TAP", "UP", "LEFT"), "LEFT"))
        assertEquals(listOf("UP", "LEFT", "DOWN"), hierarchicalHapticPath(listOf("TAP", "UP", "LEFT"), "DOWN"))
        assertEquals(emptyList<String>(), hierarchicalHapticPath(listOf("TAP"), "TAP"))
    }

    @Test fun independentBoundariesAndParentsDoNotShareSuppression() {
        state.update(listOf("UP", "RIGHT"), 0, settings)
        assertFalse(state.update(listOf("UP", "LEFT"), 1, settings)!!.suppressed)
        assertFalse(state.update(listOf("UP", "DOWN"), 2, settings)!!.suppressed)
        assertFalse(state.update(listOf("LEFT", "RIGHT"), 3, settings)!!.suppressed)
        assertFalse(state.update(listOf("LEFT", "LEFT"), 4, settings)!!.suppressed)
    }
}
