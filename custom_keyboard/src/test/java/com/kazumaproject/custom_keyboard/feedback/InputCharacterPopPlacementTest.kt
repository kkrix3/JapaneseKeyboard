package com.kazumaproject.custom_keyboard.feedback

import com.kazumaproject.custom_keyboard.data.TfbiGuideGrid
import com.kazumaproject.custom_keyboard.view.TfbiFlickDirection
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.hypot
import kotlin.math.sqrt

class InputCharacterPopPlacementTest {
    @Test fun tapAxesAndSingleDiagonalsHaveTheRequestedRadius() {
        for (direction in TfbiFlickDirection.entries) {
            val (x, y) = InputCharacterPopPlacement.slot(direction).offset(20f)
            assertEquals(if (direction == TfbiFlickDirection.TAP) 0f else 20f, hypot(x, y), .0001f)
            val (column, row) = TfbiGuideGrid.cellOf(direction)
            assertEquals((column - 1).toFloat(), kotlin.math.sign(x), 0f)
            assertEquals((row - 1).toFloat(), kotlin.math.sign(y), 0f)
        }
    }

    @Test fun allTwoStepSlotsUseTheirMappedCellAndNotUnconditionalStageScaling() {
        for (first in TfbiFlickDirection.entries) for (second in TfbiFlickDirection.entries) {
            val selected = if (second == TfbiFlickDirection.TAP) first else second
            val (column, row) = TfbiGuideGrid.cellOf(selected)
            val turned = first != TfbiFlickDirection.TAP && second != TfbiFlickDirection.TAP && first != second
            val diagonal = column != 1 && row != 1
            val placement = InputCharacterPopPlacement.twoStep(first, second)
            val (x, y) = placement.offset(20f)
            val expected = when {
                selected == TfbiFlickDirection.TAP -> 0f
                turned && diagonal -> sqrt(2f) * 20f
                else -> 20f
            }
            assertEquals("$first -> $second", expected, hypot(x, y), .0001f)
            assertEquals(0f to 0f, placement.offset(0f))
        }
        assertEquals(20f to -20f, InputCharacterPopPlacement.twoStep(
            TfbiFlickDirection.UP, TfbiFlickDirection.UP_RIGHT).offset(20f))
        assertEquals(20f to 0f, InputCharacterPopPlacement.twoStep(
            TfbiFlickDirection.UP, TfbiFlickDirection.RIGHT).offset(20f))
    }
}
