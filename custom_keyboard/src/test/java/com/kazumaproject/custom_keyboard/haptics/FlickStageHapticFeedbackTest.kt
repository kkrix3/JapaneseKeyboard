package com.kazumaproject.custom_keyboard.haptics

import android.content.Context
import android.os.Looper
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.PAUSED)
class FlickStageHapticFeedbackTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val raw = PreferenceManager.getDefaultSharedPreferences(context)
    private val played = mutableListOf<HapticPatternKind>()
    private val owner = Any()
    private lateinit var feedback: FlickStageHapticFeedback

    @Before fun setup() {
        raw.edit().clear().putBoolean(CustomHapticPreferences.KEY_STAGE_ENABLED, true)
            .putBoolean(CustomHapticPreferences.KEY_VIBRATION_ENABLED, true)
            .putBoolean(CustomHapticPreferences.KEY_STAGE_SUPPRESSION, false).commit()
        feedback = FlickStageHapticFeedback(context, play = { played += it })
    }
    private fun tick(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))
    private fun change() {
        feedback.begin(owner)
        feedback.update(owner, listOf("UP"))
        played.clear()
        feedback.update(owner, listOf("RIGHT"))
    }

    @Test fun applicationModeUsesThirtyMsStartDelayAndNoDuplicateOnSameState() {
        change()
        assertEquals(listOf(HapticPatternKind.STAGE_RETREAT), played)
        tick(29)
        feedback.update(owner, listOf("RIGHT"))
        assertEquals(1, played.size)
        tick(1)
        assertEquals(listOf(HapticPatternKind.STAGE_RETREAT, HapticPatternKind.STAGE_ADVANCE), played)
    }

    @Test fun compositeIsOneCommandAndDoesNotReadApplicationDelay() {
        raw.edit().putString(CustomHapticPreferences.KEY_STAGE_CHANGE_MODE, "composite")
            .putInt(CustomHapticPreferences.KEY_STAGE_DELAY_MS, 200).commit()
        change()
        tick(200)
        assertEquals(listOf(HapticPatternKind.STAGE_CHANGE), played)
    }

    @Test fun noDelayIssuesBothCommandsImmediately() {
        raw.edit().putString(CustomHapticPreferences.KEY_STAGE_CHANGE_MODE, "off").commit()
        change()
        assertEquals(listOf(HapticPatternKind.STAGE_RETREAT, HapticPatternKind.STAGE_ADVANCE), played)
    }

    @Test fun endAndCancelDiscardOutstandingCommands() {
        change()
        feedback.end(owner)
        tick(100)
        assertEquals(1, played.size)
        change()
        feedback.cancel()
        tick(100)
        assertEquals(1, played.size)
    }

    @Test fun newOwnerInvalidatesOldReservationAndOldEndCannotCancelNewGesture() {
        change()
        val nextOwner = Any()
        feedback.begin(nextOwner)
        feedback.end(owner)
        feedback.update(owner, listOf("DOWN"))
        feedback.update(nextOwner, listOf("LEFT"))
        tick(100)
        assertEquals(listOf(HapticPatternKind.STAGE_RETREAT, HapticPatternKind.STAGE_ADVANCE), played)
    }

    @Test fun changedStateDiscardsOldReservationIncludingSuppressedState() {
        raw.edit().putBoolean(CustomHapticPreferences.KEY_STAGE_SUPPRESSION, true).commit()
        change()
        feedback.update(owner, listOf("UP"))
        tick(100)
        assertEquals(listOf(HapticPatternKind.STAGE_RETREAT), played)
    }

    @Test fun masterOffAndStageOffCancelBeforeReservedAdvance() {
        for (key in listOf(CustomHapticPreferences.KEY_VIBRATION_ENABLED, CustomHapticPreferences.KEY_STAGE_ENABLED)) {
            raw.edit().putBoolean(CustomHapticPreferences.KEY_VIBRATION_ENABLED, true)
                .putBoolean(CustomHapticPreferences.KEY_STAGE_ENABLED, true).commit()
            change()
            raw.edit().putBoolean(key, false).commit()
            tick(100)
            feedback.update(owner, listOf("DOWN"))
            assertEquals(listOf(HapticPatternKind.STAGE_RETREAT), played)
        }
    }

    @Test fun importAndSettingChangesInvalidatePendingPlayback() {
        change()
        raw.edit().clear().commit()
        tick(100)
        assertEquals(1, played.size)
    }

    @Test fun tapAndDefaultDisabledFeatureAreSilent() {
        feedback.begin(owner)
        feedback.update(owner, emptyList())
        feedback.end(owner)
        raw.edit().remove(CustomHapticPreferences.KEY_STAGE_ENABLED).commit()
        feedback.begin(owner)
        feedback.update(owner, listOf("UP"))
        assertTrue(played.isEmpty())
    }

    @Test fun explicitCenterReturnProducesNoExtraReplacementEvent() {
        feedback.begin(owner)
        feedback.update(owner, listOf("UP"))
        feedback.update(owner, emptyList())
        feedback.update(owner, listOf("RIGHT"))
        tick(100)
        assertEquals(listOf(HapticPatternKind.STAGE_ADVANCE, HapticPatternKind.STAGE_RETREAT,
            HapticPatternKind.STAGE_ADVANCE), played)
    }
}
