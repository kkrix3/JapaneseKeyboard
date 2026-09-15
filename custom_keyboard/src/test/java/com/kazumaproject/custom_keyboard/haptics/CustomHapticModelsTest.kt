package com.kazumaproject.custom_keyboard.haptics

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CustomHapticModelsTest {

    @Test
    fun defaultPatternsMatchInitialDesign() {
        assertEquals(
            listOf(HapticStep(5, 32)),
            HapticPattern.defaultFor(HapticPatternKind.NORMAL_FLICK).steps
        )
        assertEquals(
            listOf(HapticStep(5, 32), HapticStep(20, 0), HapticStep(5, 32)),
            HapticPattern.defaultFor(HapticPatternKind.TWO_STEP_FLICK).steps
        )
        assertEquals(
            listOf(HapticStep(5, 96)),
            HapticPattern.defaultFor(HapticPatternKind.SPECIAL_KEY).steps
        )
    }

    @Test
    fun patternRejectsMoreThanTenSteps() {
        assertThrows(IllegalArgumentException::class.java) {
            HapticPattern(List(11) { HapticStep(1, 1) })
        }
    }

    @Test
    fun codecRoundTripsTimingAndAmplitudePairs() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val prefs = CustomHapticPreferences(context)
        val pattern = HapticPattern(
            listOf(
                HapticStep(0, 0),
                HapticStep(5, 255),
                HapticStep(1, 32),
                HapticStep(500, 0)
            )
        )

        val encoded = prefs.serializePattern(pattern)
        assertEquals("0,0;5,255;1,32;500,0", encoded)
        assertEquals(pattern, prefs.parsePattern(encoded))
    }

    @Test
    fun codecRejectsInvalidAmplitudeAndTooManySteps() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val prefs = CustomHapticPreferences(context)

        assertNull(prefs.parsePattern("5,256"))
        assertNull(prefs.parsePattern(List(11) { "1,1" }.joinToString(";")))
    }
}
