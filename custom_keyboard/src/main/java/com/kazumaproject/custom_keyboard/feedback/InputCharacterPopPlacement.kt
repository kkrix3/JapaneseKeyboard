package com.kazumaproject.custom_keyboard.feedback

import com.kazumaproject.custom_keyboard.data.FlickDirection
import com.kazumaproject.custom_keyboard.data.TfbiGuideGrid
import com.kazumaproject.custom_keyboard.view.TfbiFlickDirection
import kotlin.math.hypot

/** Relative glyph placement, independent of popup pixels and any future travel vector. */
internal data class InputCharacterPopPlacement(val x: Float, val y: Float) {
    fun offset(distance: Float): Pair<Float, Float> =
        if (distance == 0f) 0f to 0f else x * distance to y * distance

    companion object {
        val CENTER = InputCharacterPopPlacement(0f, 0f)

        fun slot(direction: TfbiFlickDirection, gridPlacement: Boolean = false): InputCharacterPopPlacement {
            val (column, row) = TfbiGuideGrid.cellOf(direction)
            val x = (column - 1).toFloat()
            val y = (row - 1).toFloat()
            val length = if (gridPlacement) 1f else hypot(x, y).coerceAtLeast(1f)
            return InputCharacterPopPlacement(x / length, y / length)
        }

        fun legacy(direction: FlickDirection) = slot(when (direction) {
            FlickDirection.TAP -> TfbiFlickDirection.TAP
            FlickDirection.UP -> TfbiFlickDirection.UP
            FlickDirection.DOWN -> TfbiFlickDirection.DOWN
            // Legacy names describe petal sectors; popup/guide labels put these on the axes.
            FlickDirection.UP_LEFT, FlickDirection.UP_LEFT_FAR -> TfbiFlickDirection.LEFT
            FlickDirection.UP_RIGHT, FlickDirection.UP_RIGHT_FAR -> TfbiFlickDirection.RIGHT
        })

        fun twoStep(first: TfbiFlickDirection, second: TfbiFlickDirection): InputCharacterPopPlacement {
            // The second map uses absolute 3x3 display slots, not vectors to add to the first.
            val selected = if (second == TfbiFlickDirection.TAP) first else second
            val turned = first != TfbiFlickDirection.TAP && second != TfbiFlickDirection.TAP && first != second
            return slot(selected, gridPlacement = turned)
        }
    }
}
