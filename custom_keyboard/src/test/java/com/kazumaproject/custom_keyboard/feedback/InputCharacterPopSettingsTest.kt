package com.kazumaproject.custom_keyboard.feedback

import org.junit.Assert.*
import org.junit.Test

class InputCharacterPopSettingsTest {
    @Test fun independentLimitsClampWithoutReorderingEndpoints() {
        val low = InputCharacterPopSettings(Int.MIN_VALUE, Int.MIN_VALUE, Int.MIN_VALUE,
            Int.MIN_VALUE, Int.MIN_VALUE, Int.MIN_VALUE, distanceDp = Int.MIN_VALUE).normalized()
        assertEquals(InputCharacterPopSettings(50, 10, 10, 0, 0, 0, distanceDp = 0), low)
        val high = InputCharacterPopSettings(Int.MAX_VALUE, Int.MAX_VALUE, Int.MAX_VALUE,
            Int.MAX_VALUE, Int.MAX_VALUE, Int.MAX_VALUE, distanceDp = Int.MAX_VALUE).normalized()
        assertEquals(InputCharacterPopSettings(1500, 400, 400, 100, 100, 100, distanceDp = 100), high)
        val reverse = InputCharacterPopSettings(startScalePercent = 300, endScalePercent = 50,
            startOpacityPercent = 10, endOpacityPercent = 90).normalized()
        assertEquals(3f, reverse.scale(0f), 0f); assertEquals(.5f, reverse.scale(1f), 0f)
        assertEquals(.1f, reverse.alpha(0f), .0001f); assertEquals(.9f, reverse.alpha(1f), .0001f)
    }

    @Test fun everyCurveHasEndpointsAndNoOvershootInEitherDirection() {
        for (curve in PopEasing.entries) {
            assertEquals(0f, curve.transform(-1f), 0f)
            assertEquals(1f, curve.transform(2f), 0f)
            var previous = 0f
            for (i in 0..100) {
                val t = curve.transform(i / 100f)
                assertTrue(t in previous..1f); previous = t
            }
        }
        assertEquals(.25f, PopEasing.LINEAR.transform(.25f), 0f)
        assertEquals(.0625f, PopEasing.EASE_IN.transform(.25f), 0f)
        assertEquals(.4375f, PopEasing.EASE_OUT.transform(.25f), 0f)
        assertEquals(.125f, PopEasing.EASE_IN_OUT.transform(.25f), 0f)
        assertEquals(PopEasing.EASE_OUT, PopEasing.fromPreference("unknown"))
    }

    @Test fun fadeDelayZeroTwentyAndOneHundredPercentHaveFiniteEndpoints() {
        for (delay in listOf(0, 20, 100)) for (curve in PopEasing.entries) {
            val s = InputCharacterPopSettings(fadeStartPercent = delay, fadeEasing = curve)
            assertEquals(1f, s.alpha(delay / 100f - .01f), 0f)
            assertEquals(0f, s.alpha(1f), 0f)
            for (i in 0..100) assertTrue(s.alpha(i / 100f) in 0f..1f)
        }
        assertEquals(.5f, InputCharacterPopSettings(fadeStartPercent = 20,
            fadeEasing = PopEasing.LINEAR).alpha(.6f), .0001f)
    }
}
